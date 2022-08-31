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

import org.apache.iotdb.operator.common.CommonConstant;
import org.apache.iotdb.operator.crd.CommonSpec;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.util.DigestUtils;
import org.apache.iotdb.operator.util.OutputEventUtils;
import org.apache.iotdb.operator.util.ReconcilerUtils;

import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.AffinityBuilder;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVar;
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
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTerm;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTermBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpec;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpecBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class StartUpReconciler implements IReconciler {

  private static final Logger LOGGER = LoggerFactory.getLogger(StartUpReconciler.class);

  protected final CommonSpec commonSpec;
  protected final ObjectMeta metadata;
  protected final String subResourceName;
  protected final String eventId;
  protected final Kind kind;

  public StartUpReconciler(CommonSpec commonSpec, ObjectMeta metadata, Kind kind, String eventId) {
    this.kind = kind;
    this.commonSpec = commonSpec;
    this.metadata = metadata;
    subResourceName = metadata.getName().toLowerCase() + "-" + kind.getName().toLowerCase();
    this.eventId = eventId;
  }

  @Override
  public void reconcile() throws IOException {
    LOGGER.info("{} : checkInitConditions", eventId);
    if (!checkInitConditions()) {
      return;
    }

    LOGGER.info("{} : createConfigMap", eventId);
    ConfigMap configMap = createConfigMap();

    LOGGER.info("{} : createServices", eventId);
    createServices();

    LOGGER.info("{} : createStatefulSet", eventId);
    createStatefulSet(configMap);
  }

  protected abstract boolean checkInitConditions();

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

    OutputEventUtils.sendEvent(
        kind,
        OutputEventUtils.EVENT_TYPE_NORMAL,
        "CreateConfigMap",
        metadata,
        "Successfully created ConfigMap " + subResourceName,
        "Created",
        Kind.CONFIG_MAP.getName());

    return kubernetesClient
        .configMaps()
        .inNamespace(metadata.getNamespace())
        .resource(configMap)
        .createOrReplace();
  }

  /** Common labels that need to be attached to iotdb resources. */
  protected abstract Map<String, String> getLabels();

  protected void createStatefulSet(ConfigMap configMap) {
    // metadata
    ObjectMeta statefulSetMetadata = createMetadata();

    // specific
    StatefulSetSpec statefulSetSpec = createStatefulsetSpec();

    // Here we attach an annotation to statefulset's podTemplate to let it triggers a rolling update
    // for the pods when there is only data changed in ConfigMap.
    String cmSha = DigestUtils.sha(configMap.getData().toString());
    statefulSetSpec
        .getTemplate()
        .getMetadata()
        .getAnnotations()
        .put(CommonConstant.ANNOTATION_KEY_SHA, cmSha);

    StatefulSet statefulSet =
        new StatefulSetBuilder()
            .withMetadata(statefulSetMetadata)
            .withSpec(statefulSetSpec)
            .build();

    OutputEventUtils.sendEvent(
        kind,
        OutputEventUtils.EVENT_TYPE_NORMAL,
        "CreateStatefulSet",
        metadata,
        "Successfully created CreateStatefulSet " + subResourceName,
        "Created",
        Kind.STATEFUL_SET.getName());

    kubernetesClient
        .apps()
        .statefulSets()
        .inNamespace(metadata.getNamespace())
        .resource(statefulSet)
        .createOrReplace();
  }

  private StatefulSetSpec createStatefulsetSpec() {

    // pod
    PodTemplateSpec podTemplate = createPodTemplate();

    // pvc
    PersistentVolumeClaim persistentVolumeClaim = createPersistentVolumeClaimTemplate();

    // label
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
            .withAccessModes("ReadWriteOncePod")
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
    Container container = createContainer();

    // volume
    Volume volume =
        new VolumeBuilder()
            .withName(subResourceName + CommonConstant.VOLUME_SUFFIX_CONFIG)
            .withConfigMap(new ConfigMapVolumeSourceBuilder().withName(subResourceName).build())
            .build();

    // dns config
    PodDNSConfig dnsConfig =
        new PodDNSConfigBuilder().withOptions(new PodDNSConfigOption("ndots", "3")).build();

    // pod
    PodSpec podSpec =
        new PodSpecBuilder()
            .withAffinity(affinity)
            .withTerminationGracePeriodSeconds(20L)
            .withContainers(Collections.singletonList(container))
            .withVolumes(volume)
            .withDnsConfig(dnsConfig)
            .build();

    // image pull secret
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

  private Container createContainer() {

    Probe startupProbe = createStartupProbe();
    Probe readinessProbe = createReadinessProbe();
    Probe livenessProbe = createLivenessProbe();

    ResourceRequirements resourceRequirements =
        ReconcilerUtils.createResourceLimits(commonSpec.getLimits());

    List<ContainerPort> containerPorts = createContainerPort();

    List<VolumeMount> volumeMounts = createVolumeMounts(subResourceName);

    List<EnvVar> envs = getEnvs();

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
            .withCommand("/bin/bash", "-c")
            .withArgs(getStartArgs())
            .withPorts(containerPorts)
            .withVolumeMounts(volumeMounts)
            .build();
    return container;
  }

  protected abstract List<EnvVar> getEnvs();

  protected abstract String getStartArgs();

  private List<VolumeMount> createVolumeMounts(String name) {
    VolumeMount dataVolumeMount =
        new VolumeMountBuilder()
            .withName(name + CommonConstant.VOLUME_SUFFIX_DATA)
            .withMountPath("/iotdb/" + kind.getName().toLowerCase() + "/data")
            .withSubPath("data")
            .build();

    VolumeMount logVolumeMount =
        new VolumeMountBuilder()
            .withName(name + CommonConstant.VOLUME_SUFFIX_DATA)
            .withMountPath("/iotdb/" + kind.getName().toLowerCase() + "/logs")
            .withSubPath("logs")
            .build();

    VolumeMount configMapVolumeMount =
        new VolumeMountBuilder()
            .withName(name + CommonConstant.VOLUME_SUFFIX_CONFIG)
            .withMountPath("/tmp/conf")
            .build();

    return Arrays.asList(dataVolumeMount, logVolumeMount, configMapVolumeMount);
  }

  protected abstract List<ContainerPort> createContainerPort();

  protected abstract Probe createStartupProbe();

  protected abstract Probe createReadinessProbe();

  protected abstract Probe createLivenessProbe();

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

  protected abstract void createServices();

  protected void createIngress(String name, String namespace) {}
}
