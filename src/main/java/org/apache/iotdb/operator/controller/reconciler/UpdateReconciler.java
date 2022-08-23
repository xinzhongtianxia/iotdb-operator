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
import org.apache.iotdb.operator.common.EnvKey;
import org.apache.iotdb.operator.crd.CommonSpec;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.crd.Limits;
import org.apache.iotdb.operator.util.DigestUtils;
import org.apache.iotdb.operator.util.OutputEventUtils;
import org.apache.iotdb.operator.util.ReconcilerUtils;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public abstract class UpdateReconciler implements IReconciler {
  private static final Logger LOGGER = LoggerFactory.getLogger(UpdateReconciler.class);

  protected final ObjectMeta meta;
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
      OutputEventUtils.sendEvent(
          Kind.DATA_NODE,
          OutputEventUtils.EVENT_TYPE_NORMAL,
          "Update ConfigMap",
          meta,
          "Successfully updated ConfigMap",
          "Updated",
          Kind.CONFIG_MAP.getName());
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
    StatefulSet statefulSet =
        kubernetesClient
            .apps()
            .statefulSets()
            .inNamespace(meta.getNamespace())
            .withName(subResourceName)
            .require();

    if (needUpdateStatefulSet(configMap, statefulSet)) {
      LOGGER.info("begin updating statefulset : {}:{}", meta.getNamespace(), subResourceName);

      if (newSpec.isEnableSafeDeploy()) {
        // set rolling-update-partition to replica-1 for safety
        int rollingUpdatePartition = newSpec.getReplicas() - 1;
        LOGGER.info("set rolling-update-partition to {}", rollingUpdatePartition);
        patchPartitionToAnnotations(rollingUpdatePartition);
        statefulSet
            .getSpec()
            .getUpdateStrategy()
            .getRollingUpdate()
            .setPartition(rollingUpdatePartition);
      }

      kubernetesClient
          .apps()
          .statefulSets()
          .inNamespace(meta.getNamespace())
          .resource(statefulSet)
          .replace();

      OutputEventUtils.sendEvent(
          Kind.DATA_NODE,
          OutputEventUtils.EVENT_TYPE_NORMAL,
          "Update StatefulSet",
          meta,
          "Successfully updated StatefulSet",
          "Updated",
          Kind.STATEFUL_SET.getName());

      LOGGER.info("end updating statefulset : {}:{}", meta.getNamespace(), subResourceName);
    } else if (needPatchPartition(statefulSet)) {
      kubernetesClient
          .apps()
          .statefulSets()
          .inNamespace(meta.getNamespace())
          .resource(statefulSet)
          .replace();
      OutputEventUtils.sendEvent(
          Kind.DATA_NODE,
          OutputEventUtils.EVENT_TYPE_NORMAL,
          "Update StatefulSet",
          meta,
          "Successfully updated StatefulSet's partition",
          "Updated",
          Kind.STATEFUL_SET.getName());
    } else {
      LOGGER.warn("no need to update statefulset newSpec = {}", newSpec);
    }
  }

  private boolean needPatchPartition(StatefulSet statefulSet) {
    String newPartition =
        meta.getAnnotations().getOrDefault(CommonConstant.ANNOTATION_KEY_PARTITION, "0");
    int currentPartition =
        statefulSet.getSpec().getUpdateStrategy().getRollingUpdate().getPartition();
    if (currentPartition != Integer.parseInt(newPartition)) {
      LOGGER.info("rolling update partition changed from {} to {}", currentPartition, newPartition);
      statefulSet
          .getSpec()
          .getUpdateStrategy()
          .getRollingUpdate()
          .setPartition(Integer.valueOf(newPartition));
      return true;
    }
    return false;
  }

  private void updateEnvs(StatefulSet statefulSet, Limits limits) {
    List<EnvVar> envVars =
        statefulSet.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
    envVars.removeIf(
        envVar ->
            envVar.getName().equals(EnvKey.IOTDB_MAX_DIRECT_MEMORY_SIZE.name())
                || envVar.getName().equals(EnvKey.IOTDB_MAX_HEAP_MEMORY_SIZE.name()));
    envVars.addAll(ReconcilerUtils.computeJVMMemory(limits));
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

    // images
    String oldImage = statefulSetSpec.getTemplate().getSpec().getContainers().get(0).getImage();
    if (!newSpec.getImage().equals(oldImage)) {
      statefulSetSpec.getTemplate().getSpec().getContainers().get(0).setImage(newSpec.getImage());
      needUpdate = true;
      LOGGER.info("image changed, old : {}, new : {}", oldImage, newSpec.getImage());
    }

    // replicas
    // since replicas has been validated when the event came in, here we could treat it as a
    // legal param.
    if (newSpec.getReplicas() != statefulSetSpec.getReplicas()) {
      statefulSetSpec.setReplicas(newSpec.getReplicas());
      needUpdate = true;
      LOGGER.info(
          "replicas changed, old : {}, new : {}",
          newSpec.getReplicas(),
          statefulSetSpec.getReplicas());
    }

    // resource limits
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
      statefulSet
          .getSpec()
          .getTemplate()
          .getSpec()
          .getContainers()
          .get(0)
          .setResources(ReconcilerUtils.createResourceLimits(newLimits));

      updateEnvs(statefulSet, newLimits);

      needUpdate = true;
      LOGGER.info("limits changed, old : {}, new : {}", oldLimits, newSpec.getLimits());
    }

    // configmap sha
    String newCmSha = DigestUtils.sha(configMap.getData().toString());
    String oldCmSha =
        statefulSet
            .getSpec()
            .getTemplate()
            .getMetadata()
            .getAnnotations()
            .get(CommonConstant.ANNOTATION_KEY_SHA);
    if (!newCmSha.equals(oldCmSha)) {
      statefulSet
          .getSpec()
          .getTemplate()
          .getMetadata()
          .getAnnotations()
          .put(CommonConstant.ANNOTATION_KEY_SHA, newCmSha);
      LOGGER.info("configmap updated, so we also need to update the statefulset");
      needUpdate = true;
    }

    // todo storage vertical scale

    // if statefulset need to be update, do not forget to update the image pull secret.
    if (needUpdate) {
      int oldImagePullSecretSize =
          statefulSetSpec.getTemplate().getSpec().getImagePullSecrets().size();
      if (oldImagePullSecretSize > 0
          && !statefulSetSpec
              .getTemplate()
              .getSpec()
              .getImagePullSecrets()
              .get(0)
              .getName()
              .equals(newSpec.getImagePullSecret())) {
        statefulSetSpec
            .getTemplate()
            .getSpec()
            .getImagePullSecrets()
            .get(0)
            .setName(newSpec.getImagePullSecret());
      }
    }

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

  /**
   * patch rolling update partition to annotations
   *
   * @param rollingUpdatePartition
   */
  protected abstract void patchPartitionToAnnotations(int rollingUpdatePartition);
}
