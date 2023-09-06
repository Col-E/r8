// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.partition;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.MappingPartitionFromKeySupplier;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.retrace.PartitionMappingSupplier;
import com.android.tools.r8.retrace.ProguardMapPartitioner;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetracedMethodReference;
import com.android.tools.r8.retrace.RetracedSingleFrame;
import com.android.tools.r8.retrace.Retracer;
import com.android.tools.r8.retrace.internal.MappingPartitionMetadataInternal;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
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
    Map<String, byte[]> partitions = new HashMap<>();
    DiagnosticsHandler diagnosticsHandler = new DiagnosticsHandler() {};
    MappingPartitionMetadata metadata =
        ProguardMapPartitioner.builder(diagnosticsHandler)
            .setProguardMapProducer(ProguardMapProducer.fromString(MAPPING))
            .setPartitionConsumer(
                partition -> partitions.put(partition.getKey(), partition.getPayload()))
            .build()
            .run();
    // Retracing with the original metadata not having a version will not make us read additional
    // mapping information.
    retraceWithMetadata(metadata.getBytes(), partitions::get, false);
    // Retracing with metadata setting a version will let us read the metadata. This shows that we
    // keep the additional information when partitioning.
    retraceWithMetadata(
        MappingPartitionMetadataInternal.ObfuscatedTypeNameAsKeyMetadata.create(
                MapVersion.MAP_VERSION_2_0)
            .getBytes(),
        partitions::get,
        true);
  }

  private void retraceWithMetadata(
      byte[] metadata, MappingPartitionFromKeySupplier supplier, boolean expectSynthetic) {
    DiagnosticsHandler diagnosticsHandler = new DiagnosticsHandler() {};
    ClassReference k3Class = Reference.classFromTypeName("K3");
    Retracer retracer =
        PartitionMappingSupplier.builder()
            .setMetadata(metadata)
            .setMappingPartitionFromKeySupplier(supplier)
            .build()
            .registerClassUse(diagnosticsHandler, k3Class)
            .createRetracer(diagnosticsHandler);
    List<RetracedMethodReference> allRetracedFrames =
        retracer
            .retraceFrame(
                RetraceStackTraceContext.empty(),
                OptionalInt.of(1),
                Reference.methodFromDescriptor(k3Class, "a", "()V"))
            .stream()
            .flatMap(
                frameElement -> {
                  assertEquals(expectSynthetic, frameElement.isCompilerSynthesized());
                  return frameElement.stream().map(RetracedSingleFrame::getMethodReference);
                })
            .collect(Collectors.toList());
    assertEquals(1, allRetracedFrames.size());
    assertEquals("build", allRetracedFrames.get(0).getMethodName());
  }
}
