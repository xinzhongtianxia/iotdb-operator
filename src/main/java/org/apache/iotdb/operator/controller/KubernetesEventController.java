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

import org.apache.iotdb.operator.event.KubernetesEventEvent;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This controller will collect Kubernetes Events and write them to a separate log file, will be a
 * benefit for developers to deal with some confuse environment problems.
 */
public class KubernetesEventController implements IController {
  private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesEventController.class);

  @Override
  public void startDispatch() {
    // do nothing
  }

  @Override
  public void startWatch() {
    kubernetesClient
        .resources(Event.class)
        .inNamespace(namespace)
        .inform(
            new ResourceEventHandler<Event>() {
              @Override
              public void onAdd(Event obj) {
                LOGGER.info("{}", new KubernetesEventEvent(Action.ADDED, obj));
              }

              @Override
              public void onUpdate(Event oldObj, Event newObj) {
                LOGGER.info("{}", new KubernetesEventEvent(Action.MODIFIED, newObj));
              }

              @Override
              public void onDelete(Event obj, boolean deletedFinalStateUnknown) {
                LOGGER.info("{}", new KubernetesEventEvent(Action.DELETED, obj));
              }
            });
  }
}
