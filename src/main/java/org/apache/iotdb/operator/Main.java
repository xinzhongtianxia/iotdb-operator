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

import org.apache.iotdb.operator.config.IoTDBOperatorConfig;
import org.apache.iotdb.operator.exception.ResourceAlreadyExistException;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) {
    try {
      new Main().run();
    } catch (Exception e) {
      LOGGER.error("Failed to run iotdb operator", e);
      System.exit(-1);
    }
  }

  private void run() throws ResourceAlreadyExistException {
    checkIfAlreadyExist();
    startWatcher();
  }

  private void checkIfAlreadyExist() throws ResourceAlreadyExistException {
    String namespace = IoTDBOperatorConfig.getInstance().getNamespace();
    String name = IoTDBOperatorConfig.getInstance().getName();
    KubernetesClient client = KubernetesClientManager.getInstance().getClient();
    long count =
        client
            .apps()
            .deployments()
            .inNamespace(namespace)
            .resources()
            .filter(d -> !d.get().getMetadata().getName().equals(name))
            .count();
    if (count > 0) {
      throw new ResourceAlreadyExistException(
          "there is already running IoTDB-Operator in this namespace");
    }
  }

  private void startWatcher() {
    new Watcher().start();
  }
}
