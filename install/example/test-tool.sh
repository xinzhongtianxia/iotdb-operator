#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

# create and delete pvs in random path, just for local debug
# the dir path should be consistent with that in pv.yaml

dir=$RANDOM
rm -rf /Users/gaoyang/work/k8s/pvs/*
for (( i = 1; i < 4; i++ )); do
    mkdir /Users/gaoyang/work/k8s/pvs/pv$dir$i
    sed -i "" "s/pv.*$i$/pvs\/pv$dir$i/g" pv.yaml
done

kubectl delete pv local-1 local-2 local-3 && kubectl apply -f pv.yaml && kubectl apply -f confignode-example.yaml