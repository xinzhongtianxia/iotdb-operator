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

package org.apache.iotdb.operator.controller.reconciler.confignode;

import org.apache.iotdb.operator.common.CommonConstant;
import org.apache.iotdb.operator.config.ConfigNodeConfig;
import org.apache.iotdb.operator.controller.reconciler.StartUpReconciler;
import org.apache.iotdb.operator.crd.ConfigNodeSpec;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.event.CustomResourceEvent;
import org.apache.iotdb.operator.util.OutputEventUtils;
import org.apache.iotdb.operator.util.ReconcilerUtils;

import org.apache.commons.io.IOUtils;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigNodeStartUpReconciler extends StartUpReconciler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigNodeStartUpReconciler.class);
  private final ConfigNodeConfig configNodeConfig = ConfigNodeConfig.getInstance();

  public ConfigNodeStartUpReconciler(CustomResourceEvent event) {
    super(
        event.getResource().getSpec(),
        event.getResource().getMetadata(),
        event.getKind(),
        event.getEventId());
  }

  @Override
  protected boolean checkInitConditions() {
    return true;
  }

  @Override
  protected Map<String, String> createConfigFiles() throws IOException {
    Map<String, String> configFiles = new HashMap<>();
    ConfigNodeSpec configNodeSpec = (ConfigNodeSpec) commonSpec;

    // construct iotdb-confignode.properties
    Map<String, Object> properties = new HashMap<>();
    if (configNodeSpec.getIotdbConfig() != null) {
      Map<String, Object> configNodeProperties =
          configNodeSpec.getIotdbConfig().getConfigNodeProperties();
      properties.putAll(configNodeProperties);
    }

    Map<String, Object> configNodeDefaultConfigs =
        constructDefaultConfigNodeConfigs(subResourceName, metadata.getNamespace());
    properties.putAll(configNodeDefaultConfigs);

    StringBuilder sb = new StringBuilder(CommonConstant.GENERATE_BY_OPERATOR);
    properties.forEach(
        (k, v) -> {
          sb.append(CommonConstant.LINES_SEPARATOR)
              .append(k)
              .append("=")
              .append(v)
              .append(CommonConstant.LINES_SEPARATOR);
        });

    String configNodeProperties = sb.toString();
    LOGGER.debug(
        "========== iotdb-confignode.properties : ============  \n {}", configNodeProperties);
    configFiles.put(CommonConstant.CONFIG_NODE_PROPERTY_FILE_NAME, configNodeProperties);

    // read init script into ConfigMap
    String scriptContent =
        IOUtils.toString(
            getClass()
                .getResourceAsStream(
                    File.separator
                        + "conf"
                        + File.separator
                        + CommonConstant.CONFIG_NODE_INIT_SCRIPT_FILE_NAME),
            Charset.defaultCharset());
    LOGGER.debug("========== confignode-init.sh : ============  : \n {}", scriptContent);
    configFiles.put(CommonConstant.CONFIG_NODE_INIT_SCRIPT_FILE_NAME, scriptContent);
    return configFiles;
  }

  @Override
  protected Map<String, String> getLabels() {
    Map<String, String> labels = configNodeConfig.getAdditionalLabels();
    labels.put(CommonConstant.LABEL_KEY_APP_NAME, subResourceName);
    return labels;
  }

  @Override
  protected List<EnvVar> getEnvs() {
    return ReconcilerUtils.computeJVMMemory(commonSpec.getLimits());
  }

  @Override
  protected String getStartArgs() {
    return configNodeConfig.getStartArgs();
  }

  @Override
  protected List<ContainerPort> createContainerPort() {
    ContainerPort consensusPort =
        new ContainerPortBuilder()
            .withName("consensus")
            .withContainerPort(configNodeConfig.getConsensusPort())
            .build();

    ContainerPort rpcPort =
        new ContainerPortBuilder()
            .withName("rpc")
            .withContainerPort(configNodeConfig.getInternalPort())
            .build();

    ContainerPort metricPort =
        new ContainerPortBuilder()
            .withName("metric")
            .withContainerPort(configNodeConfig.getMetricPort())
            .build();

    return Arrays.asList(consensusPort, rpcPort, metricPort);
  }

  /** set `target_confignode` and 'rpc_address' for `confignode-properties`. */
  private Map<String, Object> constructDefaultConfigNodeConfigs(String name, String namespace) {
    Map<String, Object> defaultConfigMap = new HashMap<>();

    // todo support multi-seednode
    // todo suffix maybe another string
    String targetConfigNode =
        name
            + "-0."
            + name
            + "."
            + namespace
            + ".svc.cluster.local:"
            + configNodeConfig.getInternalPort();

    defaultConfigMap.put(CommonConstant.CONFIG_NODE_TARGET_CONFIG_NODE, targetConfigNode);
    defaultConfigMap.put(
        CommonConstant.CONFIG_NODE_INTERNAL_ADDRESS, configNodeConfig.getInternalAddress());
    defaultConfigMap.put(
        CommonConstant.CONFIG_NODE_CONSENSUS_PORT, configNodeConfig.getConsensusPort());
    defaultConfigMap.put(
        CommonConstant.CONFIG_NODE_INTERNAL_PORT, configNodeConfig.getInternalPort());

    return defaultConfigMap;
  }

  @Override
  protected Probe createStartupProbe() {
    return new ProbeBuilder()
        .withNewTcpSocket()
        .withPort(new IntOrString(configNodeConfig.getInternalPort()))
        .endTcpSocket()
        .withPeriodSeconds(3)
        .withFailureThreshold(60)
        .build();
  }

  @Override
  protected Probe createReadinessProbe() {
    return new ProbeBuilder()
        .withNewTcpSocket()
        .withPort(new IntOrString(configNodeConfig.getInternalPort()))
        .endTcpSocket()
        .withPeriodSeconds(3)
        .withFailureThreshold(3)
        .build();
  }

  @Override
  protected Probe createLivenessProbe() {
    return new ProbeBuilder()
        .withNewTcpSocket()
        .withPort(new IntOrString(configNodeConfig.getInternalPort()))
        .endTcpSocket()
        .withPeriodSeconds(3)
        .withFailureThreshold(10)
        .build();
  }

  @Override
  public Map<String, Service> createServices() {
    int consensusPort = configNodeConfig.getConsensusPort();
    ServicePort consensusServicePort =
        new ServicePortBuilder()
            .withNewTargetPort(consensusPort)
            .withPort(consensusPort)
            .withName("consensus")
            .build();

    int rpcPort = configNodeConfig.getInternalPort();
    ServicePort rpcServicePort =
        new ServicePortBuilder()
            .withNewTargetPort(rpcPort)
            .withPort(rpcPort)
            .withName("rpc")
            .build();

    int metricPort = configNodeConfig.getMetricPort();
    ServicePort metricServicePort =
        new ServicePortBuilder()
            .withNewTargetPort(metricPort)
            .withPort(metricPort)
            .withName("metric")
            .build();

    // for consensus among confignodes
    Service internalService =
        new ServiceBuilder()
            .withNewMetadata()
            .withName(subResourceName)
            .withNamespace(metadata.getNamespace())
            .withLabels(getLabels())
            .endMetadata()
            .withNewSpec()
            .withSelector(getLabels())
            .withPorts(consensusServicePort, metricServicePort)
            .withClusterIP("None")
            .endSpec()
            .build();

    // for rpc from datanode and metrics from prometheus
    Service externalService =
        new ServiceBuilder()
            .withNewMetadata()
            .withName(subResourceName + CommonConstant.SERVICE_SUFFIX_EXTERNAL)
            .withNamespace(metadata.getNamespace())
            .withLabels(getLabels())
            .endMetadata()
            .withNewSpec()
            .withSelector(getLabels())
            .withPorts(rpcServicePort)
            .endSpec()
            .build();

    kubernetesClient
        .services()
        .inNamespace(metadata.getNamespace())
        .resource(internalService)
        .createOrReplace();
    kubernetesClient
        .services()
        .inNamespace(metadata.getNamespace())
        .resource(externalService)
        .createOrReplace();

    OutputEventUtils.sendEvent(
        kind,
        OutputEventUtils.EVENT_TYPE_NORMAL,
        "CreateService",
        metadata,
        "Successfully created Service "
            + subResourceName
            + " and "
            + subResourceName
            + CommonConstant.SERVICE_SUFFIX_EXTERNAL,
        "Created",
        Kind.SERVICE.getName());
    Map<String, Service> serviceMap = new HashMap<>(2);
    serviceMap.put(internalService.getMetadata().getName(), internalService);
    serviceMap.put(externalService.getMetadata().getName(), externalService);
    return serviceMap;
  }

  @Override
  public ReconcilerType getType() {
    return ReconcilerType.CONFIG_NODE_STARTUP;
  }
}
