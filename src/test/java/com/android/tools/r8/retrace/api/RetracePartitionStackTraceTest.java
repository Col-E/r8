// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.retrace.PartitionMappingSupplier;
import com.android.tools.r8.retrace.ProguardMapPartitioner;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.Retrace;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetraceStackTraceElementProxy;
import com.android.tools.r8.retrace.RetracedMethodReference.KnownRetracedMethodReference;
import com.android.tools.r8.retrace.StackTraceElementProxy;
import com.android.tools.r8.retrace.StackTraceLineParser;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetracePartitionStackTraceTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public RetracePartitionStackTraceTest(TestParameters parameters) {
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

  @Test
  public void testRetrace() throws Exception {
    TestDiagnosticMessagesImpl diagnosticMessages = new TestDiagnosticMessagesImpl();
    Map<String, byte[]> partitions = new HashMap<>();
    MappingPartitionMetadata metadata =
        ProguardMapPartitioner.builder(diagnosticMessages)
            .setProguardMapProducer(ProguardMapProducer.fromString(MAPPING))
            .setPartitionConsumer(
                partition -> partitions.put(partition.getKey(), partition.getPayload()))
            .build()
            .run();

    Set<String> requestedKeys = new HashSet<>();
    BooleanBox setPartitionCallBack = new BooleanBox();
    PartitionMappingSupplier mappingSupplier =
        PartitionMappingSupplier.builder()
            .setMetadata(metadata.getBytes())
            .setRegisterMappingPartitionCallback(requestedKeys::add)
            .setPrepareMappingPartitionsCallback(setPartitionCallBack::set)
            .setMappingPartitionFromKeySupplier(partitions::get)
            .build();

    Retrace<StackTraceLine, StackTraceLineProxy> retrace =
        Retrace.<StackTraceLine, StackTraceLineProxy>builder()
            .setMappingSupplier(mappingSupplier)
            .setStackTraceLineParser(StackTraceLineProxy::new)
            .setDiagnosticsHandler(diagnosticMessages)
            .build();

    List<StackTraceLine> minifiedStackTrace = new ArrayList<>();
    minifiedStackTrace.add(StackTraceLine.parse("at a.a(SourceFile:1)"));
    minifiedStackTrace.add(StackTraceLine.parse("at b.b(SourceFile:2)"));
    minifiedStackTrace.add(StackTraceLine.parse("at c.c(SourceFile:3)"));
    StackTrace.Builder retraceStackTraceBuilder = StackTrace.builder();
    retrace
        .retraceStackTrace(minifiedStackTrace, RetraceStackTraceContext.empty())
        .forEach(
            stackTraceLines -> {
              assertEquals(1, stackTraceLines.size());
              stackTraceLines.get(0).forEach(retraceStackTraceBuilder::add);
            });
    assertEquals(partitions.keySet(), requestedKeys);
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
    assertThat(expectedStackTrace, isSame(retraceStackTraceBuilder.build()));
  }

  public static class IdentityStackTraceLineParser
      implements StackTraceLineParser<StackTraceLine, StackTraceLineProxy> {

    @Override
    public StackTraceLineProxy parse(StackTraceLine stackTraceLine) {
      return new StackTraceLineProxy(stackTraceLine);
    }
  }

  public static class StackTraceLineProxy
      extends StackTraceElementProxy<StackTraceLine, StackTraceLineProxy> {

    private final StackTraceLine stackTraceLine;

    public StackTraceLineProxy(StackTraceLine stackTraceLine) {
      this.stackTraceLine = stackTraceLine;
    }

    @Override
    public boolean hasClassName() {
      return true;
    }

    @Override
    public boolean hasMethodName() {
      return true;
    }

    @Override
    public boolean hasSourceFile() {
      return true;
    }

    @Override
    public boolean hasLineNumber() {
      return true;
    }

    @Override
    public boolean hasFieldName() {
      return false;
    }

    @Override
    public boolean hasFieldOrReturnType() {
      return false;
    }

    @Override
    public boolean hasMethodArguments() {
      return false;
    }

    @Override
    public ClassReference getClassReference() {
      return Reference.classFromTypeName(stackTraceLine.className);
    }

    @Override
    public String getMethodName() {
      return stackTraceLine.methodName;
    }

    @Override
    public String getSourceFile() {
      return stackTraceLine.fileName;
    }

    @Override
    public int getLineNumber() {
      return stackTraceLine.lineNumber;
    }

    @Override
    public String getFieldName() {
      return null;
    }

    @Override
    public String getFieldOrReturnType() {
      return null;
    }

    @Override
    public String getMethodArguments() {
      return null;
    }

    @Override
    public StackTraceLine toRetracedItem(
        RetraceStackTraceElementProxy<StackTraceLine, StackTraceLineProxy> retracedProxy,
        boolean verbose) {
      KnownRetracedMethodReference knownRetracedMethodReference =
          retracedProxy.getRetracedMethod().asKnown();
      return new StackTraceLine(
          stackTraceLine.toString(),
          knownRetracedMethodReference.getMethodReference().getHolderClass().getTypeName(),
          knownRetracedMethodReference.getMethodName(),
          retracedProxy.getSourceFile(),
          knownRetracedMethodReference.getOriginalPositionOrDefault(0));
    }
  }
}
