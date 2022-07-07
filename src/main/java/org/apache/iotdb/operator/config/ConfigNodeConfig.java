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
public class ConfigNodeConfig extends CommonConfig {

  /** will be replaced by pod's FQDN at the starting of the container. */
  private String internalAddress = "0.0.0.0";

  private int internalPort = 22277;
  private int consensusPort = 22278;
  private int metricPort = 9091;
  private String dataDir = "/iotdb/confignode/data";
  private String logDir = "/iotdb/confignode/logs";

  private List<String> defaultProperties =
      Arrays.asList(
          CommonConstant.CONFIG_NODE_TARGET_CONFIG_NODE,
          CommonConstant.CONFIG_NODE_INTERNAL_ADDRESS,
          CommonConstant.CONFIG_NODE_INTERNAL_PORT,
          CommonConstant.CONFIG_NODE_CONSENSUS_PORT);

  /** Commands to start confignode */
  private String startArgs =
      "cp -rf /tmp/conf /iotdb/confignode/ "
          + "&& cd /iotdb/confignode/conf "
          + "&& sh confignode-init.sh";

  public void setInternalAddress(String internalAddress) {
    this.internalAddress = internalAddress;
  }

  public void setInternalPort(int internalPort) {
    this.internalPort = internalPort;
  }

  public void setConsensusPort(int consensusPort) {
    this.consensusPort = consensusPort;
  }

  public void setMetricPort(int metricPort) {
    this.metricPort = metricPort;
  }

  public void setDataDir(String dataDir) {
    this.dataDir = dataDir;
  }

  public void setLogDir(String logDir) {
    this.logDir = logDir;
  }

  public void setStartArgs(String startArgs) {
    this.startArgs = startArgs;
  }

  public ConfigNodeConfig() {
    super(Kind.CONFIG_NODE);
  }

  public String getDataDir() {
    return dataDir;
  }

  public String getLogDir() {
    return logDir;
  }

  public int getInternalPort() {
    return internalPort;
  }

  public int getConsensusPort() {
    return consensusPort;
  }

  public int getMetricPort() {
    return metricPort;
  }

  public static ConfigNodeConfig getInstance() {
    return ConfigDescriptor.INSTANCE;
  }

  public String getStartArgs() {
    return startArgs;
  }

  public String getInternalAddress() {
    return internalAddress;
  }

  private static class ConfigDescriptor {
    private ConfigDescriptor() {}

    private static ConfigNodeConfig INSTANCE = new ConfigNodeConfig();
  }

  public List<String> getDefaultProperties() {
    return defaultProperties;
  }
}
