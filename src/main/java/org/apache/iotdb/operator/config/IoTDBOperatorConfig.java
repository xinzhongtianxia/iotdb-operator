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

import java.util.Arrays;
import java.util.List;

/**
 * Configurations for IoTDB Operator.
 *
 * <p>
 *
 * <ol>
 *   <li>Firstly, these configs are set in the helm .Values file.
 *   <li>Secondly, configs in .Values file will be translate to a ConfigMap.
 *   <li>Thirdly, the parameters in ConfigMap will be passed to the container as ENVs.
 * </ol>
 *
 * <p>So, we just need to read these configs from ENV.
 */
public class IoTDBOperatorConfig {

  private String version = "v1";
  private String namespace = "iotdb";

  private final List<String> supportedVersions = Arrays.asList("v1");

  public String getVersion() {
    return version;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  private IoTDBOperatorConfig() {
    readConfigFromEnv();
  }

  private void readConfigFromEnv() {
    setNamespace(System.getenv("namespace"));
    setVersion(System.getenv("version"));
  }

  public List<String> getSupportedVersions() {
    return supportedVersions;
  }

  public static IoTDBOperatorConfig getInstance() {
    return ConfigDescriptor.INSTANCE;
  }

  private static class ConfigDescriptor {
    private ConfigDescriptor() {}

    private static IoTDBOperatorConfig INSTANCE = new IoTDBOperatorConfig();
  }
}
