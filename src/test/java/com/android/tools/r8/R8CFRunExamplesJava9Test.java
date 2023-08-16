// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.utils.FileUtils.ZIP_EXTENSION;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.desugar.LibraryFilesHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class R8CFRunExamplesJava9Test extends RunExamplesJava9Test<R8Command.Builder> {

  class R8CFTestRunner extends TestRunner<R8CFTestRunner> {

    R8CFTestRunner(String testName, String packageName, String mainClass) {
      super(testName, packageName, mainClass);
    }

    @Override
    R8CFTestRunner withMinApiLevel(int minApiLevel) {
      return self();
    }

    @Override
    R8CFTestRunner withKeepAll() {
      return withBuilderTransformation(
          builder ->
              builder
                  .setMode(CompilationMode.DEBUG)
                  .setDisableTreeShaking(true)
                  .setDisableMinification(true)
                  .addProguardConfiguration(
                      ImmutableList.of("-keepattributes *"), Origin.unknown()));
    }

    @Override
    void build(Path inputFile, Path out) throws Throwable {
      R8Command.Builder builder = R8Command.builder();
      for (UnaryOperator<R8Command.Builder> transformation : builderTransformations) {
        builder = transformation.apply(builder);
      }
      builder.addLibraryFiles(LibraryFilesHelper.getJdk9LibraryFiles(temp));
      R8Command command =
          builder.addProgramFiles(inputFile).setOutput(out, OutputMode.ClassFile).build();
      ToolHelper.runR8(command, this::combinedOptionConsumer);
    }

    @Override
    void run() throws Throwable {
      String qualifiedMainClass = packageName + "." + mainClass;
      Path inputFile = getInputJar();
      Path out = temp.getRoot().toPath().resolve(testName + ZIP_EXTENSION);

      build(inputFile, out);

      if (!ToolHelper.isJava9Runtime()) {
        System.out.println("No Java 9 support; skip execution tests");
        return;
      }

      if (!dexInspectorChecks.isEmpty()) {
        CodeInspector inspector = new CodeInspector(out);
        for (Consumer<CodeInspector> check : dexInspectorChecks) {
          check.accept(inspector);
        }
      }

      execute(testName, qualifiedMainClass, new Path[] {inputFile}, new Path[] {out}, args);
    }

    @Override
    R8CFTestRunner self() {
      return this;
    }
  }

  @Override
  R8CFTestRunner test(String testName, String packageName, String mainClass) {
    return new R8CFTestRunner(testName, packageName, mainClass);
  }

  @Override
  void execute(
      String testName,
      String qualifiedMainClass,
      Path[] inputJars,
      Path[] outputJars,
      List<String> args)
      throws IOException {
    boolean expectedToFail = expectedToFailCf(testName);
    if (expectedToFail) {
      thrown.expect(Throwable.class);
    }
    String[] mainAndArgs =
        ImmutableList.builder()
            .add(qualifiedMainClass)
            .addAll(args)
            .build()
            .toArray(StringUtils.EMPTY_ARRAY);
    ProcessResult outputResult = ToolHelper.runJava(Arrays.asList(outputJars), mainAndArgs);
    ToolHelper.ProcessResult inputResult =
        ToolHelper.runJava(ImmutableList.copyOf(inputJars), mainAndArgs);
    assertEquals(inputResult.toString(), outputResult.toString());
    if (inputResult.exitCode != 0) {
      System.out.println(inputResult);
    }
    assertEquals(0, inputResult.exitCode);
    if (expectedToFail) {
      System.out.println("Did not fail as expected");
    }
  }

  private static List<String> expectedFailures =
      ImmutableList.of(
      );

  private boolean expectedToFailCf(String testName) {
    System.out.println(testName + " " + expectedFailures.contains(testName));
    return expectedFailures.contains(testName);
  }
}
