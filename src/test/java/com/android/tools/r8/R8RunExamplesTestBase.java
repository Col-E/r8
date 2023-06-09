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
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import com.android.tools.r8.utils.TestDescriptionWatcher;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public abstract class R8RunExamplesTestBase extends TestBase {

  protected enum Input {
    JAVAC,
    JAVAC_ALL,
    JAVAC_NONE
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
      Input input,
      CompilerUnderTest compiler,
      CompilationMode mode,
      String clazz,
      Output output) {
    String pkg = clazz.substring(0, clazz.lastIndexOf('.'));
    return new String[] {pkg, input.name(), compiler.name(), mode.name(), clazz, output.name()};
  }

  @Rule
  public TestDescriptionWatcher watcher = new TestDescriptionWatcher();

  private final Input input;
  private final CompilerUnderTest compiler;
  private final CompilationMode mode;
  private final String pkg;
  private final String mainClass;
  protected final Output output;

  public R8RunExamplesTestBase(
      String pkg, String input, String compiler, String mode, String mainClass, String output) {
    this.pkg = pkg;
    this.input = Input.valueOf(input);
    this.compiler = CompilerUnderTest.valueOf(compiler);
    this.mode = CompilationMode.valueOf(mode);
    this.mainClass = mainClass;
    this.output = Output.valueOf(output);
  }

  private Path getOutputFile() {
    return temp.getRoot().toPath().resolve("out.jar");
  }

  private Path getInputFile() {
    switch(input) {
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

  public R8Command.Builder addInputFile(R8Command.Builder builder) {
    builder.addProgramFiles(getInputFile());
    return builder;
  }

  public Path getOriginalJarFile(String postFix) {
    return Paths.get(getExampleDir(), pkg + postFix + JAR_EXTENSION);
  }
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void compile() throws Exception {
    if (shouldCompileFail()) {
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
      case R8: {
          R8Command command =
              addInputFile(R8Command.builder())
                  .addLibraryFiles(
                      output == Output.CF
                          ? ToolHelper.getJava8RuntimeJar()
                          : ToolHelper.getMostRecentAndroidJar())
                  .setOutput(getOutputFile(), outputMode)
                  .setMode(mode)
                  .setDisableTreeShaking(true)
                  .setDisableMinification(true)
                  .addProguardConfiguration(
                      ImmutableList.of("-keepattributes *", "-dontoptimize"), Origin.unknown())
                  .build();
          ToolHelper.runR8(command, this::configure);
        break;
      }
      default:
        throw new Unreachable();
    }
  }

  protected void configure(InternalOptions options) {
    options.lineNumberOptimization = LineNumberOptimization.OFF;
  }

  private boolean shouldCompileFail() {
    if (output == Output.CF && getFailingCompileCf().contains(mainClass)) {
      return true;
    }
    if (output == Output.DEX && getFailingCompileCfToDex().contains(mainClass)) {
      return true;
    }
    return false;
  }

  @Test
  public void outputIsIdentical() throws IOException {
    if (shouldCompileFail()) {
      // We expected an exception, but got none.
      // Return to ensure that this test fails due to the missing exception.
      return;
    }
    Assume.assumeTrue(ToolHelper.artSupported() || ToolHelper.compareAgaintsGoldenFiles());

    DexVm vm = ToolHelper.getDexVm();
    Assume.assumeFalse("Triage (b/144966342)", vm.isNewerThan(DexVm.ART_9_0_0_HOST));

    if (shouldSkipVm(vm.getVersion())) {
      return;
    }

    Path generated = getOutputFile();

    ToolHelper.ProcessResult javaResult = ToolHelper.runJava(getOriginalJarFile(""), mainClass);
    if (javaResult.exitCode != 0) {
      System.out.println(javaResult.stdout);
      System.err.println(javaResult.stderr);
      fail("JVM failed for: " + mainClass);
    }

    TestCondition condition =
        output == Output.CF ? getFailingRunCf().get(mainClass) : getFailingRun().get(mainClass);
    if (condition != null && condition.test(DexTool.NONE, compiler, vm.getVersion(), mode)) {
      thrown.expect(Throwable.class);
    }

    if (output == Output.DEX && getFailingRunCfToDex().contains(mainClass)) {
      thrown.expect(Throwable.class);
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

    // Check output against JVM output if we have it, otherwise check on art
    String d8Output =
        ToolHelper.runArtNoVerificationErrors(
            Collections.singletonList(generated.toString()), mainClass, null, vm);
    String javaOutput = javaResult.stdout;
    assertEquals("JVM and Art output differ", javaOutput, d8Output);
  }

  private boolean shouldSkipVm(DexVm.Version version) {
    TestCondition condition = getSkip().get(mainClass);
    return condition != null && condition.test(DexTool.NONE, compiler, version, mode);
  }

  protected abstract String getExampleDir();

  protected abstract Map<String, TestCondition> getFailingRun();

  protected abstract Map<String, TestCondition> getFailingRunCf();

  protected abstract Set<String> getFailingCompileCfToDex();

  // TODO(mathiasr): Add CompilerSet for CfToDex so we can fold this into getFailingRun().
  protected abstract Set<String> getFailingRunCfToDex();

  protected abstract Set<String> getFailingCompileCf();

  protected abstract Set<String> getFailingOutputCf();

  protected abstract Map<String, TestCondition> getSkip();
}
