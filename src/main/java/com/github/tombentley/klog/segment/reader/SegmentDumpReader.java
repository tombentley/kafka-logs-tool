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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.github.tombentley.klog.segment.model.BaseMessage;
import com.github.tombentley.klog.segment.model.Batch;
import com.github.tombentley.klog.segment.model.ControlMessage;
import com.github.tombentley.klog.segment.model.DataMessage;
import com.github.tombentley.klog.segment.model.TransactionStateChange;
import com.github.tombentley.klog.segment.model.TransactionStateDeletion;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.lang.Short.parseShort;

/**
 * tool [action] [filter] [options] [files...]
 * <p>Where action could be</p>
 *  * <ul>
 *        <li>cat  (to just dump the parsed records (this still has value over cat [files] because it shows meaningful dates</li>
 *  *     <li>stat (to show general summary statistics)</li>
 *  *     <li>txn  (to show statistics)</li>
 *  * </ul>
 * Where filter could be particular to the action, but might include
 * <ul>
 * <li>{@code --pid 123 --producer-epoch 1} on a normal partition to show messages/batches by that producer session
 * <li>{@code --leader-epoch 123} on a normal partition to show batches appended by that leader
 * <li>{@code --transactional-id mytxid} (on a __transaction_state partition)
 * <ul>
 * <p>Options could be particular to a given action, and filter
 * --topic __transaction_state --partition 1 --config cleanup.policy=delete.</p>
 * <p>And where files are the dumped log segments in the given topic and partition</p>
 */
public class SegmentDumpReader {

    private final static Pattern MESSAGE_SET_PATTERN = Pattern.compile("baseOffset: (?<baseOffset>[0-9]+) " +
            "lastOffset: (?<lastOffset>[0-9]+) " +
            "count: (?<count>[0-9]+) " +
            "baseSequence: (?<baseSequence>-?[0-9]+) " +
            "lastSequence: (?<lastSequence>-?[0-9]+) " +
            "producerId: (?<producerId>-?[0-9]+) " +
            "producerEpoch: (?<producerEpoch>-?[0-9]+) " +
            "partitionLeaderEpoch: (?<partitionLeaderEpoch>[0-9]+) " +
            "isTransactional: (?<isTransactional>true|false) " +
            "isControl: (?<isControl>true|false)" +
            "( deleteHorizonMs: (?<deleteHorizonMs>OptionalLong\\.empty|[0-9]+))? " +
            "position: (?<position>[0-9]+) " +
            "CreateTime: (?<createTime>[0-9]+) " +
            "size: (?<size>[0-9]+) " +
            "magic: (?<magic>[0-9]+) " +
            "compresscodec: (?<compressCodec>none|[A-Z]+) " +
            "crc: (?<crc>[0-9]+) " +
            "isvalid: (?<isValid>true|false)");

    private final static Pattern DATA_RECORD_PATTERN = Pattern.compile("\\| offset: (?<offset>[0-9]+) " +
            "[Cc]reateTime: (?<createTime>[0-9]+) " +
            "key[Ss]ize: (?<keySize>-?[0-9]+) " +
            "value[Ss]ize: (?<valueSize>-?[0-9]+) " +
            "sequence: (?<sequence>-?[0-9]+) " +
            "header[Kk]eys: \\[(?<headerKeys>.*)\\]( payload:.*)?");

    private final static Pattern CONTROL_RECORD_PATTERN = Pattern.compile(DATA_RECORD_PATTERN.pattern() + " " +
            "endTxnMarker: (?<endTxnMarker>COMMIT|ABORT) " +
            "coordinatorEpoch: (?<coordinatorEpoch>[0-9]+)");

    private final static Pattern TRANSACTIONAL_RECORD_PATTERN = Pattern.compile(DATA_RECORD_PATTERN.pattern() + " " +
            "key: transaction_metadata::transactionalId=(?<transactionalId>.*) " +
            "payload: (?<payload>.*)");
    private final static Pattern TRANSACTIONAL_PAYLOAD_PATTERN = Pattern.compile("producerId:(?<producerId>[0-9]+)," +
            "producerEpoch:(?<producerEpoch>[0-9]+)," +
            "state=(?<state>Ongoing|PrepareCommit|PrepareAbort|CompleteCommit|CompleteAbort|Empty|Dead)," +
            "partitions=\\[(?<partitions>.*)\\]," +
            "txnLastUpdateTimestamp=(?<txnLastUpdateTimestamp>[0-9]+)," +
            "txnTimeoutMs=(?<txnTimeoutMs>[0-9]+)");

    public Segment readSegment(File dumpFile) {
        Stream<String> lines;
        try {
            lines = Files.lines(dumpFile.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return readSegment(dumpFile.getName(), lines);
    }

    public Segment readSegment(String dumpFileName, Stream<String> lines) {
        File[] segmentFile = {null};
        int[] lineNumber = {0};
        Spliterator<String> spliterator = lines.spliterator();
        // read first line
        if (!spliterator.tryAdvance(line -> {
            lineNumber[0]++;
            segmentFile[0] = this.readDumpingLine(line);
        })) {
            throw new UnexpectedFileContent("Expected > 0 lines");
        }
        Segment.Type type = segmentType(dumpFileName, segmentFile[0]);
        // ready 2nd line
        if (!spliterator.tryAdvance(line -> {
            lineNumber[0]++;
            this.readStartingOffsetLine(segmentFile[0], line);
        })) {
            throw new UnexpectedFileContent("Expected > 1 lines");
        }
        // we don't know if --deep-iteration was used, so read the next two lines to fine out
        Stream.Builder<String> builder = Stream.builder();
        String[] firstBatchLine = {null};
        if (!spliterator.tryAdvance(line -> {
            lineNumber[0]++;
            firstBatchLine[0] = line;
            builder.add(line);
        })) {
            firstBatchLine[0] = null;
        }
        boolean deepIteration;
        Stream<Batch> batches;
        if (firstBatchLine[0] == null) {
            deepIteration = false;
            batches = Stream.empty();
        } else {
            String[] maybeFirstMessageLine = {null};
            if (!spliterator.tryAdvance(line -> {
                lineNumber[0]++;
                maybeFirstMessageLine[0] = line;
                builder.add(line);
            })) {
                maybeFirstMessageLine[0] = null;
            }
            deepIteration = maybeFirstMessageLine[0] != null && maybeFirstMessageLine[0].startsWith("| ");
            lineNumber[0] -= 2;
            batches = batches(dumpFileName, lineNumber[0], type, deepIteration,
                    Stream.concat(builder.build(), StreamSupport.stream(spliterator, false)));
        }
        return new Segment(dumpFileName, type, topicName(segmentFile[0]), deepIteration, batches);
    }

    private Stream<Batch> batches(String dumpFileName, int lineNum, Segment.Type type, boolean deepIteration, Stream<String> concat) {
        Stream<Batch> batchStream = concat.flatMap(new Function<String, Stream<? extends Batch>>() {
            int lineNumber = lineNum;
            int expect = 0;
            Matcher batchMatcher = null;
            int batchLineNumber = 0;
            BaseMessage[] messages = null;
            int i = 0;
            @Override
            public Stream<? extends Batch> apply(String line) {
                lineNumber++;
                if (expect == 0 || !deepIteration) { // if dumped without --deep-iteration then expect is of no value.
                    batchLineNumber = lineNumber;
                    var matcher = matchBatch(line);
                    int count = parseInt(matcher.group("count"));
                    if (parseBoolean(matcher.group("isControl"))) {
                        expect = -count;
                    } else {
                        expect = count;
                    }
                    i = 0;
                    messages = new BaseMessage[count];
                    batchMatcher = matcher;
                } else if (expect > 0) {
                    if (type == Segment.Type.TRANSACTION_STATE) {
                        messages[i++] = parseTransactionState(line, dumpFileName);
                    } else {
                        messages[i++] = parseData(line, dumpFileName);
                    }
                    expect--;
                } else {
                    messages[i++] = parseControl(line, dumpFileName);
                    expect++;
                }

                if (!deepIteration || expect == 0) {
                    return Stream.of(parseBatch(deepIteration ? List.of(messages) : List.of(), dumpFileName));
                } else {
                    return Stream.empty();
                }
            }

            private Matcher matchBatch(String line) {
                Matcher batchLineMatcher = MESSAGE_SET_PATTERN.matcher(line);
                if (!batchLineMatcher.matches()) {
                    throw new IllegalStateException("Expected a message batch");
                }
                return batchLineMatcher;
            }

            private Batch parseBatch(List<BaseMessage> messages, String dumpFilename) {
                int count = parseInt(batchMatcher.group("count"));
                Batch batch = new Batch(dumpFilename, batchLineNumber,
                        parseLong(batchMatcher.group("baseOffset")),
                        parseLong(batchMatcher.group("lastOffset")),
                        count,
                        parseInt(batchMatcher.group("baseSequence")),
                        parseInt(batchMatcher.group("lastSequence")),
                        parseLong(batchMatcher.group("producerId")),
                        parseShort(batchMatcher.group("producerEpoch")),
                        parseInt(batchMatcher.group("partitionLeaderEpoch")),
                        parseBoolean(batchMatcher.group("isTransactional")),
                        parseBoolean(batchMatcher.group("isControl")),
                        parseLong(batchMatcher.group("position")),
                        parseLong(batchMatcher.group("createTime")),
                        parseInt(batchMatcher.group("size")),
                        Byte.parseByte(batchMatcher.group("magic")),
                        batchMatcher.group("compressCodec"),
                        Integer.parseUnsignedInt(batchMatcher.group("crc")),
                        parseBoolean(batchMatcher.group("isValid")),
                        messages);
                checkBatch(type, batch);
                return batch;
            }

            private BaseMessage parseData(String line,
                                          String dumpFilename) {
                Matcher matcher = DATA_RECORD_PATTERN.matcher(line);
                if (!matcher.matches()) {
                    throw new IllegalStateException("Expected " + (expect) + " data records in batch, but this doesn't look like a data record");
                }
                return new DataMessage(dumpFilename, lineNumber,
                        parseLong(matcher.group("offset")),
                        parseLong(matcher.group("createTime")),
                        parseInt(matcher.group("keySize")),
                        parseInt(matcher.group("valueSize")),
                        parseInt(matcher.group("sequence")),
                        matcher.group("headerKeys"));
            }

            private BaseMessage parseControl(String line,
                                             String dumpFilename) {
                Matcher matcher = CONTROL_RECORD_PATTERN.matcher(line);
                if (!matcher.matches()) {
                    throw new IllegalStateException("Expected " + (-expect) + " control records in batch, but this doesn't look like a control record");
                }
                return new ControlMessage(dumpFilename, lineNumber,
                        parseLong(matcher.group("offset")),
                        parseLong(matcher.group("createTime")),
                        parseInt(matcher.group("keySize")),
                        parseInt(matcher.group("valueSize")),
                        parseInt(matcher.group("sequence")),
                        matcher.group("headerKeys"),
                        matcher.group("endTxnMarker").equals("COMMIT"),
                        parseInt(matcher.group("coordinatorEpoch")));
            }

            private BaseMessage parseTransactionState(String line,
                                                      String dumpFilename) {
                Matcher matcher = TRANSACTIONAL_RECORD_PATTERN.matcher(line);
                if (!matcher.matches()) {
                    throw new IllegalStateException("Expected " + (expect) + " txn records in batch, but this doesn't look like a txn record");
                }
                String payload = matcher.group("payload");
                if (payload.equals("<DELETE>")) {
                    return new TransactionStateDeletion(dumpFilename, lineNumber,
                            parseLong(matcher.group("offset")),
                            parseLong(matcher.group("createTime")),
                            parseInt(matcher.group("keySize")),
                            parseInt(matcher.group("valueSize")),
                            parseInt(matcher.group("sequence")),
                            matcher.group("headerKeys"),
                            matcher.group("transactionalId"));
                } else {
                    Matcher payloadMatcher = TRANSACTIONAL_PAYLOAD_PATTERN.matcher(payload);
                    if (!payloadMatcher.matches()) {
                        throw new UnexpectedFileContent("Didn't match expected pattern");
                    }
                    return new TransactionStateChange(dumpFilename, lineNumber,
                            parseLong(matcher.group("offset")),
                            parseLong(matcher.group("createTime")),
                            parseInt(matcher.group("keySize")),
                            parseInt(matcher.group("valueSize")),
                            parseInt(matcher.group("sequence")),
                            matcher.group("headerKeys"),
                            matcher.group("transactionalId"),
                            parseLong(payloadMatcher.group("producerId")),
                            parseShort(payloadMatcher.group("producerEpoch")),
                            TransactionStateChange.State.valueOf(payloadMatcher.group("state")),
                            payloadMatcher.group("partitions"),
                            parseLong(payloadMatcher.group("txnLastUpdateTimestamp")),
                            parseLong(payloadMatcher.group("txnTimeoutMs")));
                }
            }
        });
        batchStream = batchStream.map(new AssertBatchesValid())
                .map(new AssertBatchPositionMonotonic())
                .map(new AssertLeaderEpochMonotonic());
        if (type == Segment.Type.TRANSACTION_STATE) {
            batchStream = batchStream.map(new AssertTransactionStateMachine())
                    .map(new AssertBatchesTransactional(false));
        }
        return batchStream;
    }

    private void checkBatch(Segment.Type type, Batch batch) {
        if (type == Segment.Type.TRANSACTION_STATE) {
            if (batch.producerId() != -1) {
                throw new UnexpectedFileContent("Segment of __transaction_state with producerId != -1");
            } else if (batch.producerEpoch() != -1) {
                throw new UnexpectedFileContent("Segment of __transaction_state with producerEpoch != -1");
            }
        } else if (type == Segment.Type.DATA) {
            if (batch.isTransactional()) {
                if (batch.producerId() == -1) {
                    throw new UnexpectedFileContent("Transactional batch with producerId == -1");
                } else if (batch.producerEpoch() == -1) {
                    throw new UnexpectedFileContent("Transactional batch with producerEpoch == -1");
                }
            }
        }
    }

    private String topicName(File segmentFile) {
        File parent = segmentFile.getParentFile();
        if (parent != null && parent.getName().matches("[a-zA-Z0-9_.-]+-[0-9]+")) {
            return parent.getName().substring(parent.getName().lastIndexOf("-"));
        }
        return null;
    }

    private Segment.Type segmentType(String dumpFilename, File segmentFile) {
        Segment.Type type;
        File parent = segmentFile.getParentFile();
        if (parent != null) {
            String name = parent.getName();
            type = name.matches(Segment.Type.TRANSACTION_STATE.topicName + "-[0-9]+") ? Segment.Type.TRANSACTION_STATE :
                          name.matches(Segment.Type.CONSUMER_OFFSETS.topicName + "-[0-9]+") ? Segment.Type.CONSUMER_OFFSETS : Segment.Type.DATA;
        } else {
            // Can happen if kafka-dump-log.sh run from the directory containing the segment
            System.err.printf("%s: Don't know original segment file name, assuming a normal segment", dumpFilename);
            type = Segment.Type.DATA;
        }
        return type;
    }

    private long filenameOffset(File segmentFile) {
        String pattern = "[0-9]+\\.log";
        if (!segmentFile.getName().matches(pattern)) {
            throw new UnexpectedFileContent("Expected FILE in first line to match " + pattern);
        }
        return parseLong(segmentFile.getName().substring(0, segmentFile.getName().indexOf('.')));
    }

    private File readDumpingLine(String line) {
        File segmentFile;
        String dumpingPattern = "^Dumping (.*)$";
        var dumpingMatcher = Pattern.compile(dumpingPattern).matcher(line);
        if (!dumpingMatcher.matches()) {
            throw new UnexpectedFileContent("Expected first line to match " + dumpingPattern);
        } else {
            segmentFile = new File(dumpingMatcher.group(1));
        }
        return segmentFile;
    }

    private long readStartingOffsetLine(File segmentFile, String line) {
        String startingOffsetPattern = "^Starting offset: ([0-9]+)$";
        var offsetMatcher = Pattern.compile(startingOffsetPattern).matcher(line);
        if (!offsetMatcher.matches()) {
            throw new UnexpectedFileContent("Expected second line to match " + startingOffsetPattern);
        }
        long startingOffset = parseLong(offsetMatcher.group(1));
        long filenameOffset = filenameOffset(segmentFile);
        if (filenameOffset != startingOffset) {
            throw new UnexpectedFileContent("Segment file name " + segmentFile + " implies starting offset of " + filenameOffset + " but 2nd line says offset is " + startingOffset);
        }
        return startingOffset;
    }



    public static void main(String[] args) {
        SegmentDumpReader segmentDumpReader = new SegmentDumpReader();
        // Sort to get into offset order
        Arrays.stream(args).map(File::new).map(dumpFile ->
                segmentDumpReader.readSegment(dumpFile).batches().collect(TransactionalInfoCollector.collector())).forEach(x -> {
            System.out.println("First batch: " + x.firstBatch());
            x.emptyTransactions().forEach(txn -> System.out.println("Empty txn: " + txn));
            System.out.println("Last batch: " + x.lastBatch());
            x.openTransactions().forEach((sess, txn) -> System.out.println("Open transaction: " + sess + "->" + txn));
            System.out.println("#committed: " + x.numTransactionalCommit());
            System.out.println("#aborted: " + x.numTransactionalAbort());
            System.out.println("Txn sizes: " + x.txnSizeStats());
            System.out.println("Txn durations(ms): " + x.txnDurationStats());
        });
    }

}
