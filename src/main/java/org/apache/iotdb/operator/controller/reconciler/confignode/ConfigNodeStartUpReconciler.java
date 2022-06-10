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

import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.AffinityBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorFluentImpl;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodAffinity;
import io.fabric8.kubernetes.api.model.PodAffinityBuilder;
import io.fabric8.kubernetes.api.model.PodAffinityTerm;
import io.fabric8.kubernetes.api.model.PodAffinityTermBuilder;
import io.fabric8.kubernetes.api.model.PodAntiAffinity;
import io.fabric8.kubernetes.api.model.PodAntiAffinityBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodFluent.SpecNested;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTerm;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTermBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecFluent;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpecFluent;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpecFluent.SelectorNested;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpecFluentImpl.SelectorNestedImpl;
import java.util.Collections;
import org.apache.iotdb.operator.KubernetesClientManager;
import org.apache.iotdb.operator.common.CommonConstant;
import org.apache.iotdb.operator.common.Env;
import org.apache.iotdb.operator.config.ConfigNodeConfig;
import org.apache.iotdb.operator.controller.reconciler.StartUpReconciler;
import org.apache.iotdb.operator.crd.ConfigNodeSpec;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import org.apache.iotdb.operator.crd.Limits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigNodeStartUpReconciler extends StartUpReconciler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigNodeStartUpReconciler.class);

  @Override
  protected Map<String, String> constructConfig(
      ObjectMeta metadata, ConfigNodeSpec configNodeSpec) {
    Map<String, String> configFiles = new HashMap<>();

    // construct iotdb-confignode.properties
    Map<String, Object> ioTDBConfigMap = new HashMap<>();
    if (configNodeSpec.getIotdbConfig() != null) {
      Map<String, Object> configNodeProperties =
          configNodeSpec.getIotdbConfig().getConfigNodeProperties();
      ioTDBConfigMap.putAll(configNodeProperties);
    }

    Map<String, Object> configNodeDefaultConfigs =
        constructDefaultConfigNodeConfigs(metadata, configNodeSpec);
    ioTDBConfigMap.putAll(configNodeDefaultConfigs);

    StringBuilder sb = new StringBuilder();
    ioTDBConfigMap.forEach(
        (k, v) -> {
          sb.append(CommonConstant.GENERATE_BY_OPERATOR)
              .append(CommonConstant.LINES_SEPARATOR)
              .append(k)
              .append("=")
              .append(v)
              .append(CommonConstant.LINES_SEPARATOR);
        });
    String configNodeProperties = sb.toString();
    LOGGER.debug("config node properties : \n {}", configNodeProperties);
    configFiles.put(CommonConstant.CONFIG_NODE_PROPERTY_FILE, configNodeProperties);

    return configFiles;
  }

  private Map<String, Object> constructDefaultConfigNodeConfigs(
      ObjectMeta metadata, ConfigNodeSpec configNodeSpec) {

    Map<String, Object> defaultConfigMap = new HashMap<>();

    String name = metadata.getName();
    String namespace = metadata.getNamespace();
    // todo support multi-seednode
    // todo suffix maybe another string
    // todo IoTDB should use FQDN as its rpc_address if its value is "0.0.0.0".
    String targetConfigNode = name + "-0" + name + namespace + "svc.cluster.local";

    defaultConfigMap.put(CommonConstant.CONFIG_NODE_TARGET_CONFIG_NODE, targetConfigNode);
    defaultConfigMap.put(
        CommonConstant.CONFIG_NODE_RPC_ADDRESS, ConfigNodeConfig.getInstance().getRpcAddress());
    return defaultConfigMap;
  }

  @Override
  protected StatefulSet createStatefulSet(
      ObjectMeta meta, ConfigNodeSpec configNodeSpec, List<Env> envs, Map<String, String> labels,
      ConfigMap configMap, String headlessServiceName) {

    // metadata
    ObjectMeta metadata = new ObjectMetaBuilder()
        .withNamespace(meta.getNamespace())
        .withName(meta.getName())
        .withLabels(labels)
        .withDeletionGracePeriodSeconds(30L)
        .build();

    // affinity
    PodAntiAffinity podAntiAffinity = new PodAntiAffinity();
    if (configNodeSpec.getPodDistributeStrategy()
        .equals(CommonConstant.POD_AFFINITY_POLICY_PREFERRED)) {
      WeightedPodAffinityTerm weightedPodAffinityTerm = new WeightedPodAffinityTermBuilder()
          .withNewPodAffinityTerm()
          .withNewLabelSelector()
          .withMatchLabels(labels)
          .endLabelSelector()
          .withNamespaces(meta.getNamespace())
          .withTopologyKey("kubernetes.io/hostname")
          .endPodAffinityTerm()
          .withWeight(100)
          .build();
      podAntiAffinity.setPreferredDuringSchedulingIgnoredDuringExecution(
          Collections.singletonList(weightedPodAffinityTerm));
      LOGGER.info("use preferred pod distribution strategy");
    } else {
      PodAffinityTerm podAffinityTerm = new PodAffinityTermBuilder()
          .withNewLabelSelector()
          .withMatchLabels(labels)
          .endLabelSelector()
          .withNamespaces(meta.getNamespace())
          .withTopologyKey("kubernetes.io/hostname")
          .build();
      podAntiAffinity.setRequiredDuringSchedulingIgnoredDuringExecution(
          Collections.singletonList(podAffinityTerm));
      LOGGER.info("use required pod distribution strategy");
    }
    Affinity affinity = new AffinityBuilder().withPodAntiAffinity(podAntiAffinity).build();

    // container
    // --probe
    Probe startupProbe = new ProbeBuilder()
        .withNewTcpSocket()
        .withPort(new IntOrString(ConfigNodeConfig.getInstance().getRpcPort()))
        .endTcpSocket()
        .withPeriodSeconds(3)
        .withFailureThreshold(60)
        .build();
    Probe readinessAndLivenessProbe = new ProbeBuilder()
        .withNewTcpSocket()
        .withPort(new IntOrString(ConfigNodeConfig.getInstance().getRpcPort()))
        .endTcpSocket()
        .withPeriodSeconds(3)
        .withFailureThreshold(3)
        .build();

    Map<String, Quantity> resourceLimits = new HashMap<>();
    resourceLimits.put("cpu", new Quantity(configNodeSpec.getLimits().getCpu()))
    ResourceRequirements resourceRequirements = new ResourceRequirementsBuilder()
        .withLimits()

    return null;
  }

  @Override
  protected void scaleOut(StatefulSet statefulSet) {
  }

  @Override
  protected String createServices(
      ObjectMeta metadata, Map<String, String> labels, Map<String, String> configs) {
    // 1. create internal-service for internal communication.
    int consensusPort = ConfigNodeConfig.getInstance().getConsensusPort();
    ServicePort consensusServicePort =
        new ServicePortBuilder()
            .withNewTargetPort(consensusPort)
            .withPort(consensusPort)
            .withName(CommonConstant.CONFIG_NODE_CONSENSUS_PORT)
            .build();
    Service internalService =
        new ServiceBuilder()
            .withNewMetadata()
            .withName(metadata.getName() + "-internal")
            .withNamespace(metadata.getNamespace())
            .withLabels(labels)
            .endMetadata()
            .withNewSpec()
            .withPorts(consensusServicePort)
            .endSpec()
            .build();

    // 2. create external-service for external connections from DataNode
    int rpcPort = ConfigNodeConfig.getInstance().getRpcPort();
    ServicePort rpcServicePort =
        new ServicePortBuilder()
            .withNewTargetPort(rpcPort)
            .withPort(rpcPort)
            .withName(CommonConstant.CONFIG_NODE_RPC_PORT)
            .build();
    Service externalService =
        new ServiceBuilder()
            .withNewMetadata()
            .withName(metadata.getName() + "-external")
            .withNamespace(metadata.getNamespace())
            .withLabels(labels)
            .endMetadata()
            .withNewSpec()
            .withPorts(rpcServicePort)
            .endSpec()
            .build();

    KubernetesClientManager.getInstance()
        .getClient()
        .services()
        .inNamespace(metadata.getNamespace())
        .create(internalService, externalService);

    return internalService.getMetadata().getName();
  }
}
