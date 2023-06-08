// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.partition;

import static org.junit.Assert.assertThrows;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.internal.MappingPartitionKeyStrategy;
import com.android.tools.r8.retrace.internal.ProguardMapPartitionerOnClassNameToText.ProguardMapPartitionerBuilderImplInternal;
import com.android.tools.r8.utils.StringUtils;
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
          "  void field -> a");

  @Test
  public void testPartitionAndRetrace() {
    ProguardMapProducer proguardMapProducer = ProguardMapProducer.fromString(mapping);
    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    // TODO(b/286001537): We need to handle minified names matching primitive types.
    assertThrows(
        AssertionError.class,
        () ->
            new ProguardMapPartitionerBuilderImplInternal(diagnosticsHandler)
                .setMappingPartitionKeyStrategy(MappingPartitionKeyStrategy.getDefaultStrategy())
                .setProguardMapProducer(proguardMapProducer)
                .setPartitionConsumer(partition -> {})
                .build()
                .run());
  }
}
