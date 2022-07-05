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

import java.util.HashMap;
import java.util.Map;

/** All the iotdb-roles' common configurations should be maintained here. */
public class CommonConfig {

  /**
   * Only one pv will be mounted into iotdb container, which will be shared by both data and logs,
   * we use subpath to separate them.
   */
  private String dataSubPath = "data";

  private String logSubPath = "logs";

  /**
   * ConfigMap will be mounted as files to a temporary path to avoid unexpected overriding the whole
   * conf dir.
   */
  private String configMapDir = "/tmp/conf";

  private String[] startCommand = {"/bin/bash", "-c"};

  private String pvcAccessMode = "ReadWriteOnce";

  /** some labels will be added to each resource that created by operator for further selectors. */
  private Map<String, String> additionalLabels = new HashMap<>();

  public void setDataSubPath(String dataSubPath) {
    this.dataSubPath = dataSubPath;
  }

  public void setLogSubPath(String logSubPath) {
    this.logSubPath = logSubPath;
  }

  public void setConfigMapDir(String configMapDir) {
    this.configMapDir = configMapDir;
  }

  public void setStartCommand(String[] startCommand) {
    this.startCommand = startCommand;
  }

  public void setPvcAccessMode(String pvcAccessMode) {
    this.pvcAccessMode = pvcAccessMode;
  }

  public void setAdditionalLabels(Map<String, String> additionalLabels) {
    this.additionalLabels = additionalLabels;
  }

  public String getPvcAccessMode() {
    return pvcAccessMode;
  }

  public String getDataSubPath() {
    return dataSubPath;
  }

  public String getLogSubPath() {
    return logSubPath;
  }

  public String getConfigMapDir() {
    return configMapDir;
  }

  public String[] getStartCommand() {
    return startCommand;
  }

  public CommonConfig(Kind kind) {
    additionalLabels.put(CommonConstant.LABEL_KEY_MANAGED_BY, "iotdb");
    additionalLabels.put(CommonConstant.LABEL_KEY_APP_KIND, kind.getName().toLowerCase());
  }

  public Map<String, String> getAdditionalLabels() {
    return additionalLabels;
  }
}
