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

import io.fabric8.kubernetes.client.Watcher.Action;

public class BaseEvent {
  protected final Action action;
  protected final Kind kind;
  protected final String resourceVersion;
  protected final String eventId;

  public BaseEvent(Action action, Kind kind, String resourceVersion) {
    this.action = action;
    this.kind = kind;
    this.resourceVersion = resourceVersion;
    this.eventId = EventManager.getInstance().getEventId(action, kind, resourceVersion);
  }

  public Action getAction() {
    return action;
  }

  public Kind getKind() {
    return kind;
  }

  public String getEventId() {
    return eventId;
  }
}
