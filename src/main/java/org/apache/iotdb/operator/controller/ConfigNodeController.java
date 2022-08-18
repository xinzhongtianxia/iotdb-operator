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

package org.apache.iotdb.operator.controller;

import org.apache.iotdb.operator.controller.reconciler.DefaultReconciler;
import org.apache.iotdb.operator.controller.reconciler.IReconciler;
import org.apache.iotdb.operator.controller.reconciler.confignode.ConfigNodeDeleteReconciler;
import org.apache.iotdb.operator.controller.reconciler.confignode.ConfigNodeStartUpReconciler;
import org.apache.iotdb.operator.controller.reconciler.confignode.ConfigNodeUpdateReconciler;
import org.apache.iotdb.operator.crd.ConfigNode;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.event.BaseEvent;
import org.apache.iotdb.operator.event.CustomResourceEvent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.Watcher.Action;

/**
 * Watch, receive and dispatch all events about ConfigNode AND its related resources from Kubernetes
 * Server with in the specific namespace.
 */
public class ConfigNodeController extends AbstractCustomResourceController {

  public ConfigNodeController() {
    super(Kind.CONFIG_NODE);
  }

  @Override
  protected IReconciler getReconciler(BaseEvent event) {
    Action action = event.getAction();
    switch (action) {
      case ADDED:
        return new ConfigNodeStartUpReconciler((CustomResourceEvent) event);
      case DELETED:
        return new ConfigNodeDeleteReconciler((CustomResourceEvent) event);
      case MODIFIED:
        return new ConfigNodeUpdateReconciler((CustomResourceEvent) event);
      default:
        return new DefaultReconciler(event);
    }
  }

  @Override
  protected Class<? extends HasMetadata> getResourceType() {
    return ConfigNode.class;
  }
}
