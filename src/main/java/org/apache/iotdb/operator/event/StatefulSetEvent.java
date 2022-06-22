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

import org.apache.iotdb.operator.crd.Kind;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.Watcher.Action;

public class StatefulSetEvent extends BaseEvent {
  private final StatefulSet statefulSet;

  private StatefulSet oldStatefulSet;

  public StatefulSetEvent(Action action, Kind kind, StatefulSet statefulSet) {
    super(action, kind, statefulSet.getMetadata().getResourceVersion(), null);
    this.statefulSet = statefulSet;
  }

  public StatefulSetEvent(Action action, Kind kind, StatefulSet newSts, StatefulSet oldSts) {
    super(action, kind, newSts.getMetadata().getResourceVersion(), null);
    this.statefulSet = newSts;
    this.oldStatefulSet = oldSts;
  }

  public StatefulSet getStatefulSet() {
    return statefulSet;
  }

  @Override
  public String toString() {
    ObjectMeta meta = statefulSet.getMetadata();
    return "StatefulSetEvent{"
        + "statefulSetSpec="
        + statefulSet.getSpec()
        + "statefulSetStatus="
        + statefulSet.getStatus()
        + "namespace:name="
        + meta.getNamespace()
        + ":"
        + meta.getName()
        + "resourceVersion="
        + meta.getResourceVersion()
        + "uid="
        + meta.getUid()
        + "annotations="
        + meta.getAnnotations()
        + "labels="
        + meta.getLabels()
        + ", eventId='"
        + eventId
        + '\''
        + '}';
  }
}
