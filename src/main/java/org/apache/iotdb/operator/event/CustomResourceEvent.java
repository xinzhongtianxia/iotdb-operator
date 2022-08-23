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

import org.apache.iotdb.operator.crd.CommonSpec;
import org.apache.iotdb.operator.crd.CommonStatus;
import org.apache.iotdb.operator.crd.Kind;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher.Action;

public class CustomResourceEvent extends BaseEvent {

  private final CustomResource<? extends CommonSpec, CommonStatus> oldResource;
  private final CustomResource<? extends CommonSpec, CommonStatus> resource;

  public CustomResourceEvent(
      Action action,
      Kind kind,
      CustomResource<? extends CommonSpec, CommonStatus> resource,
      CustomResource<? extends CommonSpec, CommonStatus> oldResource) {
    super(action, kind, resource.getMetadata().getResourceVersion());
    this.resource = resource;
    this.oldResource = oldResource;
  }

  /** see https://kubernetes.io/docs/reference/using-api/api-concepts/#semantics-for-watch */
  public boolean isSyntheticAdded() {
    if (action == Action.ADDED && resource.getStatus() != null) {
      return true;
    }
    return false;
  }

  public CustomResource<? extends CommonSpec, CommonStatus> getOldResource() {
    return oldResource;
  }

  public CustomResource<? extends CommonSpec, CommonStatus> getResource() {
    return resource;
  }

  @Override
  public String toString() {
    return "CustomResourceEvent{"
        + "oldResource="
        + oldResource
        + ", resource="
        + resource
        + ", action="
        + action
        + ", kind="
        + kind
        + ", resourceVersion='"
        + resourceVersion
        + '\''
        + ", eventId='"
        + eventId
        + '\''
        + '}';
  }
}
