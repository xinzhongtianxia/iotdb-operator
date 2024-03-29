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

/** Common specs in iotdb-role's CRDs. */
public class CommonSpec {
  private String image;
  private String imagePullSecret;
  private int replicas;
  private boolean enableSafeDeploy;
  private String podDistributeStrategy = "required";
  private Limits limits;
  private Storage storage;

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

  public int getReplicas() {
    return replicas;
  }

  public void setReplicas(int replicas) {
    this.replicas = replicas;
  }

  public String getPodDistributeStrategy() {
    return podDistributeStrategy;
  }

  public void setPodDistributeStrategy(String podDistributeStrategy) {
    this.podDistributeStrategy = podDistributeStrategy;
  }

  public boolean isEnableSafeDeploy() {
    return enableSafeDeploy;
  }

  public void setEnableSafeDeploy(boolean enableSafeDeploy) {
    this.enableSafeDeploy = enableSafeDeploy;
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

  @Override
  public String toString() {
    return "CommonSpec{"
        + "image='"
        + image
        + '\''
        + ", imagePullSecret='"
        + imagePullSecret
        + '\''
        + ", replicas="
        + replicas
        + ", enableSafeDeploy="
        + enableSafeDeploy
        + ", podDistributeStrategy='"
        + podDistributeStrategy
        + '\''
        + ", limits="
        + limits
        + ", storage="
        + storage
        + '}';
  }
}
