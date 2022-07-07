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

import org.apache.iotdb.operator.common.CommonConstant;
import org.apache.iotdb.operator.controller.reconciler.DefaultReconciler;
import org.apache.iotdb.operator.controller.reconciler.IReconciler;
import org.apache.iotdb.operator.controller.reconciler.confignode.ConfigNodeStatefulSetReconciler;
import org.apache.iotdb.operator.controller.reconciler.datanode.DataNodeStatefulSetReconciler;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.event.StatefulSetEvent;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class StatefulSetController implements IController {
  private static final Logger LOGGER = LoggerFactory.getLogger(StatefulSetController.class);

  private final BlockingQueue<StatefulSetEvent> statefulSetEvents = new LinkedBlockingQueue<>();

  private final ExecutorService statefulSetExecutor = Executors.newSingleThreadExecutor();

  private void receiveStatefulSetEvent(StatefulSetEvent event) {
    // filter events irrelevant to IoTDB
    Map<String, String> labels = event.getStatefulSet().getMetadata().getLabels();
    if (!labels.containsKey(CommonConstant.LABEL_KEY_MANAGED_BY)) {
      return;
    }
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("received event :\n {}", event);
    } else {
      LOGGER.info("received event : {}", event.getEventId());
    }
    statefulSetEvents.add(event);
  }

  public void reconcileStatefulSet(StatefulSetEvent event) throws IOException {
    LOGGER.info("StatefulSetReconciler begin to reconcile, eventId = {}", event.getEventId());
    IReconciler reconciler = getReconciler(event);
    reconciler.reconcile();
    LOGGER.info("StatefulSetReconciler ended reconcile, eventId = {}", event.getEventId());
  }

  private IReconciler getReconciler(StatefulSetEvent event) {
    String kindLabel =
        event.getStatefulSet().getMetadata().getLabels().get(CommonConstant.LABEL_KEY_APP_KIND);
    if (Kind.CONFIG_NODE.getName().toLowerCase().equals(kindLabel)) {
      return new ConfigNodeStatefulSetReconciler(event);
    } else if (Kind.DATA_NODE.getName().toLowerCase().equals(kindLabel)) {
      return new DataNodeStatefulSetReconciler(event);
    } else {
      return new DefaultReconciler(event);
    }
  }

  @Override
  public void startDispatch() {
    statefulSetExecutor.execute(
        () -> {
          LOGGER.info("start dispatching ConfigNode-StatefulSet events...");
          while (!Thread.interrupted()) {
            StatefulSetEvent event = null;
            try {
              event = statefulSetEvents.take();
              if (event.getAction() == Action.ADDED) {
                continue;
              }
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

  @Override
  public void startWatch() {
    kubernetesClient
        .resources(StatefulSet.class)
        .inNamespace(namespace)
        .inform(
            new ResourceEventHandler<StatefulSet>() {
              @Override
              public void onAdd(StatefulSet obj) {
                StatefulSetEvent event = new StatefulSetEvent(Action.ADDED, obj);
                receiveStatefulSetEvent(event);
              }

              @Override
              public void onUpdate(StatefulSet oldObj, StatefulSet newObj) {
                StatefulSetEvent event = new StatefulSetEvent(Action.MODIFIED, newObj, oldObj);
                receiveStatefulSetEvent(event);
              }

              @Override
              public void onDelete(StatefulSet obj, boolean deletedFinalStateUnknown) {
                StatefulSetEvent event = new StatefulSetEvent(Action.DELETED, obj);
                receiveStatefulSetEvent(event);
              }
            });
  }
}
