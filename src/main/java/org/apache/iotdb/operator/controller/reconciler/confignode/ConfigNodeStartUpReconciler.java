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
import org.apache.iotdb.operator.controller.reconciler.ReconcileUtils;
import org.apache.iotdb.operator.controller.reconciler.StartUpReconciler;
import org.apache.iotdb.operator.crd.ConfigNodeSpec;
import org.apache.iotdb.operator.event.BaseEvent;
import org.apache.iotdb.operator.event.ConfigNodeEvent;
import org.apache.iotdb.operator.util.DigestUtil;

import org.apache.commons.io.IOUtils;
import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.AffinityBuilder;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PodAffinityTerm;
import io.fabric8.kubernetes.api.model.PodAffinityTermBuilder;
import io.fabric8.kubernetes.api.model.PodAntiAffinity;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.PodDNSConfigBuilder;
import io.fabric8.kubernetes.api.model.PodDNSConfigOption;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTerm;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTermBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpecBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigNodeStartUpReconciler extends StartUpReconciler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigNodeStartUpReconciler.class);
  private final ConfigNodeConfig configNodeConfig = ConfigNodeConfig.getInstance();

  public ConfigNodeStartUpReconciler(BaseEvent event) {
    super(
        ((ConfigNodeEvent) event).getResource().getSpec(),
        ((ConfigNodeEvent) event).getResource().getMetadata(),
        event.getKind(),
        event.getEventId());
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
    LOGGER.info("iotdb-confignode.properties : \n {}", configNodeProperties);
    configFiles.put(CommonConstant.CONFIG_NODE_PROPERTY_FILE_NAME, configNodeProperties);

    // read init script into ConfigMap
    String scriptContent =
        IOUtils.toString(
            getClass()
                .getResourceAsStream(
                    File.separator + CommonConstant.CONFIG_NODE_INIT_SCRIPT_FILE_NAME),
            Charset.defaultCharset());
    LOGGER.info("confignode-init.sh : \n {}", scriptContent);
    configFiles.put(CommonConstant.CONFIG_NODE_INIT_SCRIPT_FILE_NAME, scriptContent);
    return configFiles;
  }

  @Override
  protected Map<String, String> getLabels() {
    Map<String, String> labels = configNodeConfig.getAdditionalLabels();
    labels.put(CommonConstant.LABEL_KEY_APP_NAME, subResourceName);
    return labels;
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
  protected StatefulSet createStatefulSet(ConfigMap configMap) {

    // metadata
    ObjectMeta metadata = createMetadata();

    StatefulSetSpec statefulSetSpec = createStatefulsetSpec();

    // Here we attach an annotation to statefulset's podTemplate to let it triggers a rolling update
    // for the pods when there is only data changed in ConfigMap.
    String cmSha = DigestUtil.sha(configMap.toString());
    statefulSetSpec
        .getTemplate()
        .getMetadata()
        .getAnnotations()
        .put(CommonConstant.ANNOTATION_KEY_SHA, cmSha);

    StatefulSet statefulSet =
        new StatefulSetBuilder().withMetadata(metadata).withSpec(statefulSetSpec).build();

    return kubernetesClient
        .apps()
        .statefulSets()
        .inNamespace(metadata.getNamespace())
        .resource(statefulSet)
        .create();
  }

  private StatefulSetSpec createStatefulsetSpec() {

    PodTemplateSpec podTemplate = createPodTemplate();

    PersistentVolumeClaim persistentVolumeClaim = createPersistentVolumeClaimTemplate();

    LabelSelector selector = new LabelSelectorBuilder().withMatchLabels(getLabels()).build();

    StatefulSetSpec statefulSetSpec =
        new StatefulSetSpecBuilder()
            .withSelector(selector)
            .withServiceName(subResourceName)
            .withTemplate(podTemplate)
            .withReplicas(commonSpec.getReplicas())
            .withVolumeClaimTemplates(persistentVolumeClaim)
            .build();

    return statefulSetSpec;
  }

  // pvc in sts
  private PersistentVolumeClaim createPersistentVolumeClaimTemplate() {
    Map<String, Quantity> resources = new HashMap<>(1);

    // here we need to set format to null, or it will produce abnormal errors. I am not sure if it
    // is kubernetes's bug.
    Quantity quantity =
        new Quantity(
            commonSpec.getStorage().getLimit() + CommonConstant.RESOURCE_STORAGE_UNIT_G, null);

    resources.put(CommonConstant.RESOURCE_STORAGE, quantity);
    PersistentVolumeClaim claim =
        new PersistentVolumeClaimBuilder()
            .withNewMetadata()
            .withName(subResourceName + CommonConstant.VOLUME_SUFFIX_DATA)
            .endMetadata()
            .withNewSpec()
            .withAccessModes(configNodeConfig.getPvcAccessMode())
            .withStorageClassName(commonSpec.getStorage().getStorageClass())
            .withNewResources()
            .withLimits(resources)
            .withRequests(resources)
            .endResources()
            .endSpec()
            .build();
    return claim;
  }

  private PodTemplateSpec createPodTemplate() {
    // affinity
    Affinity affinity = createAffinity();

    // container
    Container container = createConfigNodeContainer();

    Volume volume =
        new VolumeBuilder()
            .withName(subResourceName + CommonConstant.VOLUME_SUFFIX_CONFIG)
            .withConfigMap(new ConfigMapVolumeSourceBuilder().withName(subResourceName).build())
            .build();

    PodDNSConfig dnsConfig =
        new PodDNSConfigBuilder().withOptions(new PodDNSConfigOption("ndots", "3")).build();

    PodSpec podSpec =
        new PodSpecBuilder()
            .withAffinity(affinity)
            .withTerminationGracePeriodSeconds(20L)
            .withContainers(Collections.singletonList(container))
            .withVolumes(volume)
            .withDnsConfig(dnsConfig)
            .build();

    String imagePullSecret = commonSpec.getImagePullSecret();
    if (imagePullSecret != null && !imagePullSecret.isEmpty()) {
      podSpec.setImagePullSecrets(
          Collections.singletonList(
              new LocalObjectReferenceBuilder().withName(imagePullSecret).build()));
    }

    PodTemplateSpec podTemplateSpec =
        new PodTemplateSpecBuilder()
            .withNewMetadata()
            .withLabels(getLabels())
            .endMetadata()
            .withSpec(podSpec)
            .build();

    return podTemplateSpec;
  }

  private Container createConfigNodeContainer() {

    Probe startupProbe = createStartupProbe();
    Probe readinessProbe = createReadinessProbe();
    Probe livenessProbe = createLivenessProbe();

    ResourceRequirements resourceRequirements =
        ReconcileUtils.createResourceLimits(commonSpec.getLimits());

    List<ContainerPort> containerPorts = createConfigNodeContainerPort();

    List<VolumeMount> volumeMounts = createVolumeMounts(subResourceName);

    List<EnvVar> envs = computeJVMMemory();

    Container container =
        new ContainerBuilder()
            .withName(subResourceName)
            .withNewSecurityContext()
            .withPrivileged(true)
            .withRunAsUser(0L)
            .endSecurityContext()
            .withImage(commonSpec.getImage())
            .withImagePullPolicy(CommonConstant.IMAGE_PULL_POLICY_IF_NOT_PRESENT)
            .withResources(resourceRequirements)
            .withStartupProbe(startupProbe)
            .withReadinessProbe(readinessProbe)
            .withLivenessProbe(livenessProbe)
            .withEnv(envs)
            .withCommand(configNodeConfig.getStartCommand())
            .withArgs(configNodeConfig.getStartArgs())
            .withPorts(containerPorts)
            .withVolumeMounts(volumeMounts)
            .build();
    return container;
  }

  private List<VolumeMount> createVolumeMounts(String name) {
    VolumeMount dataVolumeMount =
        new VolumeMountBuilder()
            .withName(name + CommonConstant.VOLUME_SUFFIX_DATA)
            .withMountPath(configNodeConfig.getDataDir())
            .withSubPath(configNodeConfig.getDataSubPath())
            .build();

    VolumeMount logVolumeMount =
        new VolumeMountBuilder()
            .withName(name + CommonConstant.VOLUME_SUFFIX_DATA)
            .withMountPath(configNodeConfig.getLogDir())
            .withSubPath(configNodeConfig.getLogSubPath())
            .build();

    VolumeMount configMapVolumeMount =
        new VolumeMountBuilder()
            .withName(name + CommonConstant.VOLUME_SUFFIX_CONFIG)
            .withMountPath(configNodeConfig.getConfigMapDir())
            .build();

    return Arrays.asList(dataVolumeMount, logVolumeMount, configMapVolumeMount);
  }

  private List<ContainerPort> createConfigNodeContainerPort() {
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

  private Probe createStartupProbe() {
    return new ProbeBuilder()
        .withNewTcpSocket()
        .withPort(new IntOrString(configNodeConfig.getInternalPort()))
        .endTcpSocket()
        .withPeriodSeconds(3)
        .withFailureThreshold(60)
        .build();
  }

  private Probe createReadinessProbe() {
    return new ProbeBuilder()
        .withNewTcpSocket()
        .withPort(new IntOrString(configNodeConfig.getInternalPort()))
        .endTcpSocket()
        .withPeriodSeconds(3)
        .withFailureThreshold(3)
        .build();
  }

  private Probe createLivenessProbe() {
    return new ProbeBuilder()
        .withNewTcpSocket()
        .withPort(new IntOrString(configNodeConfig.getInternalPort()))
        .endTcpSocket()
        .withPeriodSeconds(3)
        .withFailureThreshold(10)
        .build();
  }

  private ObjectMeta createMetadata() {
    return new ObjectMetaBuilder()
        .withNamespace(metadata.getNamespace())
        .withName(subResourceName)
        .withLabels(getLabels())
        .withDeletionGracePeriodSeconds(10L)
        .build();
  }

  private Affinity createAffinity() {
    PodAntiAffinity podAntiAffinity = new PodAntiAffinity();
    if (commonSpec
        .getPodDistributeStrategy()
        .equals(CommonConstant.POD_AFFINITY_POLICY_PREFERRED)) {
      WeightedPodAffinityTerm weightedPodAffinityTerm =
          new WeightedPodAffinityTermBuilder()
              .withNewPodAffinityTerm()
              .withNewLabelSelector()
              .withMatchLabels(getLabels())
              .endLabelSelector()
              .withNamespaces(metadata.getNamespace())
              .withTopologyKey("kubernetes.io/hostname")
              .endPodAffinityTerm()
              .withWeight(100)
              .build();
      podAntiAffinity.setPreferredDuringSchedulingIgnoredDuringExecution(
          Collections.singletonList(weightedPodAffinityTerm));
      LOGGER.info("use preferred pod distribution strategy");
    } else {
      PodAffinityTerm podAffinityTerm =
          new PodAffinityTermBuilder()
              .withNewLabelSelector()
              .withMatchLabels(getLabels())
              .endLabelSelector()
              .withNamespaces(metadata.getNamespace())
              .withTopologyKey("kubernetes.io/hostname")
              .build();
      podAntiAffinity.setRequiredDuringSchedulingIgnoredDuringExecution(
          Collections.singletonList(podAffinityTerm));
      LOGGER.info("use required pod distribution strategy");
    }
    return new AffinityBuilder().withPodAntiAffinity(podAntiAffinity).build();
  }

  @Override
  protected void createServices() {
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
            .withPorts(consensusServicePort)
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
            .withPorts(rpcServicePort, metricServicePort)
            .endSpec()
            .build();

    kubernetesClient
        .services()
        .inNamespace(metadata.getNamespace())
        .resource(internalService)
        .create();
    kubernetesClient
        .services()
        .inNamespace(metadata.getNamespace())
        .resource(externalService)
        .create();
  }

  @Override
  public ReconcilerType getType() {
    return ReconcilerType.CONFIG_NODE_STARTUP;
  }
}
