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
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.internal.MappingPartitionKeyStrategy;
import com.android.tools.r8.retrace.internal.MappingPartitionMetadataInternal;
import com.android.tools.r8.retrace.internal.ProguardMapPartitionerOnClassNameToText.ProguardMapPartitionerBuilderImplInternal;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetracePartitionMetadataPartitionNamesTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public RetracePartitionMetadataPartitionNamesTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  public ClassReference obfuscatedClass1 = Reference.classFromTypeName("a.a");
  public ClassReference obfuscatedClass2 = Reference.classFromTypeName("b.b");
  public ClassReference obfuscatedClass3 = Reference.classFromTypeName("c.c");

  public String mapping =
      StringUtils.unixLines(
          "# { id: 'com.android.tools.r8.mapping', version: '2.0' }",
          "some.class1 -> " + obfuscatedClass1.getTypeName() + ":",
          "  void field -> a",
          "some.class2 -> " + obfuscatedClass2.getTypeName() + ":",
          "  void field -> a",
          "some.class3 -> " + obfuscatedClass3.getTypeName() + ":",
          "  void field -> a");

  @Test
  public void test() throws Exception {
    List<String> expectedPartitionKeys = new ArrayList<>();
    expectedPartitionKeys.add(obfuscatedClass1.getTypeName());
    expectedPartitionKeys.add(obfuscatedClass2.getTypeName());
    expectedPartitionKeys.add(obfuscatedClass3.getTypeName());

    ProguardMapProducer proguardMapProducer = ProguardMapProducer.fromString(mapping);
    DiagnosticsHandler diagnosticsHandler = new DiagnosticsHandler() {};
    Map<String, byte[]> partitions = new HashMap<>();
    MappingPartitionMetadata metadataData =
        new ProguardMapPartitionerBuilderImplInternal(diagnosticsHandler)
            .setMappingPartitionKeyStrategy(
                MappingPartitionKeyStrategy.OBFUSCATED_TYPE_NAME_AS_KEY_WITH_PARTITIONS)
            .setProguardMapProducer(proguardMapProducer)
            .setPartitionConsumer(
                partition -> partitions.put(partition.getKey(), partition.getPayload()))
            .build()
            .run();
    assertNotNull(metadataData);
    assertEquals(new HashSet<>(expectedPartitionKeys), partitions.keySet());

    byte[] bytes = metadataData.getBytes();
    MappingPartitionMetadataInternal mappingPartitionMetadata =
        MappingPartitionMetadataInternal.deserialize(
            bytes, MapVersion.MAP_VERSION_NONE, diagnosticsHandler);
    assertTrue(mappingPartitionMetadata.canGetPartitionKeys());
    assertEquals(expectedPartitionKeys, mappingPartitionMetadata.getPartitionKeys());
  }
}
