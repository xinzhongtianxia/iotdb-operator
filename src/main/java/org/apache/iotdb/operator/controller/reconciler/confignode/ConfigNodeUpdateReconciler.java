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
import org.apache.iotdb.operator.controller.reconciler.UpdateReconciler;
import org.apache.iotdb.operator.crd.ConfigNode;
import org.apache.iotdb.operator.crd.ConfigNodeBuilder;
import org.apache.iotdb.operator.crd.ConfigNodeSpec;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.event.CustomResourceEvent;

import io.fabric8.kubernetes.api.model.ConfigMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

public class ConfigNodeUpdateReconciler extends UpdateReconciler {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigNodeUpdateReconciler.class);

  private ConfigNodeConfig configNodeConfig = ConfigNodeConfig.getInstance();

  public ConfigNodeUpdateReconciler(CustomResourceEvent event) {
    super(event.getResource().getMetadata(), Kind.CONFIG_NODE, event.getResource().getSpec());
  }

  @Override
  public ReconcilerType getType() {
    return ReconcilerType.CONFIG_NODE_UPDATE;
  }

  @Override
  protected boolean needUpdateConfigMap(ConfigMap configMap) throws IOException {
    String configNodePropertyFileContent =
        configMap.getData().get(CommonConstant.CONFIG_NODE_PROPERTY_FILE_NAME);
    LOGGER.info("old confignode-properties: \n {}", configNodePropertyFileContent);

    Properties properties = new Properties();
    properties.load(new StringReader(configNodePropertyFileContent));

    boolean needUpdate = false;
    Map<String, Object> newProperties =
        ((ConfigNodeSpec) newSpec).getIotdbConfig().getConfigNodeProperties();

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
  protected void internalUpdateConfigMap(ConfigMap configMap) throws IOException {
    String configNodePropertyFileContent =
        configMap.getData().get(CommonConstant.CONFIG_NODE_PROPERTY_FILE_NAME);

    Properties properties = new Properties();
    properties.load(new StringReader(configNodePropertyFileContent));

    Map<String, Object> newProperties =
        ((ConfigNodeSpec) newSpec).getIotdbConfig().getConfigNodeProperties();
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

  @Override
  protected void patchPartitionToAnnotations() {

    int rollingUpdatePartition = newSpec.getReplicas() - 1;
    ConfigNode configNode =
        kubernetesClient
            .resources(ConfigNode.class)
            .inNamespace(meta.getNamespace())
            .withName(meta.getName())
            .require();
    String currentPartition =
        configNode
            .getMetadata()
            .getAnnotations()
            .getOrDefault(CommonConstant.ANNOTATION_KEY_PARTITION, "0");

    if (Integer.parseInt(currentPartition) != rollingUpdatePartition) {
      configNode
          .getMetadata()
          .getAnnotations()
          .put(CommonConstant.ANNOTATION_KEY_PARTITION, String.valueOf(rollingUpdatePartition));
      ConfigNode configNodeWithOnlyAnnotations =
          new ConfigNodeBuilder().withMetadata(configNode.getMetadata()).build();

      kubernetesClient
          .resource(configNodeWithOnlyAnnotations)
          .inNamespace(meta.getNamespace())
          .patch();
    }
  }
}
