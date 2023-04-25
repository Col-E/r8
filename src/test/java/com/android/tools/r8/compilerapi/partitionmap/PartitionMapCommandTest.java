// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.compilerapi.partitionmap;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.PartitionMapConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.compilerapi.partitionmap.PartitionMapCommandTest.ApiTest.EnsurePartitionMapOutputConsumer;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.retrace.MappingPartition;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.utils.BooleanBox;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class PartitionMapCommandTest extends CompilerApiTestRunner {

  private static final int FIRST_API_LEVEL_WITH_NATIVE_MULTIDEX = 21;

  public PartitionMapCommandTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  private interface Runner {
    void run(
        ProgramConsumer programConsumer,
        StringConsumer pgMapConsumer,
        PartitionMapConsumer partitionMapConsumer)
        throws Exception;
  }

  @Test
  public void testR8() throws Exception {
    runTest(new ApiTest(CompilerApiTest.PARAMETERS)::runR8);
  }

  @Test
  public void testD8() throws Exception {
    runTest(new ApiTest(CompilerApiTest.PARAMETERS)::runD8);
  }

  private void runTest(Runner test) throws Exception {
    EnsurePartitionMapOutputConsumer partitionConsumer = EnsurePartitionMapOutputConsumer.get();
    test.run(
        DexIndexedConsumer.emptyConsumer(),
        StringConsumer.emptyConsumer(),
        partitionConsumer.getConsumer());
    assertTrue(partitionConsumer.hasOutput());
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void runD8(
        ProgramConsumer programConsumer,
        StringConsumer pgMapConsumer,
        PartitionMapConsumer partitionMapConsumer)
        throws Exception {
      D8.run(
          D8Command.builder()
              .addClassProgramData(getBytesForClass(getMockClass()), Origin.unknown())
              .addClassProgramData(getBytesForClass(getPostStartupMockClass()), Origin.unknown())
              .addLibraryFiles(getJava8RuntimeJar())
              .setMinApiLevel(FIRST_API_LEVEL_WITH_NATIVE_MULTIDEX)
              .setProguardMapConsumer(pgMapConsumer)
              .setProgramConsumer(programConsumer)
              .setPartitionMapConsumer(partitionMapConsumer)
              .build());
    }

    public void runR8(
        ProgramConsumer programConsumer,
        StringConsumer pgMapConsumer,
        PartitionMapConsumer partitionMapConsumer)
        throws Exception {
      R8.run(
          R8Command.builder()
              .addClassProgramData(getBytesForClass(getMockClass()), Origin.unknown())
              .addProguardConfiguration(getKeepMainRules(getMockClass()), Origin.unknown())
              .addLibraryFiles(getJava8RuntimeJar())
              .setMinApiLevel(FIRST_API_LEVEL_WITH_NATIVE_MULTIDEX)
              .setProgramConsumer(programConsumer)
              .setProguardMapConsumer(pgMapConsumer)
              .setPartitionMapConsumer(partitionMapConsumer)
              .build());
    }

    @Test
    public void testR8() throws Exception {
      EnsurePartitionMapOutputConsumer ensurePartitionMapOutputConsumer =
          EnsurePartitionMapOutputConsumer.get();
      runR8(
          DexIndexedConsumer.emptyConsumer(),
          StringConsumer.emptyConsumer(),
          ensurePartitionMapOutputConsumer.getConsumer());
    }

    @Test
    public void testD8() throws Exception {
      EnsurePartitionMapOutputConsumer ensurePartitionMapOutputConsumer =
          EnsurePartitionMapOutputConsumer.get();
      runD8(
          DexIndexedConsumer.emptyConsumer(),
          StringConsumer.emptyConsumer(),
          ensurePartitionMapOutputConsumer.getConsumer());
    }

    public static class EnsurePartitionMapOutputConsumer {

      private final List<String> keys;
      private final BooleanBox finished;
      private final PartitionMapConsumer consumer;

      private EnsurePartitionMapOutputConsumer(
          List<String> keys, BooleanBox finished, PartitionMapConsumer consumer) {
        this.keys = keys;
        this.finished = finished;
        this.consumer = consumer;
      }

      private PartitionMapConsumer getConsumer() {
        return consumer;
      }

      private boolean hasOutput() {
        return finished.get() && !keys.isEmpty();
      }

      public static EnsurePartitionMapOutputConsumer get() {
        List<String> keys = new ArrayList<>();
        BooleanBox finished = new BooleanBox();
        return new EnsurePartitionMapOutputConsumer(
            keys,
            finished,
            new PartitionMapConsumer() {
              @Override
              public void acceptMappingPartition(MappingPartition mappingPartition) {
                keys.add(mappingPartition.getKey());
              }

              @Override
              public void acceptMappingPartitionMetadata(
                  MappingPartitionMetadata mappingPartitionMetadata) {
                keys.add("METADATA");
              }

              @Override
              public void finished(DiagnosticsHandler handler) {
                finished.set();
              }
            });
      }
    }
  }
}
