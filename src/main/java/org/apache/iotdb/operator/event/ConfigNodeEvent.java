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

package org.apache.iotdb.operator.event;

import org.apache.iotdb.operator.crd.CommonStatus;
import org.apache.iotdb.operator.crd.ConfigNodeSpec;
import org.apache.iotdb.operator.crd.Kind;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher.Action;

public class ConfigNodeEvent extends BaseEvent {

  private CustomResource<ConfigNodeSpec, CommonStatus> oldResource;

  private final CustomResource<ConfigNodeSpec, CommonStatus> resource;

  public ConfigNodeEvent(
      Action action, Kind kind, CustomResource<ConfigNodeSpec, CommonStatus> resource) {
    super(action, kind, resource.getMetadata().getResourceVersion(), resource.getStatus());
    this.resource = resource;
  }

  public ConfigNodeEvent(
      Action action,
      Kind kind,
      CustomResource<ConfigNodeSpec, CommonStatus> resource,
      CustomResource<ConfigNodeSpec, CommonStatus> oldResource) {
    super(action, kind, resource.getMetadata().getResourceVersion(), resource.getStatus());
    this.resource = resource;
    this.oldResource = oldResource;
  }

  public CustomResource<ConfigNodeSpec, CommonStatus> getOldResource() {
    return oldResource;
  }

  public CustomResource<ConfigNodeSpec, CommonStatus> getResource() {
    return resource;
  }

  @Override
  public String toString() {
    return "ConfigNodeEvent{" + "resource=" + resource + ", eventId='" + eventId + '\'' + '}';
  }
}
