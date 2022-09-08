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
import org.apache.iotdb.operator.config.IoTDBOperatorConfig;
import org.apache.iotdb.operator.controller.reconciler.IReconciler;
import org.apache.iotdb.operator.crd.CommonSpec;
import org.apache.iotdb.operator.crd.CommonStatus;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.event.CustomResourceEvent;
import org.apache.iotdb.operator.util.OutputEventUtils;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.dsl.Informable;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class AbstractCustomResourceController implements IController {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(AbstractCustomResourceController.class);

  private final BlockingQueue<CustomResourceEvent> resourceEvents = new LinkedBlockingQueue<>();

  private final ExecutorService executor;

  private final Kind kind;

  public AbstractCustomResourceController(Kind kind) {
    this.kind = kind;
    executor = Executors.newSingleThreadExecutor(r -> new Thread(r, kind.getName()));
  }

  @Override
  public void startWatch() {
    Informable<? extends CustomResource<? extends CommonSpec, CommonStatus>> informable;

    String scope = IoTDBOperatorConfig.getInstance().getScope();
    if (scope.equals(CommonConstant.SCOPE_NAMESPACE)) {
      String namespace = IoTDBOperatorConfig.getInstance().getNamespace();
      informable = kubernetesClient.resources(getResourceType()).inNamespace(namespace);
    } else {
      informable = kubernetesClient.resources(getResourceType()).inAnyNamespace();
    }

    LOGGER.info("start watch {} resources...", kind.getName());
    informable.inform(
        new ResourceEventHandler<CustomResource<? extends CommonSpec, CommonStatus>>() {
          @Override
          public void onAdd(CustomResource<? extends CommonSpec, CommonStatus> obj) {
            CustomResourceEvent event = new CustomResourceEvent(Action.ADDED, kind, obj, null);
            if (!event.isSyntheticAdded()) {
              LOGGER.debug("received ADDED event : {}", event);
              resourceEvents.add(event);
            }
          }

          @Override
          public void onUpdate(
              CustomResource<? extends CommonSpec, CommonStatus> oldObj,
              CustomResource<? extends CommonSpec, CommonStatus> newObj) {
            CustomResourceEvent event =
                new CustomResourceEvent(Action.MODIFIED, kind, newObj, oldObj);
            LOGGER.debug("received MODIFIED event : {}", event);
            resourceEvents.add(event);
          }

          @Override
          public void onDelete(
              CustomResource<? extends CommonSpec, CommonStatus> obj,
              boolean deletedFinalStateUnknown) {
            CustomResourceEvent event = new CustomResourceEvent(Action.DELETED, kind, obj, null);
            LOGGER.debug("received DELETED event : {}", event);
            resourceEvents.add(event);
          }
        });
  }

  protected abstract Class<? extends CustomResource<? extends CommonSpec, CommonStatus>>
      getResourceType();

  @Override
  public void startDispatch() {
    executor.execute(
        () -> {
          LOGGER.info("start dispatching {} events...", kind.getName());
          while (!Thread.interrupted()) {
            CustomResourceEvent event = null;
            try {
              event = resourceEvents.take();
              IReconciler reconciler = getReconciler(event);
              LOGGER.info(
                  "{} begin to reconcile, eventId = {}", reconciler.getType(), event.getEventId());
              reconciler.reconcile();
              LOGGER.info(
                  "{} ended reconcile, eventId = {}", reconciler.getType(), event.getEventId());
            } catch (InterruptedException e) {
              LOGGER.warn("thread has been interrupted!", e);
              Thread.currentThread().interrupt();
            } catch (Exception e) {
              handleReconcileException(event, e);
            }
          }
        });
  }

  private void handleReconcileException(CustomResourceEvent event, Exception e) {
    String eventId = "UnKnown";
    if (event != null) {
      eventId = event.getEventId();
      ObjectMeta meta = event.getResource().getMetadata();
      OutputEventUtils.sendEvent(
          kind,
          OutputEventUtils.EVENT_TYPE_WARNING,
          "event_reconcile_" + event.getAction().name(),
          meta,
          e.getCause().toString(),
          "Exception",
          kind.getName());
    }
    LOGGER.error("event handle exception, eventId = {}", eventId, e);
  }

  protected abstract IReconciler getReconciler(CustomResourceEvent event);
}
