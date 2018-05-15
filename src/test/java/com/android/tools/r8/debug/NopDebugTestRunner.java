// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.DebuggeeState;
import com.android.tools.r8.utils.DescriptorUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.Assume;
import org.junit.Test;

public class NopDebugTestRunner extends DebugTestBase {

  private final Class<?> CLAZZ = NopTest.class;

  @Test
  public void testNop() throws Exception {
    // R8 will dead-code eliminate "args = args" in NopTest.
    // In debug mode the programmer should still be able to break on dead code,
    // so R8 inserts a NOP in the output. Test that CfNop.buildIR() works.
    runTwiceTest(writeInput(CLAZZ), CLAZZ.getName());
  }

  private Path writeInput(Class<?> clazz) throws Exception {
    Path inputJar = temp.getRoot().toPath().resolve("input.jar");
    ClassFileConsumer inputConsumer = new ClassFileConsumer.ArchiveConsumer(inputJar);
    String descriptor = DescriptorUtils.javaTypeToDescriptor(clazz.getName());
    inputConsumer.accept(ToolHelper.getClassAsBytes(clazz), descriptor, null);
    inputConsumer.finished(null);
    return inputJar;
  }

  private void runTwiceTest(Path inputJar, String mainClass) throws Exception {
    Path output1 = runR8(inputJar, "output1.jar");
    Path output2 = runR8(output1, "output2.jar");
    stepOutput(mainClass, inputJar, output1, output2);
  }

  private Path runR8(Path inputJar, String outputName) throws Exception {
    Path outputJar = temp.getRoot().toPath().resolve(outputName);
    ToolHelper.runR8(
        R8Command.builder()
            .addLibraryFiles(Paths.get(ToolHelper.JAVA_8_RUNTIME))
            .setMode(CompilationMode.DEBUG)
            .addProgramFiles(inputJar)
            .setOutput(outputJar, OutputMode.ClassFile)
            .build(),
        o -> o.enableCfFrontend = true);
    return outputJar;
  }

  private void stepOutput(String mainClass, Path inputJar, Path output1, Path output2)
      throws Exception {
    Assume.assumeTrue(
        "Skipping test "
            + testName.getMethodName()
            + " because debug tests are not yet supported on Windows",
        !ToolHelper.isWindows());
    new DebugStreamComparator()
        .add("Input", streamDebugTest(mainClass, new CfDebugTestConfig(inputJar)))
        .add("R8/CF", streamDebugTest(mainClass, new CfDebugTestConfig(output1)))
        .add("R8/CF^2", streamDebugTest(mainClass, new CfDebugTestConfig(output2)))
        .compare();
  }

  private Stream<DebuggeeState> streamDebugTest(String mainClass, DebugTestConfig config)
      throws Exception {
    return streamDebugTest(config, mainClass, ANDROID_FILTER);
  }
}
