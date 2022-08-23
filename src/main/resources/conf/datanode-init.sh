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

DATA_NODE_PROP_FILE="iotdb-datanode.properties"
DATA_NODE_ENV_FILE="datanode-env.sh"
DATA_NODE_METRIC_FILE="iotdb-metric.yml"

DATA_NODE_START_SERVER_FILE="../sbin/start-server.sh"
DATA_NODE_START_CLUSTER_FILE="../sbin/start-datanode.sh"

POD_FQDN=$(echo $(hostname -f))

modify_properties() {
  if [ ! -f $DATA_NODE_PROP_FILE ]; then
    echo "not found config file $DATA_NODE_PROP_FILE"
    exit 1
  fi

  echo "set internal_address to $POD_FQDN"
  sed -i "s/^internal_address.*/internal_address=$POD_FQDN/" $DATA_NODE_PROP_FILE
}

modify_JVM_options() {
  if [ -n "$IOTDB_MAX_HEAP_MEMORY_SIZE" ]; then
    echo "set MAX_HEAP_SIZE to $IOTDB_MAX_HEAP_MEMORY_SIZE"
    sed -i "s/^    MAX_HEAP_SIZE.*/    MAX_HEAP_SIZE=$IOTDB_MAX_HEAP_MEMORY_SIZE/" $DATA_NODE_ENV_FILE
  fi

  if [ -n "$IOTDB_MAX_DIRECT_MEMORY_SIZE" ]; then
    echo "set MAX_DIRECT_MEMORY_SIZE to $IOTDB_MAX_DIRECT_MEMORY_SIZE"
    sed -i "s/^MAX_DIRECT_MEMORY_SIZE.*/MAX_DIRECT_MEMORY_SIZE=$IOTDB_MAX_DIRECT_MEMORY_SIZE/" $DATA_NODE_ENV_FILE
  fi
}

enable_metrics() {
  echo "enable metrics"
  sed -i "s/^enableMetric.*/enableMetric: true/" $DATA_NODE_METRIC_FILE
}

start_data_node() {
  echo "IOTDB_DATA_NODE_MODE=$IOTDB_DATA_NODE_MODE"
  if [ "$IOTDB_DATA_NODE_MODE" = 'standalone' ]; then
    echo "=========="
    exec "$DATA_NODE_START_SERVER_FILE"
  else
    echo "-------------"
    exec "$DATA_NODE_START_CLUSTER_FILE"
  fi
}

modify_properties
modify_JVM_options
enable_metrics
start_data_node
