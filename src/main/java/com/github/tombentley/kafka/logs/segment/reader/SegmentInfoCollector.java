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
package com.github.tombentley.kafka.logs.segment.reader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collector;

import com.github.tombentley.kafka.logs.segment.model.BaseMessage;
import com.github.tombentley.kafka.logs.segment.model.Batch;
import com.github.tombentley.kafka.logs.segment.model.ControlMessage;
import com.github.tombentley.kafka.logs.segment.model.DataMessage;
import com.github.tombentley.kafka.logs.segment.model.ProducerSession;
import com.github.tombentley.kafka.logs.segment.model.TransactionStateChangeMessage;
import com.github.tombentley.kafka.logs.segment.model.TransactionStateDeletion;

public class SegmentInfoCollector {

    private Batch currentBatch;
    private final Map<ProducerSession, FirstBatchInTxn> openTransactions = new HashMap<>();
    private Batch firstBatch;
    private final List<EmptyTransaction> emptyTransactions = new ArrayList<>();
    private final IntSummaryStatistics txnSizeStats = new IntSummaryStatistics();
    private final IntSummaryStatistics txnDurationStats = new IntSummaryStatistics();
    private long committed = 0;
    private long aborted = 0;
    private final Map<ProducerSession, TransactionStateChangeMessage.State> transactions = new HashMap<>();

    public SegmentInfoCollector() {
    }

    public static Collector<Batch, SegmentInfoCollector, SegmentInfo> collector() {
        return Collector.of(SegmentInfoCollector::new,
                SegmentInfoCollector::accumulator,
                SegmentInfoCollector::combiner,
                SegmentInfoCollector::finisher);
    }

    public void accumulator(Batch batch) {

        if (firstBatch == null) {
            firstBatch = batch;
        }
        currentBatch = batch;
        if (batch.isTransactional()) {
            ProducerSession session = new ProducerSession(batch.producerId(), batch.producerEpoch());
            if (batch.isControl()) {
                if (batch.count() != 1) {
                    throw new UnexpectedFileContent("Transactional data batch with >1 control records");
                }
                // Defer removal from openTransactions till we've seen the control record
            } else {
                var firstInBatch = openTransactions.get(session);
                if (firstInBatch == null) {
                    openTransactions.put(session, new FirstBatchInTxn(batch, new AtomicInteger(1)));
                } else {
                    firstInBatch.numDataBatches().incrementAndGet();
                }
            }
        }

        for (var message : batch.messages()) {
            if (message instanceof DataMessage) {

            } else if (message instanceof ControlMessage control) {
                if (control.commit()) {
                    committed++;
                } else {
                    aborted++;
                }
                var firstBatchInTxn = openTransactions.remove(currentBatch.session());
                if (firstBatchInTxn == null) {
                    emptyTransactions.add(new EmptyTransaction(currentBatch, control));
                } else {
                    txnSizeStats.accept(firstBatchInTxn.numDataBatches().get());
                    txnDurationStats.accept((int) (currentBatch.createTime() - firstBatchInTxn.firstBatchInTxn().createTime()));
                }
            } else if (message instanceof TransactionStateChangeMessage stateChange) {
                validateStateTransition(message, stateChange);
            } else if (message instanceof TransactionStateDeletion deletion) {

            }
        }
    }

    private void validateStateTransition(BaseMessage message, TransactionStateChangeMessage stateChange) {
        TransactionStateChangeMessage.State state = transactions.get(stateChange.session());
        if (state != null && !stateChange.state().validPrevious(state)) {
            throw new RuntimeException(message.filename() + ": " + message.line() + ": Illegal state change from " + state + " to " + stateChange.state());
        }
        transactions.put(stateChange.session(), stateChange.state());
    }

    public SegmentInfoCollector combiner(SegmentInfoCollector b) {
        return null;// TODO
    }

    public SegmentInfo finisher() {
        return new SegmentInfo(openTransactions, firstBatch, currentBatch, emptyTransactions,
                committed, aborted,
                txnSizeStats, txnDurationStats);
    }

}