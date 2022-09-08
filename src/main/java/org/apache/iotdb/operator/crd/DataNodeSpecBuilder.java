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

public class DataNodeSpecBuilder {

  private DataNodeSpec dataNodeSpec;

  public DataNodeSpecBuilder() {
    this.dataNodeSpec = new DataNodeSpec();
  }

  public DataNodeSpecBuilder withService(Service service) {
    dataNodeSpec.setService(service);
    return this;
  }

  public DataNodeSpecBuilder withMode(String mode) {
    dataNodeSpec.setMode(mode);
    return this;
  }

  public DataNodeSpecBuilder withImage(String image) {
    dataNodeSpec.setImage(image);
    return this;
  }

  public DataNodeSpecBuilder withReplicas(int replicas) {
    dataNodeSpec.setReplicas(replicas);
    return this;
  }

  public DataNodeSpecBuilder withConfigNodeName(String configNodeName) {
    dataNodeSpec.setConfignodeName(configNodeName);
    return this;
  }

  public DataNodeSpecBuilder withIoTDBConfig(IoTDBDataNodeConfig ioTDBConfig) {
    dataNodeSpec.setIotdbConfig(ioTDBConfig);
    return this;
  }

  public DataNodeSpecBuilder withImagePullSecret(String imagePullSecret) {
    dataNodeSpec.setImagePullSecret(imagePullSecret);
    return this;
  }

  public DataNodeSpecBuilder withEnableSafeDeploy(boolean enableSafeDeploy) {
    dataNodeSpec.setEnableSafeDeploy(enableSafeDeploy);
    return this;
  }

  public DataNodeSpecBuilder withLimits(Limits limits) {
    dataNodeSpec.setLimits(limits);
    return this;
  }

  public DataNodeSpecBuilder withStorage(Storage storage) {
    dataNodeSpec.setStorage(storage);
    return this;
  }

  public DataNodeSpecBuilder withPodDistributeStrategy(String podDistributeStrategy) {
    dataNodeSpec.setPodDistributeStrategy(podDistributeStrategy);
    return this;
  }

  public DataNodeSpec build() {
    return dataNodeSpec;
  }
}
