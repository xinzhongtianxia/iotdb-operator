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

/** All iotdb common configurations should be maintained here. */
public class CommonConfig {

  /** some labels will be added to each resource that created by operator for further selectors. */
  private Map<String, String> additionalLabels = new HashMap<>();

  public CommonConfig(Kind kind) {
    additionalLabels.put(
        CommonConstant.LABEL_KEY_MANAGED_BY, CommonConstant.LABEL_VALUE_MANAGED_BY);
    additionalLabels.put(CommonConstant.LABEL_KEY_APP_KIND, kind.getName().toLowerCase());

    additionalLabels.put(
        CommonConstant.LABEL_KEY_OPERATOR_VERSION,
        IoTDBOperatorConfig.getInstance().getVersion());
  }

  public Map<String, String> getAdditionalLabels() {
    return additionalLabels;
  }
}
