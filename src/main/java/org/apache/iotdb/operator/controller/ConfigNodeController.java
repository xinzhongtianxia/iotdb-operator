/*
 *     Licensed to the Apache Software Foundation (ASF) under one
 *     or more contributor license agreements.  See the NOTICE file
 *     distributed with this work for additional information
 *     regarding copyright ownership.  The ASF licenses this file
 *     to you under the Apache License, Version 2.0 (the
 *     "License"); you may not use this file except in compliance
 *     with the License.  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing,
 *     software distributed under the License is distributed on an
 *     "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *     KIND, either express or implied.  See the License for the
 *     specific language governing permissions and limitations
 *     under the License.
 */

package org.apache.iotdb.operator.controller;

import org.apache.iotdb.operator.controller.reconciler.DefaultReconciler;
import org.apache.iotdb.operator.controller.reconciler.IReconciler;
import org.apache.iotdb.operator.controller.reconciler.confignode.ConfigNodeDeleteReconciler;
import org.apache.iotdb.operator.controller.reconciler.confignode.ConfigNodeStartUpReconciler;
import org.apache.iotdb.operator.controller.reconciler.confignode.ConfigNodeUpdateReconciler;
import org.apache.iotdb.operator.crd.ConfigNode;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.event.ConfigNodeEvent;

import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Watch, receive and dispatch all events about ConfigNode AND its related resources from Kubernetes
 * Server with in the specific namespace.
 */
public class ConfigNodeController implements IController {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigNodeController.class);

  private final BlockingQueue<ConfigNodeEvent> configNodeEvents = new LinkedBlockingQueue<>();

  private final ExecutorService configNodeExecutor = Executors.newSingleThreadExecutor();

  private void receiveConfigNodeEvent(ConfigNodeEvent event) {
    LOGGER.debug("received event :\n {}", event);
    configNodeEvents.add(event);
  }

  public Kind getKind() {
    return Kind.CONFIG_NODE;
  }

  public void reconcileConfigNode(ConfigNodeEvent event) throws IOException {
    IReconciler reconciler = getReconciler(event);
    LOGGER.info("{} begin to reconcile, eventId = {}", reconciler.getType(), event.getEventId());
    reconciler.reconcile();
    LOGGER.info("{} ended reconcile, eventId = {}", reconciler.getType(), event.getEventId());
  }

  private IReconciler getReconciler(ConfigNodeEvent event) {
    Action action = event.getAction();
    switch (action) {
      case ADDED:
        return new ConfigNodeStartUpReconciler(event);
      case DELETED:
        return new ConfigNodeDeleteReconciler();
      case MODIFIED:
        return new ConfigNodeUpdateReconciler(event);
      default:
        return new DefaultReconciler(event);
    }
  }

  @Override
  public void startDispatch() {
    configNodeExecutor.execute(
        () -> {
          LOGGER.info("start dispatching ConfigNode events...");
          while (!Thread.interrupted()) {
            ConfigNodeEvent event = null;
            try {
              event = configNodeEvents.take();
              reconcileConfigNode(event);
            } catch (InterruptedException e) {
              LOGGER.warn("thread has been interrupted!", e);
              Thread.currentThread().interrupt();
            } catch (Exception e) {
              assert event != null;
              LOGGER.error("event handle exception, eventId = {}", event.getEventId(), e);
            }
          }
        });
  }

  @Override
  public void startWatch(SharedInformerFactory factory) {
    SharedIndexInformer<ConfigNode> configNodeInformer =
        factory.sharedIndexInformerFor(ConfigNode.class, 0L);

    configNodeInformer.addEventHandler(
        new ResourceEventHandler<ConfigNode>() {
          @Override
          public void onAdd(ConfigNode obj) {
            ConfigNodeEvent event = new ConfigNodeEvent(Action.ADDED, obj);
            if (event.isSyntheticAdded()) {
              LOGGER.warn("received synthetic ADDED event, convert it to MODIFIED : \n {}", event);
              // we should treat synthetic added events as modified events.
              event = new ConfigNodeEvent(Action.MODIFIED, obj);
            }
            receiveConfigNodeEvent(event);
          }

          @Override
          public void onUpdate(ConfigNode oldObj, ConfigNode newObj) {
            ConfigNodeEvent event = new ConfigNodeEvent(Action.MODIFIED, newObj, oldObj);
            receiveConfigNodeEvent(event);
          }

          @Override
          public void onDelete(ConfigNode obj, boolean deletedFinalStateUnknown) {
            ConfigNodeEvent event = new ConfigNodeEvent(Action.DELETED, obj);
            receiveConfigNodeEvent(event);
          }
        });
  }
}
