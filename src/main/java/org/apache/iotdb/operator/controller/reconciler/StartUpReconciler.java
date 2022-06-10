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

package org.apache.iotdb.operator.controller.reconciler;

import io.fabric8.kubernetes.api.model.Service;
import org.apache.iotdb.operator.KubernetesClientManager;
import org.apache.iotdb.operator.common.Env;
import org.apache.iotdb.operator.common.EnvKey;
import org.apache.iotdb.operator.crd.CommonSpec;
import org.apache.iotdb.operator.crd.CommonStatus;
import org.apache.iotdb.operator.crd.ConfigNodeSpec;
import org.apache.iotdb.operator.crd.Limits;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.CustomResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class StartUpReconciler implements IReconciler {

  private static final Logger LOGGER = LoggerFactory.getLogger(StartUpReconciler.class);

  @Override
  public void reconcile(CustomResource<CommonSpec, CommonStatus> event) {
    ConfigNodeSpec configNodeSpec = (ConfigNodeSpec) event.getSpec();

    // 1. Compute JVM memory
    List<Env> JVMMemoryOptions = computeJVMMemory(configNodeSpec.getLimits());

    // 2. Create configuration files
    Map<String, String> configFiles = constructConfig(event.getMetadata(), configNodeSpec);

    // 3. Create ConfigMap
    // todo add labels to declare it is an IoTDB resource.
    ConfigMap configMap = createConfigMap(event.getMetadata(), Collections.emptyMap(), configFiles);

    // 4. Create services
    String headlessServiceName =
        createServices(event.getMetadata(), Collections.emptyMap(), configFiles);

    // 5. Create StatefulSet and set its replicas to 1
    StatefulSet statefulSet =
        createStatefulSet(event.getMetadata(), configNodeSpec, JVMMemoryOptions,
            Collections.emptyMap(), configMap, headlessServiceName);

    // 6. Call ScaleReconciler to scale replicas to desired state
    scaleOut(statefulSet);
  }

  /**
   * To compute best-practice JVM memory options. Generally, it should be a high percentage of the
   * container total limit memory, which makes no waste of system resources.
   *
   * @param resourceLimits in CRD
   * @return JVM memory options
   */
  protected List<Env> computeJVMMemory(Limits resourceLimits) {
    int memory = resourceLimits.getMemory();
    int maxHeapMemorySize = memory * 60 / 100;
    int maxDirectMemorySize = maxHeapMemorySize * 20 / 100;
    return Arrays.asList(
        new Env(EnvKey.IOTDB_MAX_HEAP_MEMORY_SIZE.name(), String.valueOf(maxHeapMemorySize)),
        new Env(EnvKey.IOTDB_MAX_DIRECT_MEMORY_SIZE.name(), String.valueOf(maxDirectMemorySize)));
  }

  protected abstract Map<String, String> constructConfig(
      ObjectMeta metadata, ConfigNodeSpec configNodeSpec);

  protected ConfigMap createConfigMap(
      ObjectMeta metadata, Map<String, String> labels, Map<String, String> configs) {
    ConfigMap configMap =
        new ConfigMapBuilder()
            .withNewMetadata()
            .withName(metadata.getName())
            .withNamespace(metadata.getNamespace())
            .withLabels(labels)
            .endMetadata()
            .withData(configs)
            .build();
    KubernetesClientManager.getInstance()
        .getClient()
        .configMaps()
        .inNamespace(metadata.getNamespace())
        .create(configMap);
    return configMap;
  }

  protected abstract StatefulSet createStatefulSet(ObjectMeta meta, ConfigNodeSpec configNodeSpec,
      List<Env> envs, Map<String, String> labels, ConfigMap configMap,
      String headlessServiceName);

  protected abstract void scaleOut(StatefulSet statefulSet);

  /**
   * This function should return a service name which you want to used as as part of pod's FQDN.
   */
  protected abstract String createServices(
      ObjectMeta metadata, Map<String, String> labels, Map<String, String> configs);
}
