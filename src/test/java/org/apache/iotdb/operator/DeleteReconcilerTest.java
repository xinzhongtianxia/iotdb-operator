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

import org.apache.iotdb.operator.controller.reconciler.DeleteReconciler;
import org.apache.iotdb.operator.controller.reconciler.datanode.DataNodeDeleteReconciler;
import org.apache.iotdb.operator.crd.CommonStatus;
import org.apache.iotdb.operator.crd.DataNode;
import org.apache.iotdb.operator.crd.DataNodeSpec;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.event.CustomResourceEvent;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher.Action;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

public class DeleteReconcilerTest extends MockKubernetesClient {

  private DeleteReconciler reconciler;

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
    dataNode.setMetadata(metadata);
    CustomResourceEvent event =
        new CustomResourceEvent(Action.DELETED, Kind.DATA_NODE, dataNode, null);
    reconciler = new DataNodeDeleteReconciler(event);
    reconciler.setKubernetesClient(getClient());
  }

  @Test
  public void testDeleteDataNode() {
    mockConfigMap();
    mockService();
    mockStatefulSet();
    mockCustomResource();

    reconciler.deleteConfigMap();
    reconciler.deleteService();
    reconciler.deleteStatefulset();
    reconciler.deleteCustomResource();

    Mockito.verify(
            getClient()
                .services()
                .inNamespace(Mockito.anyString())
                .resource(Mockito.mock(Service.class)),
            Mockito.times(2))
        .delete();

    Mockito.verify(
            getClient()
                .configMaps()
                .inNamespace(Mockito.anyString())
                .resource(Mockito.mock(ConfigMap.class)))
        .delete();

    Mockito.verify(
            getClient()
                .apps()
                .statefulSets()
                .inNamespace(Mockito.anyString())
                .resource(Mockito.mock(StatefulSet.class)))
        .delete();

    Mockito.verify(
            getClient()
                .resources(DataNode.class)
                .inNamespace(Mockito.anyString())
                .withName(Mockito.anyString()))
        .delete();
  }
}
