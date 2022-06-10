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

/**
 * Configurations for IoTDB Operator.
 *
 * <p>
 *
 * <ol>
 *   <li>Firstly, these configs are set in the helm .Values file.
 *   <li>Secondly, configs in .Values file will be translate to a ConfigMap.
 *   <li>Thirdly, the ConfigMap will be passed to the container as ENVs.
 * </ol>
 *
 * <p>So, we just need to read these configs from ENV.
 */
public class IoTDBOperatorConfig {

  private String version;
  private String nameSpace;

  public String getVersion() {
    return version;
  }

  public String getNameSpace() {
    return nameSpace;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public void setNameSpace(String nameSpace) {
    this.nameSpace = nameSpace;
  }

  private IoTDBOperatorConfig() {
    readConfigFromEnv();
  }

  private void readConfigFromEnv() {
    setNameSpace(System.getenv("nameSpace"));
    setVersion(System.getenv("version"));
  }

  public static IoTDBOperatorConfig getInstance() {
    return ConfigDescriptor.INSTANCE;
  }

  private static class ConfigDescriptor {
    private ConfigDescriptor() {}

    private static IoTDBOperatorConfig INSTANCE = new IoTDBOperatorConfig();
  }
}
