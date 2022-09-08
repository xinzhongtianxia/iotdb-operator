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

package org.apache.iotdb.operator.crd;

import org.apache.iotdb.operator.config.ConfigNodeConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * the configNodeProperties defined in ConfigNode-CRD, part of the Events from Kubernetes Server in
 * JSON format, will be mapping to this Object By the Fabric8 Kubernetes Java Client.
 */
public class IoTDBConfigNodeConfig {

  private final Map<String, Object> configNodeProperties = new HashMap<>();

  public Map<String, Object> getConfigNodeProperties() {
    return configNodeProperties;
  }

  public void setConfigNodeProperties(String name, Object value) {
    // user need not to set some default configurations that should be set by IoTDB-Operator
    if (ConfigNodeConfig.getInstance().getDefaultProperties().contains(name)) {
      return;
    }
    configNodeProperties.put(name, value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    IoTDBConfigNodeConfig that = (IoTDBConfigNodeConfig) o;
    return configNodeProperties.equals(that.configNodeProperties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(configNodeProperties);
  }

  @Override
  public String toString() {
    return "IoTDBConfigNodeConfig{" + "configNodeProperties=" + configNodeProperties + '}';
  }
}
