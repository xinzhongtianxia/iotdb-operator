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

import org.apache.iotdb.operator.common.BaseEvent;
import org.apache.iotdb.operator.controller.reconciler.DefaultReconciler;
import org.apache.iotdb.operator.controller.reconciler.IReconciler;
import org.apache.iotdb.operator.controller.reconciler.confignode.ConfigNodeDeleteReconciler;
import org.apache.iotdb.operator.controller.reconciler.confignode.ConfigNodeStartUpReconciler;
import org.apache.iotdb.operator.controller.reconciler.confignode.ConfigNodeUpdateReconciler;
import org.apache.iotdb.operator.crd.ConfigNode;
import org.apache.iotdb.operator.crd.ConfigNodeList;
import org.apache.iotdb.operator.crd.Kind;

import io.fabric8.kubernetes.client.Watcher.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigNodeController extends BaseController {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigNodeController.class);

  @Override
  public Class<ConfigNode> getResourceClass() {
    return ConfigNode.class;
  }

  @Override
  public Class<ConfigNodeList> getResourceListClass() {
    return ConfigNodeList.class;
  }

  @Override
  public Kind getKind() {
    return Kind.CONFIG_NODE;
  }

  @Override
  void reconcile(BaseEvent event) {
    IReconciler reconciler = getReconciler(event);
    reconciler.reconcile(event.getResource());
  }

  private IReconciler getReconciler(BaseEvent event) {
    Action action = event.getAction();
    switch (action) {
      case ADDED:
        return new ConfigNodeStartUpReconciler();
      case DELETED:
        return new ConfigNodeDeleteReconciler();
      case MODIFIED:
        return new ConfigNodeUpdateReconciler();
      default:
        return new DefaultReconciler();
    }
  }
}
