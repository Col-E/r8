// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.retrace.PartitionMappingSupplier;
import com.android.tools.r8.retrace.ProguardMapPartitioner;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.RetraceFrameElement;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.Retracer;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RetracePartitionRoundTripInlineTest extends RetraceApiTestBase {

  public RetracePartitionRoundTripInlineTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  protected Class<? extends RetraceApiBinaryTest> binaryTestClass() {
    return ApiTest.class;
  }

  public static class ApiTest implements RetraceApiBinaryTest {

    private final ClassReference callerOriginal = Reference.classFromTypeName("some.Class");
    private final ClassReference callerRenamed = Reference.classFromTypeName("b");

    private final String mapping =
        "# { id: 'com.android.tools.r8.mapping', version: '2.0' }\n"
            + "inlinee.Class -> inlinee.Class:\n"
            + " # {'id':'sourceFile','fileName':'InlineeClass.kt'}\n"
            + callerOriginal.getTypeName()
            + " -> "
            + callerRenamed.getTypeName()
            + ":\n"
            + " # {'id':'sourceFile','fileName':'CallerClass.kt'}\n"
            + "  1:1:void inlinee.Class.bar():42:42 -> a\n"
            + "  1:1:void foo():43 -> a\n";

    private int prepareCounter = 0;

    @Test
    public void test() throws IOException {
      ProguardMapProducer proguardMapProducer = ProguardMapProducer.fromString(mapping);
      DiagnosticsHandler diagnosticsHandler = new DiagnosticsHandler() {};
      Map<String, byte[]> partitions = new HashMap<>();
      MappingPartitionMetadata metadataData =
          ProguardMapPartitioner.builder(diagnosticsHandler)
              .setProguardMapProducer(proguardMapProducer)
              .setPartitionConsumer(
                  partition -> partitions.put(partition.getKey(), partition.getPayload()))
              .build()
              .run();
      assertNotNull(metadataData);
      assertEquals(2, partitions.size());

      Set<String> preFetchedKeys = new LinkedHashSet<>();
      PartitionMappingSupplier mappingSupplier =
          PartitionMappingSupplier.builder()
              .setMetadata(metadataData.getBytes())
              .setRegisterMappingPartitionCallback(preFetchedKeys::add)
              .setPrepareMappingPartitionsCallback(() -> prepareCounter++)
              .setMappingPartitionFromKeySupplier(
                  key -> {
                    assertTrue(preFetchedKeys.contains(key));
                    assertTrue(partitions.containsKey(key));
                    return partitions.get(key);
                  })
              .build();
      assertEquals(0, prepareCounter);
      mappingSupplier.registerClassUse(diagnosticsHandler, callerRenamed);
      Retracer retracer = mappingSupplier.createRetracer(diagnosticsHandler);
      List<RetraceFrameElement> callerRetraced =
          retracer
              .retraceFrame(
                  RetraceStackTraceContext.empty(),
                  OptionalInt.of(1),
                  Reference.methodFromDescriptor(callerRenamed, "a", "()V"))
              .stream()
              .collect(Collectors.toList());
      // The retrace result should not be ambiguous or empty.
      assertEquals(1, callerRetraced.size());
      assertEquals(1, preFetchedKeys.size());
      assertEquals(1, prepareCounter);
      RetraceFrameElement retraceFrameElement = callerRetraced.get(0);

      // Check that visiting all frames report all source files.
      List<String> allSourceFiles =
          retraceFrameElement.stream()
              .map(x -> x.getSourceFile().getOrInferSourceFile(""))
              .collect(Collectors.toList());
      assertEquals(Arrays.asList("InlineeClass.kt", "CallerClass.kt"), allSourceFiles);
    }
  }
}
