// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.cf.LambdaTest;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.io.ByteStreams;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8CFExamplesTests extends TestBase {

  private static final Path ART_TESTS_DIR = Paths.get(R8RunArtTestsTest.ART_TESTS_DIR, "dx");
  private final TestMode testMode;
  private final CompilationMode compilationMode;

  public enum TestMode {
    CF_SKIP_IR,
    JAR_TO_IR,
    CF_TO_IR,
  }

  @Parameters(name = "{0}:{1}")
  public static List<Object[]> data() {
    List<Object[]> data = new ArrayList<>();
    for (TestMode testMode : TestMode.values()) {
      for (CompilationMode compilationMode : CompilationMode.values()) {
        data.add(new Object[] {testMode, compilationMode});
      }
    }
    return data;
  }

  public R8CFExamplesTests(TestMode testMode, CompilationMode compilationMode) {
    this.testMode = testMode;
    this.compilationMode = compilationMode;
  }

  @Test
  public void testConstMethodHandle() throws Exception {
    Path testDirectory = ART_TESTS_DIR.resolve("979-const-method-handle/classes");
    String classNames[] = {
      "constmethodhandle.ConstTest", "Main",
    };
    runTest(writeInput(testDirectory, classNames), "Main");
  }

  @Test
  public void testLambda() throws Exception {
    runTest(LambdaTest.class);
  }

  private void runTest(Class<?> clazz) throws Exception {
    runTest(writeInput(clazz), clazz.getName());
  }

  private Path writeInput(Class<?> clazz) throws Exception {
    Path inputJar = temp.getRoot().toPath().resolve("input.jar");
    ClassFileConsumer inputConsumer = new ClassFileConsumer.ArchiveConsumer(inputJar);
    String descriptor = DescriptorUtils.javaTypeToDescriptor(clazz.getName());
    inputConsumer.accept(ByteDataView.of(ToolHelper.getClassAsBytes(clazz)), descriptor, null);
    inputConsumer.finished(null);
    return inputJar;
  }

  private Path writeInput(Path testDirectory, String[] classNames) throws Exception {
    Path inputJar = temp.getRoot().toPath().resolve("input.jar");
    ClassFileConsumer inputConsumer = new ClassFileConsumer.ArchiveConsumer(inputJar);
    for (String className : classNames) {
      Path path = testDirectory.resolve(className.replace('.', '/') + ".class");
      String descriptor = DescriptorUtils.javaTypeToDescriptor(className);
      try (InputStream inputStream = new FileInputStream(path.toFile())) {
        inputConsumer.accept(
            ByteDataView.of(ByteStreams.toByteArray(inputStream)), descriptor, null);
      }
    }
    inputConsumer.finished(null);
    return inputJar;
  }

  private void runTest(Path inputJar, String mainClass) throws Exception {
    ProcessResult runInput = ToolHelper.runJava(inputJar, mainClass);
    Assert.assertEquals(0, runInput.exitCode);
    Path outputJar = runR8(inputJar, compilationMode, "output.jar");
    ProcessResult runOutput = ToolHelper.runJava(outputJar, mainClass);
    Assert.assertEquals(runInput.toString(), runOutput.toString());
  }

  private Path runR8(Path inputJar, CompilationMode mode, String outputName) throws Exception {
    Path outputJar = temp.getRoot().toPath().resolve(outputName);
    ToolHelper.runR8(
        R8Command.builder()
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .setMode(mode)
            .addProgramFiles(inputJar)
            .setOutput(outputJar, OutputMode.ClassFile)
            .build(),
        o -> {
          o.skipIR = this.testMode == TestMode.CF_SKIP_IR;
          o.enableCfFrontend = this.testMode != TestMode.JAR_TO_IR;
        });
    return outputJar;
  }
}
