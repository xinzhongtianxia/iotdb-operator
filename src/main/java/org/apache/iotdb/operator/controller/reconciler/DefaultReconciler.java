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

package org.apache.iotdb.operator.controller.reconciler;

import org.apache.iotdb.operator.event.BaseEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** All unexpected or unsupported events will be routed here. */
public class DefaultReconciler implements IReconciler {
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultReconciler.class);

  private final BaseEvent event;

  public DefaultReconciler(BaseEvent event) {
    this.event = event;
  }

  @Override
  public void reconcile() {
    LOGGER.warn("unrecognized event : \n{}", event);
  }

  @Override
  public ReconcilerType getType() {
    return ReconcilerType.DEFAULT;
  }
}
