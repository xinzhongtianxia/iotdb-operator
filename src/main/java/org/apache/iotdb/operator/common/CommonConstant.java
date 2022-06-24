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

package org.apache.iotdb.operator.common;

public class CommonConstant {

  // ============= Common =================

  public static final String GENERATE_BY_OPERATOR =
      "############ Generated By IoTDB Operator ##############";
  public static final String LINES_SEPARATOR = "\n";

  public static final String POD_AFFINITY_POLICY_PREFERRED = "preferred";
  public static final String POD_AFFINITY_POLICY_REQUIRED = "required";
  public static final String RESOURCE_STORAGE_UNIT_M = "Mi";
  public static final String RESOURCE_STORAGE_UNIT_G = "Gi";
  public static final String RESOURCE_CPU = "cpu";
  public static final String RESOURCE_MEMORY = "memory";
  public static final String RESOURCE_STORAGE = "storage";

  public static final String IMAGE_PULL_POLICY_IF_NOT_PRESENT = "IfNotPresent";

  public static final String LABEL_KEY_IOTDB_OPERATOR = "iotdb-operator";
  public static final String LABEL_KEY_APP_KIND = "app-kind";
  public static final String LABEL_KEY_APP_NAME = "app-name";

  public static final String VOLUME_SUFFIX_DATA = "-data";
  public static final String VOLUME_SUFFIX_CONFIG = "-config";

  public static final String SERVICE_SUFFIX_EXTERNAL = "-external";

  // ==============Config Node ===============

  public static final String CONFIG_NODE_PROPERTY_FILE_NAME = "iotdb-confignode.properties";
  public static final String CONFIG_NODE_INIT_SCRIPT_FILE_NAME = "confignode-init.sh";
  public static final String CONFIG_NODE_RPC_ADDRESS = "rpc_address";
  public static final String CONFIG_NODE_RPC_PORT = "rpc_port";
  public static final String CONFIG_NODE_TARGET_CONFIG_NODE = "target_confignode";
}
