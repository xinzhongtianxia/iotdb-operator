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

import org.apache.iotdb.operator.crd.CommonStatus;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.AppsAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import io.fabric8.kubernetes.client.dsl.internal.BaseOperation;
import io.fabric8.kubernetes.client.dsl.internal.apps.v1.StatefulSetOperationsImpl;
import io.fabric8.kubernetes.client.dsl.internal.core.v1.ServiceOperationsImpl;

import java.util.List;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockKubernetesClient {
  private KubernetesClient client;

  public MockKubernetesClient() {
    this.client = mock(KubernetesClient.class);
  }

  protected void mockConfigMap() {
    mockConfigMap(mock(ConfigMap.class));
  }

  protected void mockConfigMap(ConfigMap configMap) {
    MixedOperation mocConfigMapOperation = mock(MixedOperation.class);
    when(client.configMaps()).thenReturn(mocConfigMapOperation);

    BaseOperation baseOperation = mock(BaseOperation.class);
    when(mocConfigMapOperation.inNamespace(anyString())).thenReturn(baseOperation);

    Resource mockConfigMapResource = mock(Resource.class);
    when(baseOperation.resource(any(ConfigMap.class))).thenReturn(mockConfigMapResource);
    when(baseOperation.withName(anyString())).thenReturn(mockConfigMapResource);

    when(mockConfigMapResource.createOrReplace()).thenReturn(mock(ConfigMap.class));
    when(mockConfigMapResource.delete()).thenReturn(mock(List.class));
    when(mockConfigMapResource.replace()).thenReturn(mock(ConfigMap.class));
    when(mockConfigMapResource.require()).thenReturn(configMap);
    when(mockConfigMapResource.lockResourceVersion(anyString())).thenReturn(mockConfigMapResource);
  }

  protected void mockService() {
    ServiceOperationsImpl mockServiceOperation = mock(ServiceOperationsImpl.class);
    when(client.services()).thenReturn(mockServiceOperation);

    BaseOperation mockBaseOperation = mock(BaseOperation.class);
    when(mockServiceOperation.inNamespace(anyString())).thenReturn(mockBaseOperation);

    ServiceResource mockServiceResource = mock(ServiceResource.class);
    when(mockBaseOperation.resource(any(Service.class))).thenReturn(mockServiceResource);
    when(mockBaseOperation.withName(anyString())).thenReturn(mockServiceResource);

    when(mockServiceResource.createOrReplace()).thenReturn(mock(Service.class));
    when(mockServiceResource.delete()).thenReturn(mock(List.class));
    when(mockServiceResource.require()).thenReturn(mock(Service.class));
    when(mockServiceResource.replace()).thenReturn(mock(Service.class));
  }

  protected void mockStatefulSet() {
    mockStatefulSet(mock(StatefulSet.class));
  }

  protected void mockStatefulSet(StatefulSet statefulSet) {
    AppsAPIGroupDSL mockApps = mock(AppsAPIGroupDSL.class);
    when(client.apps()).thenReturn(mockApps);

    StatefulSetOperationsImpl mockStsOperation = mock(StatefulSetOperationsImpl.class);
    when(mockApps.statefulSets()).thenReturn(mockStsOperation);

    BaseOperation mockBaseOperation = mock(BaseOperation.class);
    when(mockStsOperation.inNamespace(anyString())).thenReturn(mockBaseOperation);

    RollableScalableResource<StatefulSet> mockStsResource = mock(RollableScalableResource.class);
    when(mockBaseOperation.resource(any(StatefulSet.class))).thenReturn(mockStsResource);
    when(mockBaseOperation.withName(anyString())).thenReturn(mockStsResource);

    when(mockStsResource.createOrReplace()).thenReturn(mock(StatefulSet.class));
    when(mockStsResource.delete()).thenReturn(mock(List.class));
    when(mockStsResource.require()).thenReturn(statefulSet);
    when(mockStsResource.replace()).thenReturn(mock(StatefulSet.class));
  }

  protected void mockCustomResource() {
    mockCustomResource(mock(CustomResource.class));
  }

  protected void mockCustomResource(CustomResource customResource) {
    MixedOperation mockCustomResource = mock(MixedOperation.class);
    when(client.resources(any(Class.class))).thenReturn(mockCustomResource);

    BaseOperation mockOperation = mock(BaseOperation.class);
    when(mockCustomResource.inNamespace(anyString())).thenReturn(mockOperation);

    Resource mockResource = mock(Resource.class);
    when(mockOperation.withName(anyString())).thenReturn(mockResource);

    when(mockResource.delete()).thenReturn(mock(List.class));
    when(mockResource.replaceStatus(any(CommonStatus.class))).thenReturn(CommonStatus.class);
    when(mockResource.require()).thenReturn(customResource);
  }

  protected KubernetesClient getClient() {
    return client;
  }
}
