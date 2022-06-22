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

import org.apache.iotdb.operator.controller.ConfigNodeController;
import org.apache.iotdb.operator.controller.IController;

import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class Watcher {

  private static final Logger LOGGER = LoggerFactory.getLogger(Watcher.class);

  /** todo there should be an event Controller to collect and report events issued by kubernetes */
  private final List<IController> controllers = Arrays.asList(new ConfigNodeController());

  public void start() {
    SharedInformerFactory factory = KubernetesClientManager.getInstance().getClient().informers();

    for (IController controller : controllers) {
      controller.startDispatch();
      controller.startWatch(factory);
    }

    factory.addSharedInformerEventListener(
        exception -> LOGGER.error("exception occurred : ", exception));

    factory.startAllRegisteredInformers();
  }
}
