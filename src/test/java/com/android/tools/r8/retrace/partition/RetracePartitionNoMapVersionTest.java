// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.partition;

import static org.junit.Assert.assertThrows;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.retrace.ProguardMapPartitioner;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// This is a reproduction of b/296030714.
@RunWith(Parameterized.class)
public class RetracePartitionNoMapVersionTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public RetracePartitionNoMapVersionTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private static final String MAPPING =
      StringUtils.unixLines(
          "foo.bar.baz -> K3:",
          "    void org.internal.class.<init>() -> <init>",
          "      # {'id':'com.android.tools.r8.synthesized'}",
          "    1:1:void org.internal.class.build():0:0 -> a",
          "      # {'id':'com.android.tools.r8.synthesized'}",
          "      # {'id':'com.android.tools.r8.outlineCallsite','positions':{'1':2,'2':3,'3':4}}");

  @Test
  public void test() throws IOException {
    // TODO(b/296030714): Should be fixed if moving UNKNOWN below a known version in the lattice.
    assertThrows(
        CompilationError.class,
        () ->
            ProguardMapPartitioner.builder(new DiagnosticsHandler() {})
                .setProguardMapProducer(ProguardMapProducer.fromString(MAPPING))
                .setPartitionConsumer(partition -> {})
                .build()
                .run());
  }
}
