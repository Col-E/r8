// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.R8RunArtTestsTest.CompilerUnderTest;
import com.android.tools.r8.R8RunArtTestsTest.DexTool;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public abstract class R8RunExamplesCommon {

  protected enum Input {
    DX, JAVAC, JAVAC_ALL, JAVAC_NONE
  }

  protected enum Frontend {
    JAR,
    CF
  }

  protected enum Output {
    DEX,
    CF
  }

  protected static String[] makeTest(
      Input input, CompilerUnderTest compiler, CompilationMode mode, String clazz) {
    return makeTest(input, compiler, mode, clazz, Output.DEX);
  }

  protected static String[] makeTest(
      Input input, CompilerUnderTest compiler, CompilationMode mode, String clazz, Output output) {
    return makeTest(input, compiler, mode, clazz, Frontend.JAR, output);
  }

  protected static String[] makeTest(
      Input input,
      CompilerUnderTest compiler,
      CompilationMode mode,
      String clazz,
      Frontend frontend,
      Output output) {
    String pkg = clazz.substring(0, clazz.lastIndexOf('.'));
    return new String[] {
      pkg, input.name(), compiler.name(), mode.name(), clazz, frontend.name(), output.name()
    };
  }

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  private final Input input;
  private final CompilerUnderTest compiler;
  private final CompilationMode mode;
  private final String pkg;
  private final String mainClass;
  private final Frontend frontend;
  private final Output output;

  public R8RunExamplesCommon(
      String pkg,
      String input,
      String compiler,
      String mode,
      String mainClass,
      String frontend,
      String output) {
    this.pkg = pkg;
    this.input = Input.valueOf(input);
    this.compiler = CompilerUnderTest.valueOf(compiler);
    this.mode = CompilationMode.valueOf(mode);
    this.mainClass = mainClass;
    this.frontend = Frontend.valueOf(frontend);
    this.output = Output.valueOf(output);
  }

  private Path getOutputFile() {
    return temp.getRoot().toPath().resolve("out.jar");
  }

  private Path getInputFile() {
    switch(input) {
      case DX:
        return getOriginalDexFile();
      case JAVAC:
        return getOriginalJarFile("");
      case JAVAC_ALL:
        return getOriginalJarFile("_debuginfo_all");
      case JAVAC_NONE:
        return getOriginalJarFile("_debuginfo_none");
      default:
        throw new Unreachable();
    }
  }

  private R8Command.Builder addInputFile(R8Command.Builder builder) throws NoSuchFileException {
    if (input == Input.DX) {
      // If input is DEX code, use the tool helper to add the DEX sources as R8 disallows them.
      ToolHelper.getAppBuilder(builder).addProgramFiles(getInputFile());
    } else {
      builder.addProgramFiles(getInputFile());
    }
    return builder;
  }

  public Path getOriginalJarFile(String postFix) {
    return Paths.get(getExampleDir(), pkg + postFix + JAR_EXTENSION);
  }

  private Path getOriginalDexFile() {
    return Paths.get(getExampleDir(), pkg, ToolHelper.DEFAULT_DEX_FILENAME);
  }

  private DexTool getTool() {
    return input == Input.DX ? DexTool.DX : DexTool.NONE;
  }

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void compile() throws Exception {
    if (output == Output.CF && getFailingCompileCf().contains(mainClass)) {
      thrown.expect(Throwable.class);
    }
    OutputMode outputMode = output == Output.CF ? OutputMode.ClassFile : OutputMode.DexIndexed;
    switch (compiler) {
      case D8: {
        assertTrue(output == Output.DEX);
        D8.run(
            D8Command.builder()
                .addProgramFiles(getInputFile())
                .setOutput(getOutputFile(), outputMode)
                .setMode(mode)
                .build());
        break;
      }
      case R8:
        {
          R8Command command =
              addInputFile(R8Command.builder())
                  .setOutput(getOutputFile(), outputMode)
                  .setMode(mode)
                  .build();
          ExceptionUtils.withR8CompilationHandler(
              command.getReporter(),
              () ->
                  ToolHelper.runR8(
                      command,
                      options -> {
                        options.lineNumberOptimization = LineNumberOptimization.OFF;
                        options.enableCfFrontend = frontend == Frontend.CF;
                      }));
          break;
        }
      default:
        throw new Unreachable();
    }
  }

  @Test
  public void outputIsIdentical() throws IOException, InterruptedException, ExecutionException {
    if (!ToolHelper.artSupported()) {
      return;
    }

    DexVm vm = ToolHelper.getDexVm();

    if (shouldSkipVm(vm.getVersion())) {
      return;
    }

    String original = getOriginalDexFile().toString();
    Path generated = getOutputFile();

    ToolHelper.ProcessResult javaResult = ToolHelper.runJava(getOriginalJarFile(""), mainClass);
    if (javaResult.exitCode != 0) {
      System.out.println(javaResult.stdout);
      System.err.println(javaResult.stderr);
      fail("JVM failed for: " + mainClass);
    }

    TestCondition condition =
        output == Output.CF ? getFailingRunCf().get(mainClass) : getFailingRun().get(mainClass);
    if (condition != null && condition.test(getTool(), compiler, vm.getVersion(), mode)) {
      thrown.expect(Throwable.class);
    } else {
      thrown = ExpectedException.none();
    }

    if (output == Output.CF) {
      ToolHelper.ProcessResult result = ToolHelper.runJava(generated, mainClass);
      if (result.exitCode != 0) {
        System.err.println(result.stderr);
        fail("JVM failed on compiled output for: " + mainClass);
      }
      if (!getFailingOutputCf().contains(mainClass)) {
        assertEquals(
            "JavaC/JVM and " + compiler.name() + "/JVM output differ",
            javaResult.stdout,
            result.stdout);
      }
      return;
    }

    // Check output against Art output on original dex file.
    String output =
        ToolHelper.checkArtOutputIdentical(original, generated.toString(), mainClass, vm);

    // Check output against JVM output.
    if (shouldMatchJVMOutput(vm.getVersion())) {
      String javaOutput = javaResult.stdout;
      assertEquals("JVM and Art output differ", javaOutput, output);
    }
  }

  private boolean shouldMatchJVMOutput(DexVm.Version version) {
    TestCondition condition = getOutputNotIdenticalToJVMOutput().get(mainClass);
    return condition == null || !condition.test(getTool(), compiler, version, mode);
  }

  private boolean shouldSkipVm(DexVm.Version version) {
    TestCondition condition = getSkip().get(mainClass);
    return condition != null && condition.test(getTool(), compiler, version, mode);
  }

  protected abstract String getExampleDir();

  protected abstract Map<String, TestCondition> getFailingRun();

  protected abstract Map<String, TestCondition> getFailingRunCf();

  protected abstract Set<String> getFailingCompileCf();

  protected abstract Set<String> getFailingOutputCf();

  protected abstract Map<String, TestCondition> getOutputNotIdenticalToJVMOutput();

  protected abstract Map<String, TestCondition> getSkip();
}
