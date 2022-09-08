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

import org.apache.iotdb.operator.controller.reconciler.DeleteReconciler;
import org.apache.iotdb.operator.crd.ConfigNode;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.event.CustomResourceEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigNodeDeleteReconciler extends DeleteReconciler {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigNodeDeleteReconciler.class);

  public ConfigNodeDeleteReconciler(CustomResourceEvent event) {
    super(event.getResource().getSpec(), event.getResource().getMetadata(), Kind.CONFIG_NODE);
  }

  @Override
  public ReconcilerType getType() {
    return ReconcilerType.CONFIG_NODE_DELETE;
  }

  @Override
  public void deleteCustomResource() {
    kubernetesClient
        .resources(ConfigNode.class)
        .inNamespace(metadata.getNamespace())
        .withName(metadata.getName())
        .delete();
    LOGGER.info("confignode deleted : {}", metadata.getName());
  }
}
