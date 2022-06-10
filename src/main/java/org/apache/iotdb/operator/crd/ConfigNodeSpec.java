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

public class ConfigNodeSpec extends CommonSpec {

  private String image;
  private String imagePullSecret;
  private Integer scale;
  private String podDistributeStrategy = "required";
  private Limits limits;
  private Storage storage;
  private IoTDBConfigNodeConfig iotdbConfig;

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  public String getImagePullSecret() {
    return imagePullSecret;
  }

  public void setImagePullSecret(String imagePullSecret) {
    this.imagePullSecret = imagePullSecret;
  }

  public Integer getScale() {
    return scale;
  }

  public void setScale(Integer scale) {
    this.scale = scale;
  }

  public String getPodDistributeStrategy() {
    return podDistributeStrategy;
  }

  public void setPodDistributeStrategy(String podDistributeStrategy) {
    this.podDistributeStrategy = podDistributeStrategy;
  }

  public Limits getLimits() {
    return limits;
  }

  public void setLimits(Limits limits) {
    this.limits = limits;
  }

  public Storage getStorage() {
    return storage;
  }

  public void setStorage(Storage storage) {
    this.storage = storage;
  }

  public IoTDBConfigNodeConfig getIotdbConfig() {
    return iotdbConfig;
  }

  public void setIotdbConfig(IoTDBConfigNodeConfig iotdbConfig) {
    this.iotdbConfig = iotdbConfig;
  }
}
