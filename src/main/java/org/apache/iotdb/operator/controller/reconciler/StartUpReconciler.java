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

import org.apache.iotdb.operator.common.EnvKey;
import org.apache.iotdb.operator.crd.CommonSpec;
import org.apache.iotdb.operator.crd.Limits;
import org.apache.iotdb.operator.event.BaseEvent;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public abstract class StartUpReconciler implements IReconciler {

  private static final Logger LOGGER = LoggerFactory.getLogger(StartUpReconciler.class);

  @Override
  public void reconcile(BaseEvent event) throws IOException {

    CommonSpec commonSpec = getResourceSpec(event);

    ObjectMeta metadata = getMetadata(event);

    String name = metadata.getName().toLowerCase() + "-" + event.getKind().getName().toLowerCase();
    String namespace = metadata.getNamespace();

    LOGGER.info("{} : createConfigMap", event.getEventId());
    ConfigMap configMap = createConfigMap(name, namespace, commonSpec);

    LOGGER.info("{} : createServices", event.getEventId());
    createServices(name, namespace, getLabels(name));

    LOGGER.info("{} : createStatefulSet", event.getEventId());
    createStatefulSet(
        name,
        namespace,
        commonSpec,
        computeJVMMemory(commonSpec.getLimits()),
        getLabels(name),
        configMap);
  }

  /** Get MetaData from event. */
  protected abstract ObjectMeta getMetadata(BaseEvent event);

  /** Get ResourceSpec from event. */
  protected abstract CommonSpec getResourceSpec(BaseEvent event);

  /**
   * To compute best-practice JVM memory options. Generally, it should be a relatively high
   * percentage of the container total memory, which makes no waste of system resources.
   *
   * @param resourceLimits in CRD
   * @return JVM memory options
   */
  protected List<EnvVar> computeJVMMemory(Limits resourceLimits) {
    int memory = resourceLimits.getMemory();
    int maxHeapMemorySize = memory * 60 / 100;
    int maxDirectMemorySize = maxHeapMemorySize * 20 / 100;

    EnvVar heapMemoryEnv =
        new EnvVarBuilder()
            .withName(EnvKey.IOTDB_MAX_HEAP_MEMORY_SIZE.name())
            .withValue(maxHeapMemorySize + "M")
            .build();

    EnvVar directMemoryEnv =
        new EnvVarBuilder()
            .withName(EnvKey.IOTDB_MAX_DIRECT_MEMORY_SIZE.name())
            .withValue(maxDirectMemorySize + "M")
            .build();

    return Arrays.asList(heapMemoryEnv, directMemoryEnv);
  }

  /** Create files that need to be mounted to container via ConfigMap. */
  protected abstract Map<String, String> createConfigFiles(
      String name, String namespace, CommonSpec baseSpec) throws IOException;

  private ConfigMap createConfigMap(String name, String namespace, CommonSpec commonSpec)
      throws IOException {

    Map<String, String> configFiles = createConfigFiles(name, namespace, commonSpec);

    ConfigMap configMap =
        new ConfigMapBuilder()
            .withNewMetadata()
            .withName(name)
            .withNamespace(namespace)
            .withLabels(getLabels(name))
            .endMetadata()
            .withData(configFiles)
            .build();
    return kubernetesClient.configMaps().inNamespace(namespace).create(configMap);
  }

  /** Common labels that need to be attached to iotdb resources. */
  protected abstract Map<String, String> getLabels(String name);

  protected abstract StatefulSet createStatefulSet(
      String name,
      String namespace,
      CommonSpec commonSpec,
      List<EnvVar> envs,
      Map<String, String> labels,
      ConfigMap configMap);

  protected abstract void createServices(String name, String namespace, Map<String, String> labels);

  protected void createIngress(String name, String namespace) {}
}
