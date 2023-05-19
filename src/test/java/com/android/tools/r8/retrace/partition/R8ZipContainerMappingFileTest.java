// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.partition;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.PartitionMapConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.retrace.MappingPartition;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.retrace.PartitionMappingSupplier;
import com.android.tools.r8.retrace.partition.testclasses.R8ZipContainerMappingFileTestClasses;
import com.android.tools.r8.retrace.partition.testclasses.R8ZipContainerMappingFileTestClasses.Main;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8ZipContainerMappingFileTest extends TestBase {

  private static final String SOURCE_FILE = "R8ZipContainerMappingFileTestClasses.java";

  private final StackTrace EXPECTED =
      StackTrace.builder()
          .add(
              StackTraceLine.builder()
                  .setClassName(typeName(R8ZipContainerMappingFileTestClasses.Thrower.class))
                  .setMethodName("throwError")
                  .setFileName(SOURCE_FILE)
                  .setLineNumber(13)
                  .build())
          .add(
              StackTraceLine.builder()
                  .setClassName(typeName(R8ZipContainerMappingFileTestClasses.Main.class))
                  .setMethodName("main")
                  .setFileName(SOURCE_FILE)
                  .setLineNumber(21)
                  .build())
          .build();

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(R8ZipContainerMappingFileTestClasses.class)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .inspectStackTrace(
            stackTrace -> {
              assertThat(stackTrace, isSame(EXPECTED));
            });
  }

  @Test
  public void testR8() throws Exception {
    Path pgMapFile = temp.newFile("mapping.zip").toPath();
    DiagnosticsHandler diagnosticsHandler = new TestDiagnosticMessagesImpl();
    StackTrace originalStackTrace =
        testForR8(parameters.getBackend())
            .addInnerClasses(R8ZipContainerMappingFileTestClasses.class)
            .setMinApi(parameters)
            .addKeepMainRule(Main.class)
            .addKeepAttributeSourceFile()
            .addKeepAttributeLineNumberTable()
            .setPartitionMapConsumer(createPartitionZipConsumer(pgMapFile))
            .run(parameters.getRuntime(), Main.class)
            .assertFailureWithErrorThatThrows(RuntimeException.class)
            .getOriginalStackTrace();

    assertTrue(Files.exists(pgMapFile));
    assertThat(
        originalStackTrace.retrace(createMappingSupplierFromPartitionZip(pgMapFile)),
        isSame(EXPECTED));
  }

  private PartitionMapConsumer createPartitionZipConsumer(Path pgMapFile) throws IOException {
    ZipBuilder zipBuilder = ZipBuilder.builder(pgMapFile);
    return new PartitionMapConsumer() {
      @Override
      public void acceptMappingPartition(MappingPartition mappingPartition) {
        try {
          zipBuilder.addBytes(mappingPartition.getKey(), mappingPartition.getPayload());
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public void acceptMappingPartitionMetadata(
          MappingPartitionMetadata mappingPartitionMetadata) {
        try {
          zipBuilder.addBytes("METADATA", mappingPartitionMetadata.getBytes());
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public void finished(DiagnosticsHandler handler) {
        try {
          zipBuilder.build();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  private PartitionMappingSupplier createMappingSupplierFromPartitionZip(Path pgMapFile)
      throws IOException {
    ZipFile zipFile = new ZipFile(pgMapFile.toFile());
    byte[] metadata = ByteStreams.toByteArray(zipFile.getInputStream(zipFile.getEntry("METADATA")));
    return PartitionMappingSupplier.builder()
        .setMetadata(metadata)
        .setMappingPartitionFromKeySupplier(
            key -> {
              try {
                return ByteStreams.toByteArray(zipFile.getInputStream(zipFile.getEntry(key)));
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })
        .build();
  }
}
