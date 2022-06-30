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
import org.apache.iotdb.operator.controller.reconciler.confignode.ConfigNodeStatefulSetReconciler;
import org.apache.iotdb.operator.controller.reconciler.confignode.ConfigNodeUpdateReconciler;
import org.apache.iotdb.operator.crd.ConfigNode;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.event.ConfigNodeEvent;
import org.apache.iotdb.operator.event.StatefulSetEvent;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
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

  private final BlockingQueue<StatefulSetEvent> statefulSetEvents = new LinkedBlockingQueue<>();

  private final ExecutorService configNodeExecutor = Executors.newSingleThreadExecutor();
  private final ExecutorService statefulSetExecutor = Executors.newSingleThreadExecutor();

  private void receiveConfigNodeEvent(ConfigNodeEvent event) {
    LOGGER.debug("received event :\n {}", event);
    configNodeEvents.add(event);
  }

  private void receiveStatefulSetEvent(StatefulSetEvent event) {
    LOGGER.debug("received event :\n {}", event);
    statefulSetEvents.add(event);
  }

  public Kind getKind() {
    return Kind.CONFIG_NODE;
  }

  private void startDispatchStatefulSetEvents() {
    statefulSetExecutor.execute(
        () -> {
          LOGGER.info("start dispatching ConfigNode-StatefulSet events...");
          while (!Thread.interrupted()) {
            StatefulSetEvent event = null;
            try {
              event = statefulSetEvents.take();
              reconcileStatefulSet(event);
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

  private void startDispatchConfigNodeEvents() {
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

  public void reconcileConfigNode(ConfigNodeEvent event) throws IOException {
    IReconciler reconciler = getReconciler(event);
    LOGGER.info("{} begin to reconcile, eventId = {}", reconciler.getType(), event.getEventId());
    reconciler.reconcile();
    LOGGER.info("{} ended reconcile, eventId = {}", reconciler.getType(), event.getEventId());
  }

  public void reconcileStatefulSet(StatefulSetEvent event) {
    LOGGER.info("StatefulSetReconciler begin to reconcile, eventId = {}", event.getEventId());
    new ConfigNodeStatefulSetReconciler(event).reconcile();
    LOGGER.info("StatefulSetReconciler ended reconcile, eventId = {}", event.getEventId());
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
    startDispatchStatefulSetEvents();
    startDispatchConfigNodeEvents();
  }

  @Override
  public void startWatch(SharedInformerFactory factory) {
    startWatchStatefulSet(factory);
    startWatchConfigNode(factory);
  }

  private void startWatchConfigNode(SharedInformerFactory factory) {
    SharedIndexInformer<ConfigNode> configNodeInformer =
        factory.sharedIndexInformerFor(ConfigNode.class, 0L);

    configNodeInformer.addEventHandler(
        new ResourceEventHandler<ConfigNode>() {
          @Override
          public void onAdd(ConfigNode obj) {
            ConfigNodeEvent event = new ConfigNodeEvent(Action.ADDED, Kind.CONFIG_NODE, obj);
            if (event.isSyntheticAdded()) {
              LOGGER.warn("received synthetic ADDED event, convert it to MODIFIED : \n {}", event);
              // we should treat synthetic added events as modified events.
              event = new ConfigNodeEvent(Action.MODIFIED, Kind.CONFIG_NODE, obj);
            }
            receiveConfigNodeEvent(event);
          }

          @Override
          public void onUpdate(ConfigNode oldObj, ConfigNode newObj) {
            ConfigNodeEvent event =
                new ConfigNodeEvent(Action.MODIFIED, Kind.CONFIG_NODE, newObj, oldObj);
            receiveConfigNodeEvent(event);
          }

          @Override
          public void onDelete(ConfigNode obj, boolean deletedFinalStateUnknown) {
            ConfigNodeEvent event = new ConfigNodeEvent(Action.DELETED, Kind.CONFIG_NODE, obj);
            receiveConfigNodeEvent(event);
          }
        });
  }

  private void startWatchStatefulSet(SharedInformerFactory factory) {
    SharedIndexInformer<StatefulSet> stsInformer =
        factory.sharedIndexInformerFor(StatefulSet.class, 0L);

    stsInformer.addEventHandler(
        new ResourceEventHandler<StatefulSet>() {
          @Override
          public void onAdd(StatefulSet obj) {
            StatefulSetEvent event = new StatefulSetEvent(Action.ADDED, Kind.STATEFUL_SET, obj);
            if (event.isSyntheticAdded()) {
              LOGGER.warn("received synthetic Added event : \n {}", event);
              return;
            }
            receiveStatefulSetEvent(event);
          }

          @Override
          public void onUpdate(StatefulSet oldObj, StatefulSet newObj) {
            StatefulSetEvent event =
                new StatefulSetEvent(Action.MODIFIED, Kind.STATEFUL_SET, newObj, oldObj);
            receiveStatefulSetEvent(event);
          }

          @Override
          public void onDelete(StatefulSet obj, boolean deletedFinalStateUnknown) {
            StatefulSetEvent event = new StatefulSetEvent(Action.MODIFIED, Kind.STATEFUL_SET, obj);
            receiveStatefulSetEvent(event);
          }
        });
  }
}
