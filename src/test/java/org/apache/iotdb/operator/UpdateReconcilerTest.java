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

package org.apache.iotdb.operator;

import org.apache.iotdb.operator.common.CommonConstant;
import org.apache.iotdb.operator.controller.reconciler.StartUpReconciler;
import org.apache.iotdb.operator.controller.reconciler.UpdateReconciler;
import org.apache.iotdb.operator.controller.reconciler.datanode.DataNodeStartUpReconciler;
import org.apache.iotdb.operator.controller.reconciler.datanode.DataNodeUpdateReconciler;
import org.apache.iotdb.operator.crd.CommonStatus;
import org.apache.iotdb.operator.crd.DataNode;
import org.apache.iotdb.operator.crd.DataNodeBuilder;
import org.apache.iotdb.operator.crd.DataNodeSpec;
import org.apache.iotdb.operator.crd.DataNodeSpecBuilder;
import org.apache.iotdb.operator.crd.IoTDBDataNodeConfig;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.crd.LimitsBuilder;
import org.apache.iotdb.operator.crd.ServiceBuilder;
import org.apache.iotdb.operator.crd.StorageBuilder;
import org.apache.iotdb.operator.event.CustomResourceEvent;
import org.apache.iotdb.operator.util.DigestUtils;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetUpdateStrategyBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher.Action;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class UpdateReconcilerTest extends MockKubernetesClient {
  private UpdateReconciler updateReconciler;

  private StartUpReconciler startUpReconciler;

  @Before
  public void buildEvent() {
    CustomResource<DataNodeSpec, CommonStatus> dataNode = new DataNode();
    ObjectMeta metadata = getMeta();
    IoTDBDataNodeConfig dataNodeConfig = new IoTDBDataNodeConfig();
    dataNodeConfig.setDataNodeProperties("config-1", "123");
    DataNodeSpec spec =
        new DataNodeSpecBuilder()
            .withEnableSafeDeploy(true)
            .withImage("registry.cn-beijing.aliyuncs.com/iotdb/iotdb:standalone")
            .withImagePullSecret("iotdbImagePullSecret")
            .withMode("standalone")
            .withIoTDBConfig(dataNodeConfig)
            .withLimits(new LimitsBuilder().withCpu(1).withMemory(1024).build())
            .withStorage(
                new StorageBuilder().withStorageClass("alicloud-disk-ssd").withLimit(1).build())
            .withService(
                new ServiceBuilder()
                    .withExternalTrafficPolicy("Local")
                    .withType("NodePort")
                    .build())
            .withPodDistributeStrategy("preferred")
            .withReplicas(1)
            .build();

    dataNode.setMetadata(metadata);
    dataNode.setSpec(spec);

    DataNode old = new DataNode();
    IoTDBDataNodeConfig oldDataNodeConfig = new IoTDBDataNodeConfig();
    oldDataNodeConfig.setDataNodeProperties("config-1", "456");
    DataNodeSpec oldSpec =
        new DataNodeSpecBuilder()
            .withEnableSafeDeploy(true)
            .withImage("registry.cn-beijing.aliyuncs.com/iotdb/iotdb:standalone-2")
            .withImagePullSecret("iotdbImagePullSecret")
            .withMode("standalone")
            .withIoTDBConfig(oldDataNodeConfig)
            .withLimits(new LimitsBuilder().withCpu(2).withMemory(1024).build())
            .withStorage(
                new StorageBuilder().withStorageClass("alicloud-disk-ssd").withLimit(1).build())
            .withService(
                new ServiceBuilder()
                    .withExternalTrafficPolicy("Local")
                    .withType("NodePort")
                    .build())
            .withPodDistributeStrategy("preferred")
            .withReplicas(2)
            .build();
    old.setSpec(oldSpec);
    old.setMetadata(getMeta());

    CustomResourceEvent event =
        new CustomResourceEvent(Action.MODIFIED, Kind.DATA_NODE, dataNode, old);
    updateReconciler = new DataNodeUpdateReconciler(event);
    updateReconciler.setKubernetesClient(getClient());

    CustomResourceEvent addEvent = new CustomResourceEvent(Action.ADDED, Kind.DATA_NODE, old, null);
    startUpReconciler = new DataNodeStartUpReconciler(addEvent);
    startUpReconciler.setKubernetesClient(getClient());
  }

  private ObjectMeta getMeta() {
    Map<String, String> labels = new HashMap<>();
    labels.put("app", "lemming");
    return new ObjectMetaBuilder()
        .withName("iotdb")
        .withNamespace("iotdb")
        .withLabels(labels)
        .withCreationTimestamp("2022-08-29T06:10:00Z")
        .withResourceVersion("231321313")
        .withUid("34124141")
        .build();
  }

  @Test
  public void testUpdateConfigMap() throws IOException {
    mockConfigMap();
    ConfigMap oldConfigMap = startUpReconciler.createConfigMap();
    oldConfigMap.getMetadata().setResourceVersion("231321313");
    mockConfigMap(oldConfigMap);
    ConfigMap configMap = updateReconciler.updateConfigMap();

    Mockito.verify(
            getClient().configMaps().inNamespace(Mockito.anyString()).withName(Mockito.anyString()))
        .require();

    Mockito.verify(
            getClient()
                .configMaps()
                .inNamespace(Mockito.anyString())
                .withName(Mockito.anyString())
                .lockResourceVersion(Mockito.anyString()))
        .replace();

    Properties properties = new Properties();
    properties.load(
        new StringReader(configMap.getData().get(CommonConstant.DATA_NODE_PROPERTY_FILE_NAME)));

    Assert.assertEquals("123", properties.getProperty("config-1"));
  }

  @Test
  public void testStatefulSet() throws IOException {
    mockConfigMap();
    ConfigMap oldConfigMap = startUpReconciler.createConfigMap();
    oldConfigMap.getMetadata().setResourceVersion("231321313");
    mockConfigMap(oldConfigMap);

    mockStatefulSet();
    StatefulSet oldStatefulSet = startUpReconciler.createStatefulSet(oldConfigMap);
    oldStatefulSet
        .getSpec()
        .setUpdateStrategy(
            new StatefulSetUpdateStrategyBuilder()
                .withNewRollingUpdate()
                .endRollingUpdate()
                .build());
    mockStatefulSet(oldStatefulSet);
    mockCustomResource(getDataNode());
    ConfigMap configMap = updateReconciler.updateConfigMap();
    StatefulSet statefulSet = updateReconciler.updateStatefulSet(configMap);

    Mockito.verify(
            getClient()
                .apps()
                .statefulSets()
                .inNamespace(Mockito.anyString())
                .withName(Mockito.anyString()))
        .require();

    Mockito.verify(
            getClient()
                .apps()
                .statefulSets()
                .inNamespace(Mockito.anyString())
                .withName(Mockito.anyString()))
        .replace();

    String image = statefulSet.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
    Assert.assertEquals("registry.cn-beijing.aliyuncs.com/iotdb/iotdb:standalone", image);

    Map<String, String> annotations =
        statefulSet.getSpec().getTemplate().getMetadata().getAnnotations();
    System.out.println(annotations);
    Assert.assertEquals(
        DigestUtils.sha(configMap.getData().toString()),
        annotations.get(CommonConstant.ANNOTATION_KEY_SHA));

    int partition = statefulSet.getSpec().getUpdateStrategy().getRollingUpdate().getPartition();
    Assert.assertEquals(0, partition);

    String cpu =
        statefulSet
            .getSpec()
            .getTemplate()
            .getSpec()
            .getContainers()
            .get(0)
            .getResources()
            .getLimits()
            .get("cpu")
            .getAmount();
    Assert.assertEquals("1", cpu);

    Assert.assertEquals(1, statefulSet.getSpec().getReplicas().intValue());
  }

  private DataNode getDataNode() {
    DataNode dataNode = new DataNodeBuilder().withMetadata(getMeta()).build();
    return dataNode;
  }
}
