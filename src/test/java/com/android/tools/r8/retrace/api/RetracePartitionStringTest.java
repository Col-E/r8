// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.retrace.ProguardMapPartitioner;
import com.android.tools.r8.retrace.ProguardMapProducer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RetracePartitionStringTest extends RetraceApiTestBase {

  public RetracePartitionStringTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  protected Class<? extends RetraceApiBinaryTest> binaryTestClass() {
    return ApiTest.class;
  }

  public static class ApiTest implements RetraceApiBinaryTest {

    private final String aMapping =
        "com.foo.bar.baz -> a:\n"
            + "# someCommentHere\n"
            + "  int field -> c\n"
            + "  void method() -> d";

    private final String bMapping =
        "com.android.google.r8 -> b:\n"
            + " boolean otherField -> e\n"
            + "  int otherMethod() -> f\n"
            + "# otherCommentHere";

    private final String header = "# { id: 'com.android.tools.r8.mapping', version: '2.0' }";

    private final String mapping = header + "\n" + aMapping + "\n" + bMapping;

    @Test
    public void test() throws IOException {
      ProguardMapProducer proguardMapProducer = ProguardMapProducer.fromString(mapping);
      Map<String, String> partitions = new HashMap<>();
      MappingPartitionMetadata metadataData =
          ProguardMapPartitioner.builder(new DiagnosticsHandler() {})
              .setProguardMapProducer(proguardMapProducer)
              .setPartitionConsumer(
                  partition ->
                      partitions.put(
                          partition.getKey(),
                          new String(partition.getPayload(), StandardCharsets.UTF_8)))
              .build()
              .run();
      assertNotNull(metadataData);
      assertEquals(2, partitions.size());
      assertEquals(aMapping, partitions.get("a"));
      assertEquals(bMapping, partitions.get("b"));
    }
  }
}
