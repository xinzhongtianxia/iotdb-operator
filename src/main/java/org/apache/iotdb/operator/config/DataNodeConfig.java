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

package org.apache.iotdb.operator.config;

import org.apache.iotdb.operator.common.CommonConstant;
import org.apache.iotdb.operator.crd.Kind;

import java.util.Arrays;
import java.util.List;

/** Configurations for IoTDB ConfigNode. */
public class DataNodeConfig extends CommonConfig {

  /** will be replaced by pod's FQDN at the starting of the container. */
  private final String internalAddress = "0.0.0.0";

  /** will be replaced by pod's FQDN at the starting of the container. */
  private final String rpcAddress = "0.0.0.0";

  // for sdk
  private final int rpcNodePort = 30777;

  // for grafana
  private final int restNodePort = 30888;

  private final int rpcPort = 6667;
  private final int mppDataExchangePort = 8777;
  private final int internalPort = 9003;
  private final int dataRegionConsensusPort = 40010;
  private final int schemaRegionConsensusPort = 50010;
  private final int metricPort = 9091;
  private final int restPort = 18080;

  private List<String> defaultProperties =
      Arrays.asList(
          CommonConstant.DATA_NODE_DATA_REGION_CONSENSUS_PORT,
          CommonConstant.DATA_NODE_INTERNAL_ADDRESS,
          CommonConstant.DATA_NODE_INTERNAL_PORT,
          CommonConstant.DATA_NODE_MPP_DATA_EXCHANGE_PORT,
          CommonConstant.DATA_NODE_SCHEMA_REGION_CONSENSUS_PORT,
          CommonConstant.DATA_NODE_TARGET_CONFIG_NODE,
          CommonConstant.DATA_NODE_RPC_ADDRESS,
          CommonConstant.DATA_NODE_RPC_PORT);

  /** Commands to start datanode */
  private String startArgs =
      "cp -rf /tmp/conf /iotdb/datanode/ "
          + "&& cd /iotdb/datanode/conf "
          + "&& sh datanode-init.sh";

  public DataNodeConfig() {
    super(Kind.DATA_NODE);
  }

  public String getInternalAddress() {
    return internalAddress;
  }

  public int getRpcPort() {
    return rpcPort;
  }

  public int getMppDataExchangePort() {
    return mppDataExchangePort;
  }

  public int getInternalPort() {
    return internalPort;
  }

  public int getDataRegionConsensusPort() {
    return dataRegionConsensusPort;
  }

  public int getSchemaRegionConsensusPort() {
    return schemaRegionConsensusPort;
  }

  public int getMetricPort() {
    return metricPort;
  }

  public String getRpcAddress() {
    return rpcAddress;
  }

  public String getStartArgs() {
    return startArgs;
  }

  public static DataNodeConfig getInstance() {
    return ConfigDescriptor.INSTANCE;
  }

  public int getRpcNodePort() {
    return rpcNodePort;
  }

  public int getRestNodePort() {
    return restNodePort;
  }

  public int getRestPort() {
    return restPort;
  }

  private static class ConfigDescriptor {
    private ConfigDescriptor() {}

    private static DataNodeConfig INSTANCE = new DataNodeConfig();
  }

  public List<String> getDefaultProperties() {
    return defaultProperties;
  }
}
