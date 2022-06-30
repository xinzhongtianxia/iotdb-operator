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

import org.apache.iotdb.operator.crd.CommonSpec;
import org.apache.iotdb.operator.crd.CommonStatus;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.crd.Limits;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class UpdateReconciler implements IReconciler {
  private static final Logger LOGGER = LoggerFactory.getLogger(UpdateReconciler.class);

  private final ObjectMeta meta;
  protected final String subResourceName;

  public UpdateReconciler(ObjectMeta meta, Kind kind) {
    this.meta = meta;
    subResourceName = meta.getName().toLowerCase() + "-" + kind.getName().toLowerCase();
  }

  @Override
  public void reconcile() throws IOException {
    boolean isConfigMapUpdated = updateConfigMap();
    updateStatefulSet(isConfigMapUpdated);
  }

  protected boolean updateConfigMap() throws IOException {

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
      return true;
    }
    LOGGER.info(
        "configmap not changed, no need to update ConfigMap {}:{}",
        meta.getNamespace(),
        subResourceName);
    return false;
  }

  protected void updateStatefulSet(boolean isConfigMapUpdated) {
    // todo consider rolling-update with pause and resume

    StatefulSet statefulSet =
        kubernetesClient
            .apps()
            .statefulSets()
            .inNamespace(meta.getNamespace())
            .withName(subResourceName)
            .require();

    if (needUpdateStatefulSet(isConfigMapUpdated, statefulSet)) {
      LOGGER.info("begin updating statefulset : {}:{}", meta.getNamespace(), subResourceName);

      internalUpdateStatefulSet(statefulSet);
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

  protected abstract void internalUpdateStatefulSet(StatefulSet statefulSet);

  protected abstract void internalUpdateConfigMap(ConfigMap configMap) throws IOException;

  /**
   * We should always compare the specification of new-resource in event with the resource in
   * Kubernetes. Do not use the specification of old-resource in event, it does not always represent
   * the real state of currently running confignode (or datanode). For example, if we received an
   * modification event but failed to update the statefulset or configmap, then the modification
   * event reached again, for supplementation, we will get a specification int the event equals to
   * the current running confignode (or datanode), though the current statefulset's stated is not
   * what we desired.
   */
  protected abstract boolean needUpdateStatefulSet(
      boolean isConfigMapUpdated, StatefulSet statefulSet);

  /**
   * We should always compare the specification of new-resource in event with the resource in *
   * Kubernetes. Do not use the specification of old-resource in event, it does not always represent
   * the real state of currently running confignode (or datanode).
   * @see #needUpdateStatefulSet
   */
  protected abstract boolean needUpdateConfigMap(ConfigMap configMap) throws IOException;
}
