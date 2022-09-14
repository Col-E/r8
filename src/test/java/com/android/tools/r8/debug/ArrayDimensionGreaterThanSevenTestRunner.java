// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.DebuggeeState;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.Assume;
import org.junit.Test;

public class ArrayDimensionGreaterThanSevenTestRunner extends DebugTestBase {

  private static final Class CLASS = ArrayDimensionGreaterThanSevenTest.class;
  private static final String NAME = CLASS.getCanonicalName();

  private DebugTestConfig getR8CfConfig(String s)
      throws IOException, com.android.tools.r8.CompilationFailedException {
    Path cfOut = temp.getRoot().toPath().resolve(s);
    ToolHelper.runR8(
        R8Command.builder()
            .addClassProgramData(ToolHelper.getClassAsBytes(CLASS), Origin.unknown())
            .setMode(CompilationMode.DEBUG)
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .setOutput(cfOut, OutputMode.ClassFile)
            .setDisableTreeShaking(true)
            .setDisableMinification(true)
            .addProguardConfiguration(ImmutableList.of("-keepattributes *"), Origin.unknown())
            .build());
    return new CfDebugTestConfig(cfOut);
  }

  private Stream<DebuggeeState> createStream(DebugTestConfig config) throws Exception {
    return streamDebugTest(config, NAME, ANDROID_FILTER);
  }

  @Test
  // Once R8 does not use expanded frames this can be enabled again.
  public void test() throws Exception {
    // TODO(b/199700280): Reenable on 12.0.0 when we have the libjdwp.so file include and the flags
    // fixed.
    Assume.assumeTrue(
        "Skipping test " + testName.getMethodName() + " because debugging not enabled in 12.0.0",
        !ToolHelper.getDexVm().isNewerThanOrEqual(DexVm.ART_12_0_0_HOST));
    Assume.assumeTrue(ToolHelper.getDexVm().isNewerThan(DexVm.ART_5_1_1_HOST)
        && !ToolHelper.isWindows());
    DebugTestConfig cfConfig = new CfDebugTestConfig().addPaths(ToolHelper.getClassPathForTests());
    DebugTestConfig d8Config = new D8DebugTestConfig().compileAndAddClasses(temp, CLASS);
    DebugTestConfig r8CfConfig = getR8CfConfig("r8cf.jar");
    new DebugStreamComparator()
        .add("CF", createStream(cfConfig))
        .add("R8/CF", createStream(r8CfConfig))
        .add("D8", createStream(d8Config))
        .compare();
  }

  @Test
  // Verify that ASM fails when using expanded frames directly.
  // See b/111296969
  // Right now the test passes because the dump uses up to 7 dimensions
  // But we need to update it to check for the 31-32 boundary
  public void runTestOnAsmDump() throws Exception {
    Path out = temp.getRoot().toPath().resolve("out.jar");
    ArchiveConsumer consumer = new ArchiveConsumer(out);
    consumer.accept(
        ByteDataView.of(ArrayDimensionGreaterThanSevenTestDump.dump()),
        DescriptorUtils.javaTypeToDescriptor(NAME),
        null);
    consumer.finished(null);
    ProcessResult result = ToolHelper.runJava(out, NAME);
    assertEquals("Assumes ASM can go up to 7 dimensions", 0, result.exitCode);
  }
}
