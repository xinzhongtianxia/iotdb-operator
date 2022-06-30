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

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

public class KubernetesClientManager {

  private final KubernetesClient client;

  private KubernetesClientManager() {
    String namespace = IoTDBOperatorConfig.getInstance().getNamespace();
    this.client =
        new KubernetesClientBuilder()
            .withConfig(new ConfigBuilder().withNamespace(namespace).build())
            .build();
  }

  public static KubernetesClientManager getInstance() {
    return Holder.INSTANCE;
  }

  public KubernetesClient getClient() {
    return client;
  }

  private static class Holder {
    private static final KubernetesClientManager INSTANCE = new KubernetesClientManager();

    private Holder() {}
  }
}
