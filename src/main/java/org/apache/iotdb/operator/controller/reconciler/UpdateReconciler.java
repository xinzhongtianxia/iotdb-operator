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
import org.apache.iotdb.operator.crd.Limits;
import org.apache.iotdb.operator.util.DigestUtils;
import org.apache.iotdb.operator.util.ReconcilerUtils;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class UpdateReconciler implements IReconciler {
  private static final Logger LOGGER = LoggerFactory.getLogger(UpdateReconciler.class);

  private final ObjectMeta meta;
  protected final String subResourceName;
  protected final CommonSpec newSpec;

  public UpdateReconciler(ObjectMeta meta, Kind kind, CommonSpec newSpec) {
    this.meta = meta;
    this.newSpec = newSpec;
    this.subResourceName = meta.getName().toLowerCase() + "-" + kind.getName().toLowerCase();
  }

  @Override
  public void reconcile() throws IOException {
    ConfigMap configMap = updateConfigMap();
    updateStatefulSet(configMap);
  }

  protected ConfigMap updateConfigMap() throws IOException {

    ConfigMap configMap =
        kubernetesClient
            .configMaps()
            .inNamespace(meta.getNamespace())
            .withName(subResourceName)
            .require();

    if (needUpdateConfigMap(configMap)) {
      LOGGER.info("begin updating configmap : {}:{}", meta.getNamespace(), subResourceName);
      internalUpdateConfigMap(configMap);
      kubernetesClient
          .configMaps()
          .inNamespace(meta.getNamespace())
          .resource(configMap)
          .lockResourceVersion(configMap.getMetadata().getResourceVersion())
          .replace();

      LOGGER.info("end updating configmap : {}:{}", meta.getNamespace(), subResourceName);
    } else {
      LOGGER.info(
          "configmap not changed, no need to update ConfigMap {}:{}",
          meta.getNamespace(),
          subResourceName);
    }
    return configMap;
  }

  protected void updateStatefulSet(ConfigMap configMap) {
    // todo consider rolling-update with pause and resume

    StatefulSet statefulSet =
        kubernetesClient
            .apps()
            .statefulSets()
            .inNamespace(meta.getNamespace())
            .withName(subResourceName)
            .require();

    if (needUpdateStatefulSet(configMap, statefulSet)) {
      LOGGER.info("begin updating statefulset : {}:{}", meta.getNamespace(), subResourceName);

      internalUpdateStatefulSet(configMap, statefulSet);
      kubernetesClient
          .apps()
          .statefulSets()
          .inNamespace(meta.getNamespace())
          .resource(statefulSet)
          .lockResourceVersion(statefulSet.getMetadata().getResourceVersion())
          .replace();

      LOGGER.info("end updating statefulset : {}:{}", meta.getNamespace(), subResourceName);
    } else {
      LOGGER.info("no need to update statefulset {}:{}", meta.getNamespace(), subResourceName);
      // since we actually received modification event but there is no need to do any updating work,
      // then
      // the event must be a status-changed event or synthetic event.
      LOGGER.info("old status : {} \n new Status : {}", getOldStatus(), getNewStatus());
    }
  }

  protected abstract Object getNewStatus();

  protected abstract Object getOldStatus();

  protected void internalUpdateStatefulSet(ConfigMap configMap, StatefulSet statefulSet) {
    int replicas = newSpec.getReplicas();
    String image = newSpec.getImage();
    String imageSecret = newSpec.getImagePullSecret();

    // update replicas
    statefulSet.getSpec().setReplicas(replicas);

    // update image
    statefulSet.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);

    // update image pull secrets
    statefulSet
        .getSpec()
        .getTemplate()
        .getSpec()
        .setImagePullSecrets(Collections.singletonList(new LocalObjectReference(imageSecret)));

    // update limits
    Limits limits = newSpec.getLimits();
    ResourceRequirements resourceRequirements = ReconcilerUtils.createResourceLimits(limits);
    statefulSet
        .getSpec()
        .getTemplate()
        .getSpec()
        .getContainers()
        // there is only one container in the pod
        .get(0)
        .setResources(resourceRequirements);

    // update env
    List<EnvVar> envs = ReconcilerUtils.computeJVMMemory(limits);
    statefulSet.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(envs);

    // update configMapSha256
    String cmSha = DigestUtils.sha(configMap.getData().toString());
    statefulSet
        .getSpec()
        .getTemplate()
        .getMetadata()
        .getAnnotations()
        .put(CommonConstant.ANNOTATION_KEY_SHA, cmSha);
  }

  protected abstract void internalUpdateConfigMap(ConfigMap configMap) throws IOException;

  /**
   * We should always compare the specification of new-resource in event with the resource in
   * Kubernetes. Do not use the specification of old-resource in event, it does not always represent
   * the real state of currently running confignode (or datanode). For example, if we received an
   * modification event but failed to update the statefulset or configmap, then the modification
   * event reached again, for supplementation, we will get a specification in the event equals to
   * the current running confignode (or datanode), though the current statefulset's stated is not
   * what we desired.
   */
  protected boolean needUpdateStatefulSet(ConfigMap configMap, StatefulSet statefulSet) {
    boolean needUpdate = false;

    StatefulSetSpec statefulSetSpec = statefulSet.getSpec();
    String oldImage = statefulSetSpec.getTemplate().getSpec().getContainers().get(0).getImage();
    if (!newSpec.getImage().equals(oldImage)) {
      needUpdate = true;
      LOGGER.info("image changed, old : {}, new : {}", oldImage, newSpec.getImage());
    }

    Map<String, Quantity> oldLimits =
        statefulSet
            .getSpec()
            .getTemplate()
            .getSpec()
            .getContainers()
            .get(0)
            .getResources()
            .getLimits();
    Limits newLimits = newSpec.getLimits();
    Quantity newCpuQuantity = new Quantity(String.valueOf(newLimits.getCpu()));
    Quantity newMemQuantity =
        new Quantity(
            String.valueOf(newLimits.getMemory()) + CommonConstant.RESOURCE_STORAGE_UNIT_M);
    if (!oldLimits.get(CommonConstant.RESOURCE_CPU).equals(newCpuQuantity)
        || !oldLimits.get(CommonConstant.RESOURCE_MEMORY).equals(newMemQuantity)) {
      needUpdate = true;
      LOGGER.info("limits changed, old : {}, new : {}", oldLimits, newSpec.getLimits());
    }

    if (newSpec.getReplicas() != statefulSetSpec.getReplicas()) {
      needUpdate = true;
      LOGGER.info(
          "replica changed, old : {}, new : {}",
          statefulSetSpec.getReplicas(),
          newSpec.getReplicas());
    }

    String newCmSha = DigestUtils.sha(configMap.getData().toString());
    String oldCmSha =
        statefulSet
            .getSpec()
            .getTemplate()
            .getMetadata()
            .getAnnotations()
            .get(CommonConstant.ANNOTATION_KEY_SHA);
    if (!newCmSha.equals(oldCmSha)) {
      LOGGER.info("configmap updated, so we also need to update the statefulset");
      needUpdate = true;
    }

    // todo storage vertical scale

    return needUpdate;
  }

  /**
   * We should always compare the specification of new-resource in event with the resource in *
   * Kubernetes Api Server. Do not use the specification of old-resource in event, it does not
   * always represent the real state of currently running confignode (or datanode).
   *
   * @see #needUpdateStatefulSet
   */
  protected abstract boolean needUpdateConfigMap(ConfigMap configMap) throws IOException;
}
