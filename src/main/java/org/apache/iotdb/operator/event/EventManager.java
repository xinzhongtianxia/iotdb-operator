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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(EventManager.class);
  private static final EventManager instance = new EventManager();

  public String getEventId(Action action, Kind kind, String resourceVersion) {
    return action.name() + "-" + kind.getName() + "-" + resourceVersion;
  }

  private EventManager() {}

  public static EventManager getInstance() {
    return instance;
  }
}
