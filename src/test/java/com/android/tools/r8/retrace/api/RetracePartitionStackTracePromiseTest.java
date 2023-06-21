// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowingFunction;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.retrace.PartitionMappingSupplierAsync;
import com.android.tools.r8.retrace.ProguardMapPartitioner;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.RetraceAsync;
import com.android.tools.r8.retrace.RetraceAsyncResult;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetraceStackTraceResult;
import com.android.tools.r8.retrace.api.RetraceApiTypeResultTest.RetracePartitionStackTraceTest;
import com.android.tools.r8.retrace.api.RetraceApiTypeResultTest.RetracePartitionStackTraceTest.IdentityStackTraceLineParser;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetracePartitionStackTracePromiseTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public RetracePartitionStackTracePromiseTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private final String MAPPING =
      StringUtils.unixLines(
          "com.Foo -> a:",
          "  1:1:void m1():42:42 -> a",
          "com.Bar -> b:",
          "  2:2:void m2():43:43 -> b",
          "com.Baz -> c:",
          "  3:3:void m3():44:44 -> c");

  public static class Promise<T> {

    private final T value;

    private Promise(T value) {
      this.value = value;
    }

    public <R> Promise<R> then(ThrowingFunction<T, R, InterruptedException> f)
        throws InterruptedException {
      return new Promise<>(f.apply(value));
    }

    public <R> Promise<R> thenApply(ThrowingFunction<T, Promise<R>, InterruptedException> f)
        throws InterruptedException {
      return f.apply(value);
    }
  }

  public static class PromiseRunner<T> {

    public T run(Promise<T> promise) {
      // Here we are cheating since we know the value is always available.
      return promise.value;
    }
  }

  public static class Storage {

    private final byte[] metadata;
    private final Map<String, byte[]> partitions;

    public Storage(byte[] metadata, Map<String, byte[]> partitions) {
      this.metadata = metadata;
      this.partitions = partitions;
    }

    public Promise<byte[]> getMetadata() {
      return new Promise<>(metadata);
    }

    public Promise<byte[]> getPartition(String key) {
      return new Promise<>(partitions.getOrDefault(key, new byte[0]));
    }
  }

  private Storage buildStorage(TestDiagnosticMessagesImpl diagnosticMessages) throws Exception {
    Map<String, byte[]> partitions = new HashMap<>();
    MappingPartitionMetadata metadataPromise =
        ProguardMapPartitioner.builder(diagnosticMessages)
            .setProguardMapProducer(ProguardMapProducer.fromString(MAPPING))
            .setPartitionConsumer(
                partition -> partitions.put(partition.getKey(), partition.getPayload()))
            .build()
            .run();
    return new Storage(metadataPromise.getBytes(), partitions);
  }

  private static <T, R> Promise<Map<T, R>> transpose(Map<T, Promise<R>> promiseMap)
      throws InterruptedException {
    Map<T, R> resolvedMap = new HashMap<>();
    Box<InterruptedException> interruptedException = new Box<>();
    promiseMap.forEach(
        (key, promise) -> {
          try {
            promise.then(resolvedValue -> resolvedMap.put(key, resolvedValue));
          } catch (InterruptedException e) {
            interruptedException.set(e);
          }
        });
    if (interruptedException.isSet()) {
      throw interruptedException.get();
    }
    return new Promise<>(resolvedMap);
  }

  @Test
  public void testRetrace() throws Exception {
    TestDiagnosticMessagesImpl diagnosticMessages = new TestDiagnosticMessagesImpl();
    Storage storage = buildStorage(diagnosticMessages);

    List<StackTraceLine> minifiedStackTrace = new ArrayList<>();
    minifiedStackTrace.add(StackTraceLine.parse("at a.a(SourceFile:1)"));
    minifiedStackTrace.add(StackTraceLine.parse("at b.b(SourceFile:2)"));
    minifiedStackTrace.add(StackTraceLine.parse("at c.c(SourceFile:3)"));

    StackTrace retracedStacktrace =
        new PromiseRunner<StackTrace>()
            .run(
                storage
                    .getMetadata()
                    .thenApply(
                        metadata -> {
                          Map<String, Promise<byte[]>> partitionRequests = new HashMap<>();
                          RetraceAsyncResult<RetraceStackTraceResult<StackTraceLine>> asyncResult =
                              RetraceAsync
                                  .<StackTraceLine,
                                      RetracePartitionStackTraceTest.StackTraceLineProxy>
                                      builder()
                                  .setStackTraceLineParser(new IdentityStackTraceLineParser())
                                  .setDiagnosticsHandler(diagnosticMessages)
                                  .setMappingSupplier(
                                      PartitionMappingSupplierAsync.builder()
                                          .setMetadata(metadata)
                                          .setRegisterMappingPartitionCallback(
                                              key ->
                                                  partitionRequests.put(
                                                      key, storage.getPartition(key)))
                                          .build())
                                  .build()
                                  .retraceStackTrace(
                                      minifiedStackTrace, RetraceStackTraceContext.empty());
                          return getThen(partitionRequests, asyncResult);
                        }));
    StackTrace expectedStackTrace =
        StackTrace.builder()
            .add(
                StackTraceLine.builder()
                    .setClassName("com.Foo")
                    .setMethodName("m1")
                    .setFileName("Foo.java")
                    .setLineNumber(42)
                    .build())
            .add(
                StackTraceLine.builder()
                    .setClassName("com.Bar")
                    .setMethodName("m2")
                    .setFileName("Bar.java")
                    .setLineNumber(43)
                    .build())
            .add(
                StackTraceLine.builder()
                    .setClassName("com.Baz")
                    .setMethodName("m3")
                    .setFileName("Baz.java")
                    .setLineNumber(44)
                    .build())
            .build();
    assertThat(retracedStacktrace, isSame(expectedStackTrace));
  }

  // This method needs to be outlined due to a bug in javac 8.
  private Promise<StackTrace> getThen(
      Map<String, Promise<byte[]>> partitionRequests,
      RetraceAsyncResult<RetraceStackTraceResult<StackTraceLine>> asyncResult)
      throws InterruptedException {
    return transpose(partitionRequests)
        .then(
            resolvedPartitions -> {
              StackTrace.Builder retraceStackTraceBuilder = StackTrace.builder();
              asyncResult
                  .getResult(resolvedPartitions::get)
                  .forEach(
                      retraced -> {
                        Assert.assertEquals(1, retraced.size());
                        retraced.get(0).forEach(retraceStackTraceBuilder::add);
                      });
              return retraceStackTraceBuilder.build();
            });
  }
}
