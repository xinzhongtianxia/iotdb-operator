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

package org.apache.iotdb.operator.controller.reconciler.confignode;

import org.apache.iotdb.operator.common.STATE;
import org.apache.iotdb.operator.controller.reconciler.IReconciler;
import org.apache.iotdb.operator.crd.CommonStatus;
import org.apache.iotdb.operator.crd.ConfigNode;
import org.apache.iotdb.operator.crd.ConfigNodeBuilder;
import org.apache.iotdb.operator.event.BaseEvent;
import org.apache.iotdb.operator.event.StatefulSetEvent;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpec;
import io.fabric8.kubernetes.api.model.apps.StatefulSetStatus;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.dsl.Deletable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This reconciler is only responsible for
 *
 * <ol>
 *   <li>computing and patching cnode's status when there is a status change happened to sts.
 *   <li>cleaning pvcs created by sts when the sts has been deleted
 * </ol>
 */
public class ConfigNodeStatefulSetReconciler implements IReconciler {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ConfigNodeStatefulSetReconciler.class);

  private final ObjectMeta metadata;
  private final Action action;
  private final StatefulSetStatus status;
  private final StatefulSetSpec spec;

  public ConfigNodeStatefulSetReconciler(BaseEvent baseEvent) {
    StatefulSetEvent statefulSetEvent = (StatefulSetEvent) baseEvent;
    metadata = statefulSetEvent.getStatefulSet().getMetadata();
    action = statefulSetEvent.getAction();
    spec = statefulSetEvent.getStatefulSet().getSpec();
    status = statefulSetEvent.getStatefulSet().getStatus();
  }

  @Override
  public void reconcile() {

    if (action == Action.MODIFIED) {
      if (status == null) {
        return;
      }

      int available = status.getAvailableReplicas();
      int desired = spec.getReplicas();

      STATE state = available == desired ? STATE.READY : STATE.RECONCILING;

      String stsName = metadata.getName();
      String configNodeName = stsName.substring(0, stsName.lastIndexOf("-"));

      // For now, we just get out StatefulSet's status and patch it to ConfigNode.
      ConfigNode configNodeWithOnlyStatus =
          new ConfigNodeBuilder()
              .withStatus(new CommonStatus(available, desired, state.name()))
              .build();

      LOGGER.info(
          "patch status = {} to ConfigNode {}",
          configNodeWithOnlyStatus.getStatus(),
          configNodeName);

      kubernetesClient
          .resources(ConfigNode.class)
          .inNamespace(metadata.getNamespace())
          .withName(configNodeName)
          .patchStatus(configNodeWithOnlyStatus);
    } else if (action == Action.DELETED) {
      // we should delete pvc created by the statefulset, or the pvc and its bounded pv will never
      // be removed.
      kubernetesClient
          .persistentVolumeClaims()
          .inNamespace(metadata.getNamespace())
          .withLabels(metadata.getLabels())
          .resources()
          .forEach(Deletable::delete);
      LOGGER.info("pvc deleted : labels = {}", metadata.getLabels());
    } else {
      // do nothing
    }
  }

  @Override
  public ReconcilerType getType() {
    return ReconcilerType.CONFIG_NODE_STATEFUL_SET;
  }
}
