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

package org.apache.iotdb.operator;

import org.apache.iotdb.operator.common.BaseEvent;
import org.apache.iotdb.operator.config.IoTDBOperatorConfig;
import org.apache.iotdb.operator.controller.ConfigNodeController;
import org.apache.iotdb.operator.controller.DataNodeController;
import org.apache.iotdb.operator.controller.IController;
import org.apache.iotdb.operator.crd.CommonSpec;
import org.apache.iotdb.operator.crd.CommonStatus;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.WatcherException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class Watcher {

  private static final Logger LOGGER = LoggerFactory.getLogger(Watcher.class);

  private final List<IController> controllers =
      Arrays.asList(new ConfigNodeController(), new DataNodeController());

  public void start() {
    for (IController controller : controllers) {
      preWatch(controller);
      watch(controller);
    }
  }

  private void preWatch(IController controller) {
    controller.startDispatch();
  }

  private void watch(IController controller) {
    KubernetesClient client = KubernetesClientManager.getInstance().getClient();
    client
        .resources(controller.getResourceClass(), controller.getResourceListClass())
        .inNamespace(IoTDBOperatorConfig.getInstance().getNameSpace())
        .watch(
            new io.fabric8.kubernetes.client.Watcher<CustomResource<CommonSpec, CommonStatus>>() {
              @Override
              public void eventReceived(
                  Action action, CustomResource<CommonSpec, CommonStatus> resource) {
                BaseEvent event = new BaseEvent(action, controller.getKind(), resource);
                controller.receiveEvent(event);
              }

              @Override
              public void onClose(WatcherException cause) {
                LOGGER.error("Watcher connection closed! kind = {}", controller.getKind(), cause);
              }
            });
  }
}
