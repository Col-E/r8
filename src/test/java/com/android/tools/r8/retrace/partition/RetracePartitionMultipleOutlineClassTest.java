// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.partition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.retrace.PartitionMappingSupplier;
import com.android.tools.r8.retrace.ProguardMapPartitioner;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.RetraceFrameElement;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.Retracer;
import com.android.tools.r8.utils.StringUtils;
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
import org.junit.runners.Parameterized.Parameters;

/**
 * This tests that we can join multiple class mappings with the same name if they are created by the
 * partitioning scheme. To ensure proper retracing of filenames, the partitioner will ensure that a
 * partition having inline frames to another class will contain a class-mapping with source-file
 * mapping. Potentially the inlined class references will still have a mapping.
 */
@RunWith(Parameterized.class)
public class RetracePartitionMultipleOutlineClassTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public RetracePartitionMultipleOutlineClassTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private final ClassReference inlineOriginal = Reference.classFromTypeName("inlinee.Class");
  private final ClassReference inlineRenamed = Reference.classFromTypeName("a");
  private final ClassReference callerSomeClassOriginal = Reference.classFromTypeName("some.Class");
  private final ClassReference callerSomeClassRenamed = Reference.classFromTypeName("b");
  private final ClassReference callerOtherClassOriginal =
      Reference.classFromTypeName("other.Class");
  private final ClassReference callerOtherClassRenamed = Reference.classFromTypeName("c");
  private final String mappingInline =
      StringUtils.unixLines(
          "# { id: 'com.android.tools.r8.mapping', version: '2.0' }",
          inlineOriginal.getTypeName() + " -> " + inlineRenamed.getTypeName() + ":",
          "  # {'id':'sourceFile','fileName':'InlineeClass.kt'}",
          "  void foo() -> a");
  private final String mappingSomeClass =
      StringUtils.unixLines(
          callerSomeClassOriginal.getTypeName()
              + " -> "
              + callerSomeClassRenamed.getTypeName()
              + ":",
          " # {'id':'sourceFile','fileName':'CallerClass.kt'}",
          "  1:1:void inlinee.Class.bar():42:42 -> a",
          "  1:1:void foo():43 -> a");
  private final String mappingOtherClass =
      StringUtils.unixLines(
          callerOtherClassOriginal.getTypeName()
              + " -> "
              + callerOtherClassRenamed.getTypeName()
              + ":",
          " # {'id':'sourceFile','fileName':'OtherClass.kt'}",
          "  1:1:void inlinee.Class.bar():42:42 -> a",
          "  1:1:void foo():43 -> a");

  private int prepareCounter = 0;

  @Test
  public void test() throws IOException {
    ProguardMapProducer proguardMapProducer =
        ProguardMapProducer.fromString(mappingInline + mappingSomeClass + mappingOtherClass);
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
    assertEquals(3, partitions.size());

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
    // Load all classes to ensure we build a mapping containing all.
    mappingSupplier.registerClassUse(diagnosticsHandler, callerSomeClassRenamed);
    mappingSupplier.registerClassUse(diagnosticsHandler, callerOtherClassRenamed);
    mappingSupplier.registerClassUse(diagnosticsHandler, inlineRenamed);
    // Add a redundant call to an existing mapping to ensure that will not incorrectly load the
    // data twice.
    mappingSupplier.registerClassUse(diagnosticsHandler, inlineRenamed);
    assertEquals(3, preFetchedKeys.size());
    Retracer retracer = mappingSupplier.createRetracer(diagnosticsHandler);
    List<RetraceFrameElement> callerRetraced =
        retracer
            .retraceFrame(
                RetraceStackTraceContext.empty(),
                OptionalInt.of(1),
                Reference.methodFromDescriptor(callerSomeClassRenamed, "a", "()V"))
            .stream()
            .collect(Collectors.toList());
    // The retrace result should not be ambiguous or empty.
    assertEquals(1, callerRetraced.size());
    RetraceFrameElement retraceFrameElement = callerRetraced.get(0);

    // Check that visiting all frames report all source files.
    List<String> allSourceFiles =
        retraceFrameElement.stream()
            .map(x -> x.getSourceFile().getOrInferSourceFile(""))
            .collect(Collectors.toList());
    assertEquals(Arrays.asList("InlineeClass.kt", "CallerClass.kt"), allSourceFiles);
  }
}
