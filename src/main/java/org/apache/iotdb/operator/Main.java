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

import org.apache.iotdb.operator.common.CommonConstant;
import org.apache.iotdb.operator.config.IoTDBOperatorConfig;
import org.apache.iotdb.operator.exception.ResourceAlreadyExistException;
import org.apache.iotdb.operator.service.HttpService;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

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

  private void run() throws ResourceAlreadyExistException, IOException {
    String scope = IoTDBOperatorConfig.getInstance().getScope();
    checkIfAlreadyExist(scope);

    startWatcher();

    startHttpService();

    LOGGER.info("IoTDB Operator started in {} scope", scope);
  }

  private void startHttpService() {
    HttpService.getInstance().start();
  }

  private void checkIfAlreadyExist(String scope) throws ResourceAlreadyExistException {
    String name = IoTDBOperatorConfig.getInstance().getName();
    KubernetesClient client = KubernetesClientManager.getInstance().getClient();
    long count;
    if (scope.equals(CommonConstant.SCOPE_CLUSTER)) {
      count =
          client
              .apps()
              .deployments()
              .inAnyNamespace()
              .withLabels(IoTDBOperatorConfig.getInstance().getOperatorDeploymentLabels())
              .resources()
              .filter(d -> !d.get().getMetadata().getName().equals(name))
              .count();
    } else {
      String namespace = IoTDBOperatorConfig.getInstance().getNamespace();
      count =
          client
              .apps()
              .deployments()
              .inNamespace(namespace)
              .withLabels(IoTDBOperatorConfig.getInstance().getOperatorDeploymentLabels())
              .resources()
              .filter(d -> !d.get().getMetadata().getName().equals(name))
              .count();
    }

    if (count > 0) {
      throw new ResourceAlreadyExistException(
          "there is already running IoTDB-Operator in this namespace");
    }
  }

  private void startWatcher() {
    new Watcher().start();
  }
}
