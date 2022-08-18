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

import io.fabric8.kubernetes.api.model.ObjectMeta;

public class DataNodeBuilder {

  private CommonStatus commonStatus;

  private DataNodeSpec spec;

  private ObjectMeta metadata;

  public DataNodeBuilder() {}

  public DataNodeBuilder withStatus(CommonStatus commonStatus) {
    this.commonStatus = commonStatus;
    return this;
  }

  public DataNodeBuilder withSpec(DataNodeSpec spec) {
    this.spec = spec;
    return this;
  }

  public DataNodeBuilder withMetadata(ObjectMeta metadata) {
    this.metadata = metadata;
    return this;
  }

  public DataNode build() {
    DataNode dataNode = new DataNode();
    if (commonStatus != null) {
      dataNode.setStatus(commonStatus);
    }
    if (spec != null) {
      dataNode.setSpec(spec);
    }
    if (metadata != null) {
      dataNode.setMetadata(metadata);
    }
    return dataNode;
  }
}
