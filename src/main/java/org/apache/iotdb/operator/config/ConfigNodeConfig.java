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

import org.apache.iotdb.operator.crd.Kind;

/** Configurations for IoTDB ConfigNode. */
public class ConfigNodeConfig extends CommonConfig {

  /** will be replaced to pod's FQDN at the starting of container. */
  private String rpcAddress = "0.0.0.0";

  private int rpcPort = 22277;
  private int consensusPort = 22278;
  private int metricPort = 9091;
  private String dataDir = "/iotdb/confignode/data";
  private String logDir = "/iotdb/confignode/logs";

  /** Commands to start confignode */
  private String startArgs =
      "cp -rf /tmp/conf /iotdb/confignode/ && cd /iotdb/confignode/conf && sh confignode-init.sh";

  public void setRpcAddress(String rpcAddress) {
    this.rpcAddress = rpcAddress;
  }

  public void setRpcPort(int rpcPort) {
    this.rpcPort = rpcPort;
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

  public int getRpcPort() {
    return rpcPort;
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

  public String getRpcAddress() {
    return rpcAddress;
  }

  private static class ConfigDescriptor {
    private ConfigDescriptor() {}

    private static ConfigNodeConfig INSTANCE = new ConfigNodeConfig();
  }

  @Override
  public String toString() {
    return "ConfigNodeConfig{"
        + "rpcAddress='"
        + rpcAddress
        + '\''
        + ", rpcPort="
        + rpcPort
        + ", consensusPort="
        + consensusPort
        + ", metricPort="
        + metricPort
        + ", dataDir='"
        + dataDir
        + '\''
        + ", logDir='"
        + logDir
        + '\''
        + ", startArgs='"
        + startArgs
        + '\''
        + '}';
  }
}
