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
import org.apache.iotdb.operator.controller.reconciler.UpdateReconciler;
import org.apache.iotdb.operator.crd.CommonStatus;
import org.apache.iotdb.operator.crd.ConfigNodeSpec;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.crd.Limits;
import org.apache.iotdb.operator.event.BaseEvent;
import org.apache.iotdb.operator.event.ConfigNodeEvent;
import org.apache.iotdb.operator.util.DigestUtil;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

public class ConfigNodeUpdateReconciler extends UpdateReconciler {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigNodeUpdateReconciler.class);

  private ConfigNodeConfig configNodeConfig = ConfigNodeConfig.getInstance();

  private final ConfigNodeSpec newSpec;

  private final CommonStatus oldStatus;
  private final CommonStatus newStatus;

  public ConfigNodeUpdateReconciler(BaseEvent event) {
    super(((ConfigNodeEvent) event).getResource().getMetadata(), Kind.CONFIG_NODE);
    newSpec = ((ConfigNodeEvent) event).getResource().getSpec();
    newStatus = ((ConfigNodeEvent) event).getResource().getStatus();
    if (((ConfigNodeEvent) event).getOldResource() != null) {
      oldStatus = ((ConfigNodeEvent) event).getOldResource().getStatus();
    } else {
      oldStatus = new CommonStatus();
    }
  }

  @Override
  public ReconcilerType getType() {
    return ReconcilerType.CONFIG_NODE_UPDATE;
  }

  @Override
  protected Object getNewStatus() {
    return newStatus;
  }

  @Override
  protected Object getOldStatus() {
    return oldStatus;
  }

  @Override
  protected void internalUpdateStatefulSet(ConfigMap configMap, StatefulSet statefulSet) {
    int replicas = newSpec.getReplicas();
    String image = newSpec.getImage();
    String imageSecret = newSpec.getImagePullSecret();
    statefulSet.getSpec().setReplicas(replicas);
    statefulSet.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);
    statefulSet
        .getSpec()
        .getTemplate()
        .getSpec()
        .setImagePullSecrets(Collections.singletonList(new LocalObjectReference(imageSecret)));

    Limits limits = newSpec.getLimits();
    ResourceRequirements resourceRequirements = ReconcileUtils.createResourceLimits(limits);
    statefulSet
        .getSpec()
        .getTemplate()
        .getSpec()
        .getContainers()
        // there is only one container in the pod
        .get(0)
        .setResources(resourceRequirements);

    // update configMapSha256
    String cmSha = DigestUtil.sha(configMap.getData().toString());
    statefulSet
        .getSpec()
        .getTemplate()
        .getMetadata()
        .getAnnotations()
        .put(CommonConstant.ANNOTATION_KEY_SHA, cmSha);
  }

  @Override
  protected boolean needUpdateConfigMap(ConfigMap configMap) throws IOException {
    String configNodePropertyFileContent =
        configMap.getData().get(CommonConstant.CONFIG_NODE_PROPERTY_FILE_NAME);
    LOGGER.info("old confignode-properties: \n {}", configNodePropertyFileContent);

    Properties properties = new Properties();
    properties.load(new StringReader(configNodePropertyFileContent));

    boolean needUpdate = false;
    Map<String, Object> newProperties = newSpec.getIotdbConfig().getConfigNodeProperties();

    if (newProperties.size()
        != properties.size() - configNodeConfig.getDefaultProperties().size()) {
      needUpdate = true;
    } else {
      for (Entry<Object, Object> entry : properties.entrySet()) {
        Object k = entry.getKey();
        Object v = entry.getValue();
        String key = (String) k;
        if (configNodeConfig.getDefaultProperties().contains(key)) {
          continue;
        }
        if (!v.equals(newProperties.get(key))) {
          needUpdate = true;
          break;
        }
      }
    }

    if (needUpdate) {
      LOGGER.info(
          "configmap changed, old: {} \n new (without default properties) : {}",
          properties,
          newProperties);
    }

    return needUpdate;
  }

  @Override
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

    String newCmSha = DigestUtil.sha(configMap.getData().toString());
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

  @Override
  protected void internalUpdateConfigMap(ConfigMap configMap) throws IOException {
    String configNodePropertyFileContent =
        configMap.getData().get(CommonConstant.CONFIG_NODE_PROPERTY_FILE_NAME);

    Properties properties = new Properties();
    properties.load(new StringReader(configNodePropertyFileContent));

    Map<String, Object> newProperties = newSpec.getIotdbConfig().getConfigNodeProperties();
    newProperties.forEach(
        (k, v) -> {
          if (properties.containsKey(k)) {
            properties.replace(k, v);
          } else {
            properties.setProperty(k, (String) v);
          }
        });

    Iterator<Entry<Object, Object>> it = properties.entrySet().iterator();
    while (it.hasNext()) {
      Entry<Object, Object> entry = it.next();
      String key = (String) entry.getKey();
      if (configNodeConfig.getDefaultProperties().contains(key)) {
        continue;
      }
      if (!newProperties.containsKey(key)) {
        it.remove();
      }
    }
    StringWriter stringWriter = new StringWriter();
    properties.store(stringWriter, CommonConstant.GENERATE_BY_OPERATOR);
    String newConfigNodePropertyFileContent = stringWriter.toString();
    LOGGER.info("new confignode-properties: \n {}", newConfigNodePropertyFileContent);
    configMap
        .getData()
        .put(CommonConstant.CONFIG_NODE_PROPERTY_FILE_NAME, newConfigNodePropertyFileContent);
  }
}
