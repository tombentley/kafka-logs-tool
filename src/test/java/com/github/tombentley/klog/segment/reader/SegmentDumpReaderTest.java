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
package com.github.tombentley.klog.segment.reader;

import java.util.List;
import java.util.stream.Collectors;

import com.github.tombentley.klog.segment.model.Batch;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SegmentDumpReaderTest {

    /** Without --deep-iteration */
    @Test
    public void testWithoutDeepIteration() {
        var content = "Dumping ./00000000000000000000.log\n" +
                      "Starting offset: 0\n" +
                      "baseOffset: 0 lastOffset: 1 count: 2 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 0 isTransactional: false isControl: false position: 0 CreateTime: 1632815304456 size: 88 magic: 2 compresscodec: none crc: 873053997 isvalid: true\n" +
                      "baseOffset: 2 lastOffset: 2 count: 1 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 0 isTransactional: false isControl: false position: 88 CreateTime: 1632815305550 size: 75 magic: 2 compresscodec: none crc: 945198711 isvalid: true\n" +
                      "baseOffset: 3 lastOffset: 3 count: 1 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 0 isTransactional: false isControl: false position: 163 CreateTime: 1632815307188 size: 79 magic: 2 compresscodec: none crc: 757930674 isvalid: true";
        Segment segment = new SegmentDumpReader()
                .readSegment("<test-input>", content.lines());
        assertFalse(segment.deepIteration());
        assertEquals(null, segment.topicName());
        assertEquals("<test-input>", segment.dumpFileName());
        assertEquals(Segment.Type.DATA, segment.type());
        TransactionalInfo transactionalInfo = segment
                .batches().collect(TransactionalInfoCollector.collector());
        Assertions.assertEquals(0, transactionalInfo.firstBatch().baseOffset());
        Assertions.assertEquals(1, transactionalInfo.firstBatch().lastOffset());
        assertFalse(transactionalInfo.firstBatch().isTransactional());
        Assertions.assertEquals(3, transactionalInfo.lastBatch().baseOffset());
        Assertions.assertEquals(3, transactionalInfo.lastBatch().lastOffset());
        assertFalse(transactionalInfo.lastBatch().isTransactional());
        assertEquals(0, transactionalInfo.numTransactionalAbort());
        assertEquals(0, transactionalInfo.numTransactionalCommit());
        assertTrue(transactionalInfo.emptyTransactions().isEmpty());
        assertTrue(transactionalInfo.openTransactions().isEmpty());
        assertEquals(0, transactionalInfo.txnSizeStats().getCount());
        assertEquals(0, transactionalInfo.txnDurationStats().getCount());
    }

    @Test
    public void testWithoutDeepIterationLineNumbers() {
        var content = "Dumping ./00000000000000000000.log\n" +
                      "Starting offset: 0\n" +
                      "baseOffset: 0 lastOffset: 1 count: 2 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 0 isTransactional: false isControl: false position: 0 CreateTime: 1632815304456 size: 88 magic: 2 compresscodec: none crc: 873053997 isvalid: true\n" +
                      "baseOffset: 2 lastOffset: 2 count: 1 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 0 isTransactional: false isControl: false position: 88 CreateTime: 1632815305550 size: 75 magic: 2 compresscodec: none crc: 945198711 isvalid: true\n" +
                      "baseOffset: 3 lastOffset: 3 count: 1 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 0 isTransactional: false isControl: false position: 163 CreateTime: 1632815307188 size: 79 magic: 2 compresscodec: none crc: 757930674 isvalid: true";
        List<Batch> batches = new SegmentDumpReader()
                .readSegment("<test-input>", content.lines())
                .batches().collect(Collectors.toList());
        assertEquals(3, batches.get(0).line());
        assertEquals(4, batches.get(1).line());
        assertEquals(5, batches.get(2).line());
    }

    /** --deep-iteration */
    @Test
    public void testWithDeepIteration() {
        var content = "Dumping /tmp/kafka-logs/foo-0/00000000000000000000.log\n" +
                      "Starting offset: 0\n" +
                      "baseOffset: 0 lastOffset: 1 count: 2 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 0 isTransactional: false isControl: false position: 0 CreateTime: 1632815304456 size: 88 magic: 2 compresscodec: none crc: 873053997 isvalid: true\n" +
                      "| offset: 0 CreateTime: 1632815303637 keySize: -1 valueSize: 7 sequence: -1 headerKeys: []\n" +
                      "| offset: 1 CreateTime: 1632815304456 keySize: -1 valueSize: 5 sequence: -1 headerKeys: []\n" +
                      "baseOffset: 2 lastOffset: 2 count: 1 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 0 isTransactional: false isControl: false position: 88 CreateTime: 1632815305550 size: 75 magic: 2 compresscodec: none crc: 945198711 isvalid: true\n" +
                      "| offset: 2 CreateTime: 1632815305550 keySize: -1 valueSize: 7 sequence: -1 headerKeys: []\n" +
                      "baseOffset: 3 lastOffset: 3 count: 1 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 0 isTransactional: false isControl: false position: 163 CreateTime: 1632815307188 size: 79 magic: 2 compresscodec: none crc: 757930674 isvalid: true\n" +
                      "| offset: 3 CreateTime: 1632815307188 keySize: -1 valueSize: 11 sequence: -1 headerKeys: []\n";
        TransactionalInfo transactionalInfo = new SegmentDumpReader().readSegment("<test-input>", content.lines())
                .batches()
                .collect(TransactionalInfoCollector.collector());
        Assertions.assertEquals(0, transactionalInfo.firstBatch().baseOffset());
        Assertions.assertEquals(1, transactionalInfo.firstBatch().lastOffset());
        assertFalse(transactionalInfo.firstBatch().isTransactional());
        Assertions.assertEquals(3, transactionalInfo.lastBatch().baseOffset());
        Assertions.assertEquals(3, transactionalInfo.lastBatch().lastOffset());
        assertFalse(transactionalInfo.lastBatch().isTransactional());
        assertEquals(0, transactionalInfo.numTransactionalAbort());
        assertEquals(0, transactionalInfo.numTransactionalCommit());
        assertTrue(transactionalInfo.emptyTransactions().isEmpty());
        assertTrue(transactionalInfo.openTransactions().isEmpty());
        assertEquals(0, transactionalInfo.txnSizeStats().getCount());
        assertEquals(0, transactionalInfo.txnDurationStats().getCount());
    }

    /** --deep-iteration */
    @Test
    public void testWithDeepIterationLineNumbers() {
        var content = "Dumping /tmp/kafka-logs/foo-0/00000000000000000000.log\n" +
                      "Starting offset: 0\n" +
                      "baseOffset: 0 lastOffset: 1 count: 2 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 0 isTransactional: false isControl: false position: 0 CreateTime: 1632815304456 size: 88 magic: 2 compresscodec: none crc: 873053997 isvalid: true\n" +
                      "| offset: 0 CreateTime: 1632815303637 keySize: -1 valueSize: 7 sequence: -1 headerKeys: []\n" +
                      "| offset: 1 CreateTime: 1632815304456 keySize: -1 valueSize: 5 sequence: -1 headerKeys: []\n" +
                      "baseOffset: 2 lastOffset: 2 count: 1 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 0 isTransactional: false isControl: false position: 88 CreateTime: 1632815305550 size: 75 magic: 2 compresscodec: none crc: 945198711 isvalid: true\n" +
                      "| offset: 2 CreateTime: 1632815305550 keySize: -1 valueSize: 7 sequence: -1 headerKeys: []\n" +
                      "baseOffset: 3 lastOffset: 3 count: 1 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 0 isTransactional: false isControl: false position: 163 CreateTime: 1632815307188 size: 79 magic: 2 compresscodec: none crc: 757930674 isvalid: true\n" +
                      "| offset: 3 CreateTime: 1632815307188 keySize: -1 valueSize: 11 sequence: -1 headerKeys: []\n";
        List<Batch> batches = new SegmentDumpReader().readSegment("<test-input>", content.lines())
                .batches().collect(Collectors.toList());
        assertEquals(2, batches.get(0).messages().size());
        assertEquals(1, batches.get(1).messages().size());
        assertEquals(1, batches.get(2).messages().size());

        assertEquals(3, batches.get(0).line());
        assertEquals(4, batches.get(0).messages().get(0).line());
        assertEquals(5, batches.get(0).messages().get(1).line());
        assertEquals(6, batches.get(1).line());
        assertEquals(7, batches.get(1).messages().get(0).line());
        assertEquals(8, batches.get(2).line());
        assertEquals(9, batches.get(2).messages().get(0).line());
    }

    /** --deep-iteration --print-data-log */
    @Test
    public void testWithDeepIterationAndPayload() {
        var content = "Dumping /tmp/kafka-logs/foo-0/00000000000000000000.log\n" +
                      "Starting offset: 0\n" +
                      "baseOffset: 0 lastOffset: 1 count: 2 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 0 isTransactional: false isControl: false position: 0 CreateTime: 1632815304456 size: 88 magic: 2 compresscodec: none crc: 873053997 isvalid: true\n" +
                      "| offset: 0 CreateTime: 1632815303637 keySize: -1 valueSize: 7 sequence: -1 headerKeys: [] payload: drfverv\n" +
                      "| offset: 1 CreateTime: 1632815304456 keySize: -1 valueSize: 5 sequence: -1 headerKeys: [] payload: rberb\n" +
                      "baseOffset: 2 lastOffset: 2 count: 1 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 0 isTransactional: false isControl: false position: 88 CreateTime: 1632815305550 size: 75 magic: 2 compresscodec: none crc: 945198711 isvalid: true\n" +
                      "| offset: 2 CreateTime: 1632815305550 keySize: -1 valueSize: 7 sequence: -1 headerKeys: [] payload: trnnrtn\n" +
                      "baseOffset: 3 lastOffset: 3 count: 1 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 0 isTransactional: false isControl: false position: 163 CreateTime: 1632815307188 size: 79 magic: 2 compresscodec: none crc: 757930674 isvalid: true\n" +
                      "| offset: 3 CreateTime: 1632815307188 keySize: -1 valueSize: 11 sequence: -1 headerKeys: [] payload: 65u5k6uk,yj\n";
        TransactionalInfo transactionalInfo = new SegmentDumpReader().readSegment("<test-input>", content.lines())
                .batches()
                .collect(TransactionalInfoCollector.collector());
        Assertions.assertEquals(0, transactionalInfo.firstBatch().baseOffset());
        Assertions.assertEquals(1, transactionalInfo.firstBatch().lastOffset());
        assertFalse(transactionalInfo.firstBatch().isTransactional());
        Assertions.assertEquals(3, transactionalInfo.lastBatch().baseOffset());
        Assertions.assertEquals(3, transactionalInfo.lastBatch().lastOffset());
        assertFalse(transactionalInfo.lastBatch().isTransactional());
        assertEquals(0, transactionalInfo.numTransactionalAbort());
        assertEquals(0, transactionalInfo.numTransactionalCommit());
        assertTrue(transactionalInfo.emptyTransactions().isEmpty());
        assertTrue(transactionalInfo.openTransactions().isEmpty());
        assertEquals(0, transactionalInfo.txnSizeStats().getCount());
        assertEquals(0, transactionalInfo.txnDurationStats().getCount());
    }

    /** --deep-iteration */
    @Test
    public void testWithDeepIterationWithControlRecords() {
        var content = "Dumping /tmp/kafka-0-logs/transactional-foo-0/00000000000000000000.log\n" +
                      "Starting offset: 0\n" +
                      "baseOffset: 0 lastOffset: 1 count: 2 baseSequence: 0 lastSequence: 1 producerId: 0 producerEpoch: 0 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 0 CreateTime: 1632840910502 size: 95 magic: 2 compresscodec: none crc: 3463992817 isvalid: true\n" +
                      "| offset: 0 CreateTime: 1632840910484 keySize: -1 valueSize: 10 sequence: 0 headerKeys: []\n" +
                      "| offset: 1 CreateTime: 1632840910502 keySize: -1 valueSize: 10 sequence: 1 headerKeys: []\n" +
                      "baseOffset: 2 lastOffset: 2 count: 1 baseSequence: 2 lastSequence: 2 producerId: 0 producerEpoch: 0 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 95 CreateTime: 1632840911002 size: 78 magic: 2 compresscodec: none crc: 3470306477 isvalid: true\n" +
                      "| offset: 2 CreateTime: 1632840911002 keySize: -1 valueSize: 10 sequence: 2 headerKeys: []\n" +
                      "baseOffset: 3 lastOffset: 3 count: 1 baseSequence: 3 lastSequence: 3 producerId: 0 producerEpoch: 0 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 173 CreateTime: 1632840911503 size: 78 magic: 2 compresscodec: none crc: 244140094 isvalid: true\n" +
                      "| offset: 3 CreateTime: 1632840911503 keySize: -1 valueSize: 10 sequence: 3 headerKeys: []\n" +
                      "baseOffset: 4 lastOffset: 4 count: 1 baseSequence: -1 lastSequence: -1 producerId: 0 producerEpoch: 0 partitionLeaderEpoch: 0 isTransactional: true isControl: true position: 251 CreateTime: 1632840911601 size: 78 magic: 2 compresscodec: none crc: 4234329125 isvalid: true\n" +
                      "| offset: 4 CreateTime: 1632840911601 keySize: 4 valueSize: 6 sequence: -1 headerKeys: [] endTxnMarker: COMMIT coordinatorEpoch: 4\n" +
                      "baseOffset: 5 lastOffset: 5 count: 1 baseSequence: 4 lastSequence: 4 producerId: 0 producerEpoch: 0 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 329 CreateTime: 1632840912091 size: 78 magic: 2 compresscodec: none crc: 3445037521 isvalid: true\n" +
                      "| offset: 5 CreateTime: 1632840912091 keySize: -1 valueSize: 10 sequence: 4 headerKeys: []\n" +
                      "baseOffset: 6 lastOffset: 6 count: 1 baseSequence: -1 lastSequence: -1 producerId: 0 producerEpoch: 0 partitionLeaderEpoch: 0 isTransactional: true isControl: true position: 407 CreateTime: 1632840912595 size: 78 magic: 2 compresscodec: none crc: 1079808135 isvalid: true\n" +
                      "| offset: 6 CreateTime: 1632840912595 keySize: 4 valueSize: 6 sequence: -1 headerKeys: [] endTxnMarker: COMMIT coordinatorEpoch: 4\n";
        TransactionalInfo transactionalInfo = new SegmentDumpReader().readSegment("<test-input>", content.lines())
                .batches()
                .collect(TransactionalInfoCollector.collector());
        Assertions.assertEquals(0, transactionalInfo.firstBatch().baseOffset());
        Assertions.assertEquals(1, transactionalInfo.firstBatch().lastOffset());
        Assertions.assertTrue(transactionalInfo.firstBatch().isTransactional());
        assertFalse(transactionalInfo.firstBatch().isControl());
        Assertions.assertEquals(6, transactionalInfo.lastBatch().baseOffset());
        Assertions.assertEquals(6, transactionalInfo.lastBatch().lastOffset());
        Assertions.assertTrue(transactionalInfo.lastBatch().isTransactional());
        Assertions.assertTrue(transactionalInfo.lastBatch().isControl());
        assertEquals(0, transactionalInfo.numTransactionalAbort());
        assertEquals(2, transactionalInfo.numTransactionalCommit());
        assertTrue(transactionalInfo.emptyTransactions().isEmpty());
        assertTrue(transactionalInfo.openTransactions().isEmpty());
        assertEquals(2, transactionalInfo.txnSizeStats().getCount());
        assertEquals(2, transactionalInfo.txnDurationStats().getCount());
    }

    /** --transaction-log-decoder on a segment from __transaction_state */
    @Test
    public void testWithTransactionLogDecoder() {
        var content = "Dumping /tmp/kafka-0-logs/__transaction_state-4/00000000000000000000.log\n" +
                      "Starting offset: 0\n" +
                      "baseOffset: 0 lastOffset: 0 count: 1 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 4 isTransactional: false isControl: false position: 0 CreateTime: 1632840910297 size: 120 magic: 2 compresscodec: none crc: 2207277534 isvalid: true\n" +
                      "| offset: 0 CreateTime: 1632840910297 keySize: 15 valueSize: 37 sequence: -1 headerKeys: [] key: transaction_metadata::transactionalId=my-txnal-id payload: producerId:0,producerEpoch:0,state=Empty,partitions=[],txnLastUpdateTimestamp=1632840910282,txnTimeoutMs=60000\n" +
                      "baseOffset: 1 lastOffset: 1 count: 1 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 4 isTransactional: false isControl: false position: 120 CreateTime: 1632840910511 size: 149 magic: 2 compresscodec: none crc: 2028590545 isvalid: true\n" +
                      "| offset: 1 CreateTime: 1632840910511 keySize: 15 valueSize: 64 sequence: -1 headerKeys: [] key: transaction_metadata::transactionalId=my-txnal-id payload: producerId:0,producerEpoch:0,state=Ongoing,partitions=[transactional-foo-0],txnLastUpdateTimestamp=1632840910510,txnTimeoutMs=60000\n" +
                      "baseOffset: 2 lastOffset: 2 count: 1 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 4 isTransactional: false isControl: false position: 269 CreateTime: 1632840911586 size: 149 magic: 2 compresscodec: none crc: 3719422551 isvalid: true\n" +
                      "| offset: 2 CreateTime: 1632840911586 keySize: 15 valueSize: 64 sequence: -1 headerKeys: [] key: transaction_metadata::transactionalId=my-txnal-id payload: producerId:0,producerEpoch:0,state=PrepareCommit,partitions=[transactional-foo-0],txnLastUpdateTimestamp=1632840911585,txnTimeoutMs=60000\n" +
                      "baseOffset: 3 lastOffset: 3 count: 1 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 4 isTransactional: false isControl: false position: 418 CreateTime: 1632840911620 size: 120 magic: 2 compresscodec: none crc: 3726340669 isvalid: true\n" +
                      "| offset: 3 CreateTime: 1632840911620 keySize: 15 valueSize: 37 sequence: -1 headerKeys: [] key: transaction_metadata::transactionalId=my-txnal-id payload: producerId:0,producerEpoch:0,state=CompleteCommit,partitions=[],txnLastUpdateTimestamp=1632840911588,txnTimeoutMs=60000\n" +
                      "baseOffset: 4 lastOffset: 4 count: 1 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 4 isTransactional: false isControl: false position: 538 CreateTime: 1632840912092 size: 149 magic: 2 compresscodec: none crc: 3298507796 isvalid: true\n" +
                      "| offset: 4 CreateTime: 1632840912092 keySize: 15 valueSize: 64 sequence: -1 headerKeys: [] key: transaction_metadata::transactionalId=my-txnal-id payload: producerId:0,producerEpoch:0,state=Ongoing,partitions=[transactional-foo-0],txnLastUpdateTimestamp=1632840912092,txnTimeoutMs=60000\n" +
                      "baseOffset: 5 lastOffset: 5 count: 1 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 4 isTransactional: false isControl: false position: 687 CreateTime: 1632840912592 size: 149 magic: 2 compresscodec: none crc: 764186261 isvalid: true\n" +
                      "| offset: 5 CreateTime: 1632840912592 keySize: 15 valueSize: 64 sequence: -1 headerKeys: [] key: transaction_metadata::transactionalId=my-txnal-id payload: producerId:0,producerEpoch:0,state=PrepareCommit,partitions=[transactional-foo-0],txnLastUpdateTimestamp=1632840912592,txnTimeoutMs=60000\n" +
                      "baseOffset: 6 lastOffset: 6 count: 1 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 4 isTransactional: false isControl: false position: 836 CreateTime: 1632840912607 size: 120 magic: 2 compresscodec: none crc: 1098902730 isvalid: true\n" +
                      "| offset: 6 CreateTime: 1632840912607 keySize: 15 valueSize: 37 sequence: -1 headerKeys: [] key: transaction_metadata::transactionalId=my-txnal-id payload: producerId:0,producerEpoch:0,state=CompleteCommit,partitions=[],txnLastUpdateTimestamp=1632840912593,txnTimeoutMs=60000\n";
        TransactionalInfo transactionalInfo = new SegmentDumpReader()
                .readSegment("<test-input>", content.lines())
                .batches()
                .collect(TransactionalInfoCollector.collector());
        Assertions.assertEquals(0, transactionalInfo.firstBatch().baseOffset());
        Assertions.assertEquals(0, transactionalInfo.firstBatch().lastOffset());
        assertFalse(transactionalInfo.firstBatch().isTransactional());
        Assertions.assertEquals(6, transactionalInfo.lastBatch().baseOffset());
        Assertions.assertEquals(6, transactionalInfo.lastBatch().lastOffset());
        assertFalse(transactionalInfo.lastBatch().isTransactional());
        assertEquals(0, transactionalInfo.numTransactionalAbort());
        assertEquals(0, transactionalInfo.numTransactionalCommit());
        assertTrue(transactionalInfo.emptyTransactions().isEmpty());
        assertTrue(transactionalInfo.openTransactions().isEmpty());
        assertEquals(0, transactionalInfo.txnSizeStats().getCount());
        assertEquals(0, transactionalInfo.txnDurationStats().getCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // 2.7 dump
            "Dumping /tmp/kafka-logs/foo-0/00000000000000000000.log\n" +
                    "Starting offset: 0\n" +
                    "baseOffset: 933607637 lastOffset: 933607638 count: 2 baseSequence: 0 lastSequence: 0 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 63 isTransactional: false isControl: false position: 0 CreateTime: 1655761268674 size: 165 magic: 2 compresscodec: NONE crc: 1118624748 isvalid: true\n" +
                    "| offset: 933607637 CreateTime: 1655761268674 keysize: 71 valuesize: 24 sequence: 0 headerKeys: []\n" +
                    "| offset: 933607638 CreateTime: 1655761268674 keysize: 71 valuesize: 24 sequence: 1 headerKeys: []\n",
            // 3.x dump
            "Dumping /tmp/kafka-logs/foo-0/00000000000000000000.log\n" +
                    "Log starting offset: 0\n" +
                    "baseOffset: 933607637 lastOffset: 933607637 count: 2 baseSequence: 0 lastSequence: 0 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 63 isTransactional: false isControl: false deleteHorizonMs: OptionalLong.empty position: 0 CreateTime: 1655761268674 size: 165 magic: 2 compresscodec: none crc: 1118624748 isvalid: true\n" +
                    "| offset: 933607637 CreateTime: 1655761268674 keySize: 71 valueSize: 24 sequence: 0 headerKeys: []\n" +
                    "| offset: 933607638 CreateTime: 1655761268674 keysize: 71 valuesize: 24 sequence: 1 headerKeys: []\n"
    })
    public void testSupportForMultipleLogDumpFormats(String content) {
        List<Batch> batches = new SegmentDumpReader().readSegment("<test-input>", content.lines())
                .batches().collect(Collectors.toList());
        assertEquals(2, batches.get(0).messages().size());
        assertEquals(3, batches.get(0).line());
        assertEquals(4, batches.get(0).messages().get(0).line());
        assertEquals(5, batches.get(0).messages().get(1).line());
    }

    @Test
    public void testInvalidLogDumpFormat() {
        // in 2.8 the kafka-dump-log.sh is broken: some batch record fields are leaked into the data record
        var content = "Dumping /tmp/kafka-logs/foo-0/00000000000000000000.log\n" +
                "Starting offset: 0\n" +
                "baseOffset: 933607637 lastOffset: 933607637 count: 1 baseSequence: 0 lastSequence: 0 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 63 isTransactional: false isControl: false position: 0 CreateTime: 1655761268674 size: 165 magic: 2 compresscodec: NONE crc: 1118624748 isvalid: true\n" +
                "| offset: 933607637 isValid: true crc: null keySize: 71 valueSize: 24 CreateTime: 1655761268674 baseOffset: 933607637 lastOffset: 933607637 baseSequence: 0 lastSequence: 0 producerEpoch: -1 partitionLeaderEpoch: 63 batchSize: 165 magic: 2 compressType: NONE position: 0 sequence: 0 headerKeys: []\n";
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, () -> {
            new SegmentDumpReader().readSegment("<test-input>", content.lines())
                    .batches().collect(Collectors.toList());
        });
        Assertions.assertEquals("Expected 1 data records in batch, but this doesn't look like a data record", thrown.getMessage());
    }

    // TODO simulate hanging transaction
    // TODO test for the txn state machine

}
