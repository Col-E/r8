// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.partition;

import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.retrace.PartitionedToProguardMappingConverter;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.internal.MappingPartitionKeyStrategy;
import com.android.tools.r8.retrace.internal.ProguardMapPartitionerOnClassNameToText.ProguardMapPartitionerBuilderImplInternal;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetracePartitionAndJoinIdentityTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public RetracePartitionAndJoinIdentityTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testPartitionAndJoin() throws Exception {
    Path mappingFile =
        ToolHelper.RETRACE_MAPS_DIR.resolve(
            "ad5c3e88ef2bae5ef324eb225fbc57345cd57863-r8lib.jar.map");
    ProguardMapProducer proguardMapProducer = ProguardMapProducer.fromPath(mappingFile);
    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
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
    diagnosticsHandler.assertNoMessages();

    StringBuilder builder = new StringBuilder();
    PartitionedToProguardMappingConverter.builder()
        .setMetadata(metadataData.getBytes())
        .setDiagnosticsHandler(diagnosticsHandler)
        .setConsumer((string, handler) -> builder.append(string))
        .setPartitionSupplier(partitions::get)
        .build()
        .run();
    List<String> joinedMapLines = StringUtils.splitLines(builder.toString());
    // TODO(b/274735214): Partitioning does not capture the preamble of the mapping file yet, so we
    //  discard it before checking equality.
    List<String> lines = Files.readAllLines(mappingFile);
    List<String> filteredLines = lines.subList(computeFirstLine(lines), lines.size());
    assertListsAreEqual(filteredLines, joinedMapLines);
  }

  private int computeFirstLine(List<String> lines) {
    int firstLine = 0;
    for (int i = 0; i < lines.size(); i++) {
      String currentLine = lines.get(i).trim();
      if (!currentLine.startsWith("#")
          && currentLine.contains(" -> ")
          && currentLine.endsWith(":")) {
        firstLine = i;
        break;
      }
    }
    return firstLine;
  }
}
