#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

echo "============================="
echo "== IOTDB PRE-START SCRIPTS =="
echo "============================="

CONFIG_NODE_PROP_FILE="iotdb-confignode.properties"
CONFIG_NODE_START_FILE="../sbin/start-confignode.sh"
CONFIG_NODE_ENV_FILE="confignode-env.sh"
CONFIG_NODE_METRIC_FILE="iotdb-metric.yml"

modify_properties() {
  if [ ! -f $CONFIG_NODE_PROP_FILE ]; then
    echo "not found config file $CONFIG_NODE_PROP_FILE"
    exit 1
  fi

  pod_fqdn=$(echo $(hostname -f))
  echo "set rpc_address to $pod_fqdn"
  sed -i "s/^rpc_address.*/rpc_address=$pod_fqdn/" $CONFIG_NODE_PROP_FILE
}

modify_JVM_options() {
  if [ -n "$IOTDB_MAX_HEAP_MEMORY_SIZE" ]; then
    echo "set MAX_HEAP_SIZE to $IOTDB_MAX_HEAP_MEMORY_SIZE"
    sed -i "s/^    MAX_HEAP_SIZE.*/    MAX_HEAP_SIZE=$IOTDB_MAX_HEAP_MEMORY_SIZE/" $CONFIG_NODE_ENV_FILE
  fi

  if [ -n "$IOTDB_MAX_DIRECT_MEMORY_SIZE" ]; then
    echo "set MAX_DIRECT_MEMORY_SIZE to $IOTDB_MAX_DIRECT_MEMORY_SIZE"
    sed -i "s/^MAX_DIRECT_MEMORY_SIZE.*/MAX_DIRECT_MEMORY_SIZE=$IOTDB_MAX_DIRECT_MEMORY_SIZE/" $CONFIG_NODE_ENV_FILE
  fi
}

enable_metrics() {
  echo "enable metrics"
  sed -i "s/^enableMetric.*/enableMetric: true/" $CONFIG_NODE_METRIC_FILE
}

start_config_node() {
  exec "$CONFIG_NODE_START_FILE"
}

modify_properties
modify_JVM_options
enable_metrics
start_config_node
