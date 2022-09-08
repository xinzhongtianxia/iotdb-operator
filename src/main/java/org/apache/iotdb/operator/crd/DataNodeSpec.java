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

public class DataNodeSpec extends CommonSpec {

  private IoTDBDataNodeConfig iotdbConfig;

  private Service service;

  private String mode;

  private String confignodeName;

  public IoTDBDataNodeConfig getIotdbConfig() {
    return iotdbConfig;
  }

  public void setIotdbConfig(IoTDBDataNodeConfig iotdbConfig) {
    this.iotdbConfig = iotdbConfig;
  }

  public String getConfignodeName() {
    return confignodeName;
  }

  public void setConfignodeName(String confignodeName) {
    this.confignodeName = confignodeName;
  }

  public Service getService() {
    return service;
  }

  public void setService(Service service) {
    this.service = service;
  }

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  @Override
  public String toString() {
    return "DataNodeSpec{"
        + "iotdbConfig="
        + iotdbConfig
        + ", service="
        + service
        + ", mode='"
        + mode
        + '\''
        + ", confignodeName='"
        + confignodeName
        + '\''
        + "} "
        + super.toString();
  }
}
