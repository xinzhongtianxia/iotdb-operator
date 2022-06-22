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

public class CommonStatus {

  /** numbers of available pods */
  private int available;

  /** numbers of desired pods defined in CRD */
  private int desired;

  /** current status, see {@link org.apache.iotdb.operator.common.STATE} */
  private String state;

  public CommonStatus() {}

  public CommonStatus(int available, int desired, String state) {
    this.available = available;
    this.desired = desired;
    this.state = state;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public int getAvailable() {
    return available;
  }

  public void setAvailable(int available) {
    this.available = available;
  }

  public int getDesired() {
    return desired;
  }

  public void setDesired(int desired) {
    this.desired = desired;
  }

  @Override
  public String toString() {
    return "CommonStatus{"
        + "available="
        + available
        + ", desired="
        + desired
        + ", state='"
        + state
        + '\''
        + '}';
  }
}
