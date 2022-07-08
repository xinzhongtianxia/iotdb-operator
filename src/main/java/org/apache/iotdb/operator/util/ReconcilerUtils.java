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

package org.apache.iotdb.operator.util;

import org.apache.iotdb.operator.common.CommonConstant;
import org.apache.iotdb.operator.common.EnvKey;
import org.apache.iotdb.operator.crd.Limits;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReconcilerUtils {
  public static ResourceRequirements createResourceLimits(Limits limits) {
    Map<String, Quantity> resourceLimits = new HashMap<>(2);
    int cpu = limits.getCpu();
    int memoryMb = limits.getMemory();
    resourceLimits.put(CommonConstant.RESOURCE_CPU, new Quantity(String.valueOf(cpu)));
    resourceLimits.put(
        CommonConstant.RESOURCE_MEMORY,
        new Quantity(memoryMb + CommonConstant.RESOURCE_STORAGE_UNIT_M));
    return new ResourceRequirementsBuilder().withLimits(resourceLimits).build();
  }

  /**
   * To compute best-practice JVM memory options. Generally, it should be a relatively high
   * percentage of the container total memory, which makes no waste of system resources.
   */
  public static List<EnvVar> computeJVMMemory(Limits limits) {
    int memory = limits.getMemory();
    int maxHeapMemorySize = memory * 60 / 100;
    int maxDirectMemorySize = maxHeapMemorySize * 20 / 100;

    EnvVar heapMemoryEnv =
        new EnvVarBuilder()
            .withName(EnvKey.IOTDB_MAX_HEAP_MEMORY_SIZE.name())
            .withValue(maxHeapMemorySize + "M")
            .build();

    EnvVar directMemoryEnv =
        new EnvVarBuilder()
            .withName(EnvKey.IOTDB_MAX_DIRECT_MEMORY_SIZE.name())
            .withValue(maxDirectMemorySize + "M")
            .build();

    return Arrays.asList(heapMemoryEnv, directMemoryEnv);
  }
}
