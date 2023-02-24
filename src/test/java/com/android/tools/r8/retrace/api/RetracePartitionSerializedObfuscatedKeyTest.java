// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.retrace.PartitionMappingSupplier;
import com.android.tools.r8.retrace.Retrace;
import com.android.tools.r8.retrace.RetraceCommand;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RetracePartitionSerializedObfuscatedKeyTest extends RetraceApiTestBase {

  public RetracePartitionSerializedObfuscatedKeyTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  protected Class<? extends RetraceApiBinaryTest> binaryTestClass() {
    return ApiTest.class;
  }

  public static class ApiTest implements RetraceApiBinaryTest {

    private final String mapping = "com.R8 -> a:\n" + "  void m() -> b\n";

    // Serialized data from ObfuscatedTypeNameAsKeyMetadata with Map Version 1.0.
    private final byte[] metadata = new byte[] {0, 0, 49, 46, 48};

    private static final String minifiedStackTraceLine = " at a.b()";

    private static final String retracedStackTraceLine = " at com.R8.m(R8.java)";

    @Test
    public void test() {
      PartitionMappingSupplier mappingSupplier =
          PartitionMappingSupplier.builder()
              .setMetadata(metadata)
              .setMappingPartitionFromKeySupplier(
                  key -> {
                    assertEquals("a", key);
                    return mapping.getBytes(StandardCharsets.UTF_8);
                  })
              .build();

      List<String> stackTrace = new ArrayList<>();
      stackTrace.add(minifiedStackTraceLine);

      Retrace.run(
          RetraceCommand.builder()
              .setMappingSupplier(mappingSupplier)
              .setStackTrace(stackTrace)
              .setRetracedStackTraceConsumer(
                  retraced -> {
                    assertEquals(1, retraced.size());
                    assertEquals(retracedStackTraceLine, retraced.get(0));
                  })
              .build());
    }
  }
}
