// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.DebuggeeState;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.Ignore;
import org.junit.Test;

public class ArrayDimensionGreaterThanSevenTestRunner extends DebugTestBase {

  private static final Class CLASS = ArrayDimensionGreaterThanSevenTest.class;
  private static final String NAME = CLASS.getCanonicalName();

  private DebugTestConfig getR8CfConfig(String s, Consumer<InternalOptions> optionsConsumer)
      throws IOException, com.android.tools.r8.CompilationFailedException {
    Path cfOut = temp.getRoot().toPath().resolve(s);
    ToolHelper.runR8(
        R8Command.builder()
            .addClassProgramData(ToolHelper.getClassAsBytes(CLASS), Origin.unknown())
            .setMode(CompilationMode.DEBUG)
            .setOutput(cfOut, OutputMode.ClassFile)
            .build(),
        optionsConsumer);
    return new CfDebugTestConfig(cfOut);
  }

  private Stream<DebuggeeState> createStream(DebugTestConfig config) throws Exception {
    return streamDebugTest(config, NAME, ANDROID_FILTER);
  }

  @Test
  @Ignore("b/111296969")
  // Once R8 does not use expanded frames this can be enabled again.
  public void test() throws Exception {
    DebugTestConfig cfConfig = new CfDebugTestConfig().addPaths(ToolHelper.getClassPathForTests());
    DebugTestConfig d8Config = new D8DebugTestConfig().compileAndAddClasses(temp, CLASS);
    DebugTestConfig r8JarConfig =
        getR8CfConfig("r8jar.jar", options -> options.enableCfFrontend = false);
    DebugTestConfig r8CfConfig =
        getR8CfConfig("r8cf.jar", options -> options.enableCfFrontend = true);
    new DebugStreamComparator()
        .add("CF", createStream(cfConfig))
        .add("R8/CF", createStream(r8CfConfig))
        .add("R8/Jar", createStream(r8JarConfig))
        .add("D8", createStream(d8Config))
        .compare();
  }

  @Test
  // Verify that ASM fails when using expanded frames directly.
  // See b/111296969
  public void runTestOnAsmDump() throws Exception {
    Path out = temp.getRoot().toPath().resolve("out.jar");
    ArchiveConsumer consumer = new ArchiveConsumer(out);
    consumer.accept(
        ArrayDimensionGreaterThanSevenTestDump.dump(),
        DescriptorUtils.javaTypeToDescriptor(NAME),
        null);
    consumer.finished(null);
    ProcessResult result = ToolHelper.runJava(out, NAME);
    assertEquals("Expected ASM to fail when using visitFrame(F_NEW, ...)", 1, result.exitCode);
    assertThat(result.stderr, containsString("java.lang.NoClassDefFoundError: F"));
  }
}
