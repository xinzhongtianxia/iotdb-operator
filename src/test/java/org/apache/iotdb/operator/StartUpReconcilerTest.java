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
import org.apache.iotdb.operator.common.EnvKey;
import org.apache.iotdb.operator.config.DataNodeConfig;
import org.apache.iotdb.operator.controller.reconciler.StartUpReconciler;
import org.apache.iotdb.operator.controller.reconciler.datanode.DataNodeStartUpReconciler;
import org.apache.iotdb.operator.crd.CommonStatus;
import org.apache.iotdb.operator.crd.DataNode;
import org.apache.iotdb.operator.crd.DataNodeSpec;
import org.apache.iotdb.operator.crd.DataNodeSpecBuilder;
import org.apache.iotdb.operator.crd.IoTDBDataNodeConfig;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.crd.LimitsBuilder;
import org.apache.iotdb.operator.crd.ServiceBuilder;
import org.apache.iotdb.operator.crd.StorageBuilder;
import org.apache.iotdb.operator.event.CustomResourceEvent;
import org.apache.iotdb.operator.util.DigestUtils;

import org.apache.commons.io.IOUtils;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpec;
import io.fabric8.kubernetes.client.CustomResource;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import io.fabric8.kubernetes.client.Watcher.Action;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StartUpReconcilerTest extends MockKubernetesClient {

  private StartUpReconciler reconciler;

  @Before
  public void buildEvent() {
    CustomResource<DataNodeSpec, CommonStatus> dataNode = new DataNode();
    Map<String, String> labels = new HashMap<>();
    labels.put("app", "lemming");
    ObjectMeta metadata =
        new ObjectMetaBuilder()
            .withName("iotdb")
            .withNamespace("iotdb")
            .withLabels(labels)
            .withCreationTimestamp("2022-08-29T06:10:00Z")
            .withResourceVersion("231321313")
            .withUid("34124141")
            .build();
    DataNodeSpec spec =
        new DataNodeSpecBuilder()
            .withEnableSafeDeploy(true)
            .withImage("registry.cn-beijing.aliyuncs.com/iotdb/iotdb:standalone")
            .withImagePullSecret("iotdbImagePullSecret")
            .withMode("standalone")
            .withIoTDBConfig(new IoTDBDataNodeConfig())
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
    CustomResourceEvent event =
        new CustomResourceEvent(Action.ADDED, Kind.DATA_NODE, dataNode, null);
    reconciler = new DataNodeStartUpReconciler(event);
    reconciler.setKubernetesClient(getClient());
  }

  @Test
  public void testCreateConfigMap() throws IOException {
    mockConfigMap();

    ConfigMap configMap = reconciler.createConfigMap();

    Mockito.verify(
            getClient()
                .configMaps()
                .inNamespace(Mockito.anyString())
                .resource(Mockito.mock(ConfigMap.class)))
        .createOrReplace();

    ObjectMeta metadata = configMap.getMetadata();
    assertEquals("iotdb-datanode", metadata.getName());
    assertEquals("iotdb", metadata.getNamespace());
    assertEquals(4, metadata.getLabels().size());
    assertEquals("datanode", metadata.getLabels().get(CommonConstant.LABEL_KEY_APP_KIND));

    Map<String, String> data = configMap.getData();
    String content =
        IOUtils.toString(
            getClass()
                .getResourceAsStream(
                    File.separator
                        + "conf"
                        + File.separator
                        + CommonConstant.DATA_NODE_INIT_SCRIPT_FILE_NAME),
            Charset.defaultCharset());
    assertEquals(content, data.get(CommonConstant.DATA_NODE_INIT_SCRIPT_FILE_NAME));

    Properties datanodeProperties = new Properties();
    datanodeProperties.load(
        new StringReader(data.get(CommonConstant.DATA_NODE_PROPERTY_FILE_NAME)));
    assertEquals(
        DataNodeConfig.getInstance().getRpcAddress(),
        datanodeProperties.getProperty(CommonConstant.DATA_NODE_RPC_ADDRESS));
    assertEquals(
        DataNodeConfig.getInstance().getRpcPort() + "",
        datanodeProperties.getProperty(CommonConstant.DATA_NODE_RPC_PORT));

    Properties restProperties = new Properties();
    restProperties.load(
        new StringReader(data.get(CommonConstant.DATA_NODE_REST_PROPERTY_FILE_NAME)));
    assertEquals(
        DataNodeConfig.getInstance().getRestPort() + "",
        restProperties.getProperty("rest_service_port"));
    assertTrue(Boolean.parseBoolean(restProperties.getProperty("enable_rest_service")));
  }

  @Test
  public void testCreateService() {
    mockService();

    Map<String, Service> serviceMap = reconciler.createServices();

    Mockito.verify(
            getClient()
                .services()
                .inNamespace(Mockito.anyString())
                .resource(Mockito.mock(Service.class)),
            Mockito.times(serviceMap.size()))
        .createOrReplace();

    Service internalService = serviceMap.get("iotdb-datanode");
    ServiceSpec internalServiceSpec = internalService.getSpec();
    assertEquals("None", internalServiceSpec.getClusterIP());
    assertEquals("metric", internalServiceSpec.getPorts().get(0).getName());
    assertEquals(
        DataNodeConfig.getInstance().getMetricPort(),
        internalServiceSpec.getPorts().get(0).getPort().intValue());
    Map<String, String> selector = DataNodeConfig.getInstance().getAdditionalLabels();
    selector.put(CommonConstant.LABEL_KEY_APP_NAME, "iotdb-datanode");
    assertEquals(internalServiceSpec.getSelector(), selector);

    Service externalService = serviceMap.get("iotdb-datanode-external");
    ServiceSpec externalServiceSpec = externalService.getSpec();
    assertEquals(CommonConstant.SERVICE_TYPE_NODE_PORT, externalServiceSpec.getType());
    assertEquals(
        CommonConstant.SERVICE_EXTERNAL_TRAFFIC_POLICY_LOCAL,
        externalServiceSpec.getExternalTrafficPolicy());
    assertEquals(selector, externalServiceSpec.getSelector());
    for (ServicePort servicePort : externalServiceSpec.getPorts()) {
      if (servicePort.getName().equals("rpc")) {
        assertEquals(DataNodeConfig.getInstance().getRpcPort(), servicePort.getPort().intValue());
        assertEquals(
            DataNodeConfig.getInstance().getRpcNodePort(), servicePort.getNodePort().intValue());
      } else {
        assertEquals(DataNodeConfig.getInstance().getRestPort(), servicePort.getPort().intValue());
        assertEquals(
            DataNodeConfig.getInstance().getRestNodePort(), servicePort.getNodePort().intValue());
      }
    }
  }

  @Test
  public void testCreateStatefulSet() throws IOException {
    mockConfigMap();
    ConfigMap configMap = reconciler.createConfigMap();

    mockStatefulSet();
    StatefulSet statefulSet = reconciler.createStatefulSet(configMap);

    Mockito.verify(
            getClient()
                .apps()
                .statefulSets()
                .inNamespace(Mockito.anyString())
                .resource(Mockito.mock(StatefulSet.class)))
        .createOrReplace();

    // meta
    ObjectMeta meta = statefulSet.getMetadata();
    assertEquals("iotdb-datanode", meta.getName());
    assertEquals(10L, meta.getDeletionGracePeriodSeconds().longValue());

    // annotation
    StatefulSetSpec statefulSetSpec = statefulSet.getSpec();
    assertEquals(
        DigestUtils.sha(configMap.getData().toString()),
        statefulSetSpec
            .getTemplate()
            .getMetadata()
            .getAnnotations()
            .get(CommonConstant.ANNOTATION_KEY_SHA));

    // affinity
    PodTemplateSpec podTemplateSpec = statefulSetSpec.getTemplate();
    String topologyKey =
        podTemplateSpec
            .getSpec()
            .getAffinity()
            .getPodAntiAffinity()
            .getPreferredDuringSchedulingIgnoredDuringExecution()
            .get(0)
            .getPodAffinityTerm()
            .getTopologyKey();
    assertEquals("kubernetes.io/hostname", topologyKey);

    // image
    Container container = podTemplateSpec.getSpec().getContainers().get(0);
    assertEquals("registry.cn-beijing.aliyuncs.com/iotdb/iotdb:standalone", container.getImage());

    // container port
    List<String> containerPortNames =
        container.getPorts().stream().map(ContainerPort::getName).collect(Collectors.toList());
    assertTrue(containerPortNames.contains("metric"));
    assertTrue(containerPortNames.contains("rpc"));
    assertTrue(containerPortNames.contains("rest"));

    // env
    List<EnvVar> envVars = container.getEnv();
    assertEquals(3, envVars.size());
    for (EnvVar envVar : envVars) {
      if (envVar.getName().equals(EnvKey.IOTDB_DATA_NODE_MODE.name())) {
        assertEquals("standalone", envVar.getValue());
      }
    }

    // probe
    assertEquals(
        DataNodeConfig.getInstance().getRpcPort(),
        container.getLivenessProbe().getTcpSocket().getPort().getIntVal().intValue());
    assertEquals(
        DataNodeConfig.getInstance().getRestPort(),
        container.getReadinessProbe().getHttpGet().getPort().getIntVal().intValue());

    // resource limit
    assertEquals(
        "1", container.getResources().getLimits().get(CommonConstant.RESOURCE_CPU).getAmount());

    // volume mounts
    List<VolumeMount> volumeMounts = container.getVolumeMounts();
    assertEquals(3, volumeMounts.size());
    for (VolumeMount volumeMount : volumeMounts) {
      if (volumeMount.getName().equals("iotdb-datanode" + CommonConstant.VOLUME_SUFFIX_DATA)) {
        if (volumeMount.getSubPath().equals("data")) {
          assertEquals("/iotdb/datanode/data", volumeMount.getMountPath());
        } else if (volumeMount.getSubPath().equals("logs")) {
          assertEquals("/iotdb/datanode/logs", volumeMount.getMountPath());
        } else {
          fail(volumeMount.getSubPath());
        }
      } else if (volumeMount
          .getName()
          .equals("iotdb-datanode" + CommonConstant.VOLUME_SUFFIX_CONFIG)) {
        assertEquals("/tmp/conf", volumeMount.getMountPath());
      } else {
        fail(volumeMount.getName());
      }
    }

    // image pull secret
    assertEquals(
        "iotdbImagePullSecret", podTemplateSpec.getSpec().getImagePullSecrets().get(0).getName());

    // dns config
    assertEquals("ndots", podTemplateSpec.getSpec().getDnsConfig().getOptions().get(0).getName());
    assertEquals("3", podTemplateSpec.getSpec().getDnsConfig().getOptions().get(0).getValue());

    // volume
    assertEquals(
        "iotdb-datanode", podTemplateSpec.getSpec().getVolumes().get(0).getConfigMap().getName());

    // pvc
    PersistentVolumeClaim pvc = statefulSetSpec.getVolumeClaimTemplates().get(0);
    assertEquals("iotdb-datanode-data", pvc.getMetadata().getName());
    assertEquals("ReadWriteOncePod", pvc.getSpec().getAccessModes().get(0));
    assertEquals("alicloud-disk-ssd", pvc.getSpec().getStorageClassName());
  }
}
