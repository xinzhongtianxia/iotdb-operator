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
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.crd.Limits;

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

  protected final CommonSpec commonSpec;
  protected final ObjectMeta metadata;
  protected final String subResourceName;
  protected final String eventId;

  public StartUpReconciler(CommonSpec commonSpec, ObjectMeta metadata, Kind kind, String eventId) {
    this.commonSpec = commonSpec;
    this.metadata = metadata;
    subResourceName = metadata.getName().toLowerCase() + "-" + kind.getName().toLowerCase();
    this.eventId = eventId;
  }

  @Override
  public void reconcile() throws IOException {

    LOGGER.info("{} : createConfigMap", eventId);
    ConfigMap configMap = createConfigMap();

    LOGGER.info("{} : createServices", eventId);
    createServices();

    LOGGER.info("{} : createStatefulSet", eventId);
    createStatefulSet(configMap);
  }

  /**
   * To compute best-practice JVM memory options. Generally, it should be a relatively high
   * percentage of the container total memory, which makes no waste of system resources.
   */
  protected List<EnvVar> computeJVMMemory() {
    Limits resourceLimits = commonSpec.getLimits();
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
  protected abstract Map<String, String> createConfigFiles() throws IOException;

  private ConfigMap createConfigMap() throws IOException {

    Map<String, String> configFiles = createConfigFiles();

    ConfigMap configMap =
        new ConfigMapBuilder()
            .withNewMetadata()
            .withName(subResourceName)
            .withNamespace(metadata.getNamespace())
            .withLabels(getLabels())
            .endMetadata()
            .withData(configFiles)
            .build();
    return kubernetesClient
        .configMaps()
        .inNamespace(metadata.getNamespace())
        .resource(configMap)
        .create();
  }

  /** Common labels that need to be attached to iotdb resources. */
  protected abstract Map<String, String> getLabels();

  protected abstract StatefulSet createStatefulSet(ConfigMap configMap);

  protected abstract void createServices();

  protected void createIngress(String name, String namespace) {}
}
