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

package org.apache.iotdb.operator.util;

import org.apache.iotdb.operator.KubernetesClientManager;
import org.apache.iotdb.operator.crd.Kind;

import io.fabric8.kubernetes.api.model.MicroTime;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.events.v1.Event;
import io.fabric8.kubernetes.api.model.events.v1.EventBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class OutputEventUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(OutputEventUtils.class);

  private static final KubernetesClient kubernetesClient =
      KubernetesClientManager.getInstance().getClient();

  public static final String EVENT_TYPE_NORMAL = "Normal";
  public static final String EVENT_TYPE_WARNING = "Warning";

  public static void sendEvent(
      Kind kind,
      String type,
      String action,
      ObjectMeta meta,
      String message,
      String reason,
      String reportingController) {
    Event event =
        new EventBuilder()
            .withEventTime(
                new MicroTime(
                    ZonedDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'"))))
            .withAction(action)
            .withNewRegarding()
            .withKind(kind.getName())
            .withName(meta.getName())
            .withNamespace(meta.getNamespace())
            .withResourceVersion(meta.getResourceVersion())
            .withUid(meta.getUid())
            .endRegarding()
            .withNote(message)
            .withNewMetadata()
            .withNamespace(meta.getNamespace())
            .withName(meta.getName() + "." + Instant.now().getNano())
            .endMetadata()
            .withReportingController(reportingController)
            .withReportingInstance(meta.getName() + "-" + kind.getName().toLowerCase())
            .withReason(reason)
            .withType(type)
            .build();
    LOGGER.debug(event.toString());
    try {
      kubernetesClient.resource(event).inNamespace(meta.getNamespace()).create();
    } catch (Exception e) {
      LOGGER.error("exception when patch event, event = {}", event, e);
    }
  }
}
