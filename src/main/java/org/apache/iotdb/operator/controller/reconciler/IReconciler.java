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

import java.io.IOException;

/** All reconcilers should implement this interface. */
public interface IReconciler {
  /**
   * The entry of reconciler
   *
   * @throws IOException
   */
  void reconcile() throws IOException;

  /** return the specific type of this reconciler. */
  ReconcilerType getType();

  enum ReconcilerType {
    DEFAULT,
    CONFIG_NODE_STARTUP,
    CONFIG_NODE_UPDATE,
    CONFIG_NODE_STATEFUL_SET,
    CONFIG_NODE_DELETE,
    DATA_NODE_STARTUP,
    DATA_NODE_STATEFUL_SET,
    DATA_NODE_UPDATE,
    DATA_NODE_DELETE
  }
}
