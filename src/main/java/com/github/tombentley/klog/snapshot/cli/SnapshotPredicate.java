/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.tombentley.klog.snapshot.cli;

import com.github.tombentley.klog.snapshot.model.ProducerState;
import java.util.function.Predicate;

class SnapshotPredicate {
    static Predicate<ProducerState> predicate(Integer pid, Integer producerEpoch) {
        Predicate<ProducerState> predicate = null;
        if (pid != null) {
            int pidPrim = pid;
            predicate = state -> state.producerId() == pidPrim;
        }
        if (producerEpoch != null) {
            int epoch = producerEpoch;
            Predicate<ProducerState> epochPredicate = state -> state.producerEpoch() == epoch;
            predicate = predicate != null ? predicate.and(epochPredicate) : epochPredicate;
        }
        return predicate;
    }
}
