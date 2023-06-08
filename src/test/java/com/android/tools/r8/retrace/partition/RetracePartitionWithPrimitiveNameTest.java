// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.partition;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.retrace.PartitionMappingSupplier;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.RetraceOptions;
import com.android.tools.r8.retrace.RetraceStackFrameAmbiguousResultWithContext;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.StringRetrace;
import com.android.tools.r8.retrace.internal.MappingPartitionKeyStrategy;
import com.android.tools.r8.retrace.internal.ProguardMapPartitionerOnClassNameToText.ProguardMapPartitionerBuilderImplInternal;
import com.android.tools.r8.utils.StringUtils;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/286001537. */
@RunWith(Parameterized.class)
public class RetracePartitionWithPrimitiveNameTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public RetracePartitionWithPrimitiveNameTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  public final String mapping =
      StringUtils.unixLines(
          "# { id: 'com.android.tools.r8.mapping', version: '2.0' }",
          "some.class1 -> int:",
          "  void method() -> a");

  @Test
  public void testPartitionAndRetrace() throws Exception {
    ProguardMapProducer proguardMapProducer = ProguardMapProducer.fromString(mapping);
    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    Map<String, byte[]> partitions = new HashMap<>();
    MappingPartitionMetadata metadata =
        new ProguardMapPartitionerBuilderImplInternal(diagnosticsHandler)
            .setMappingPartitionKeyStrategy(MappingPartitionKeyStrategy.getDefaultStrategy())
            .setProguardMapProducer(proguardMapProducer)
            .setPartitionConsumer(
                partition -> partitions.put(partition.getKey(), partition.getPayload()))
            .build()
            .run();

    PartitionMappingSupplier mappingSupplier =
        PartitionMappingSupplier.builder()
            .setMetadata(metadata.getBytes())
            .setMappingPartitionFromKeySupplier(partitions::get)
            .build();

    StringRetrace retracer =
        StringRetrace.create(RetraceOptions.builder().setMappingSupplier(mappingSupplier).build());
    RetraceStackFrameAmbiguousResultWithContext<String> result =
        retracer.retraceFrame("  at int.a()", RetraceStackTraceContext.empty());
    StringBuilder sb = new StringBuilder();
    result.forEach(frames -> frames.forEach(sb::append));
    assertEquals("  at some.class1.method(class1.java)", sb.toString());
  }
}
