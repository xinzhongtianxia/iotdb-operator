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

import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DeleteReconciler extends AbstractReconciler implements IReconciler {
  private static final Logger LOGGER = LoggerFactory.getLogger(DeleteReconciler.class);

  protected final CommonSpec commonSpec;
  protected final ObjectMeta metadata;
  protected final String subResourceName;

  protected DeleteReconciler(CommonSpec commonSpec, ObjectMeta metadata, Kind kind) {
    this.commonSpec = commonSpec;
    this.metadata = metadata;
    subResourceName = metadata.getName().toLowerCase() + "-" + kind.getName().toLowerCase();
  }

  @Override
  public void reconcile() {
    deleteService();

    deleteConfigMap();

    deleteStatefulset();

    deleteCustomResource();
  }

  public abstract void deleteCustomResource();

  public void deleteStatefulset() {
    kubernetesClient
        .apps()
        .statefulSets()
        .inNamespace(metadata.getNamespace())
        .withName(subResourceName)
        .delete();
    LOGGER.info("statefulset deleted : {}", subResourceName);
  }

  public void deleteConfigMap() {
    kubernetesClient
        .configMaps()
        .inNamespace(metadata.getNamespace())
        .withName(subResourceName)
        .delete();
    LOGGER.info("configmap deleted : {}", subResourceName);
  }

  public void deleteService() {
    // delete externalService
    kubernetesClient
        .services()
        .inNamespace(metadata.getNamespace())
        .withName(subResourceName)
        .delete();
    LOGGER.info("internal-service deleted : {}", subResourceName);

    // delete internalService
    kubernetesClient
        .services()
        .inNamespace(metadata.getNamespace())
        .withName(subResourceName + CommonConstant.SERVICE_SUFFIX_EXTERNAL)
        .delete();
    LOGGER.info(
        "external-service deleted : {}", subResourceName + CommonConstant.SERVICE_SUFFIX_EXTERNAL);
  }
}
