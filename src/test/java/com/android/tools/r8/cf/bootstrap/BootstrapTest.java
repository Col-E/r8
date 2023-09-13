// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.bootstrap;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.base.Charsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BootstrapTest extends TestBase {

  static class HelloWorldProgram {
    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }

  private static final Class<?> HELLO_CLASS = HelloWorldProgram.class;
  private static String HELLO_EXPECTED = StringUtils.lines("Hello, world!");

  private static final Path R8_STABLE_JAR =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "r8-releases", "3.2.54", "r8.jar");

  private static class R8Result {

    final ProcessResult processResult;
    final Path outputJar;
    final String pgMap;

    R8Result(ProcessResult processResult, Path outputJar, String pgMap) {
      this.processResult = processResult;
      this.outputJar = outputJar;
      this.pgMap = pgMap;
    }

    @Override
    public String toString() {
      return processResult.toString() + "\n\n" + pgMap;
    }
  }

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultCfRuntime().build();
  }

  private Path getHelloInputs() {
    return ToolHelper.getClassFileForTestClass(HELLO_CLASS);
  }

  private String getHelloKeepRules() {
    return TestBase.keepMainProguardConfiguration(HELLO_CLASS);
  }

  @Test
  public void reference() throws Exception {
    testForJvm(parameters)
        .addProgramFiles(getHelloInputs())
        .run(parameters.getRuntime(), HELLO_CLASS)
        .assertSuccessWithOutput(HELLO_EXPECTED);
  }

  @Test
  public void testDebug() throws Exception {
    compareForMode(CompilationMode.DEBUG);
  }

  @Test
  public void testRelease() throws Exception {
    compareForMode(CompilationMode.RELEASE);
  }

  private R8Result compareForMode(CompilationMode mode) throws Exception {
    // Run r8.jar on hello.jar to ensure that r8.jar is a working compiler.
    R8Result helloCompiledWithR8 =
        runExternalR8(R8_STABLE_JAR, getHelloInputs(), getHelloKeepRules(), mode);
    testForJvm(parameters)
        .addProgramFiles(helloCompiledWithR8.outputJar)
        .run(parameters.getRuntime(), HELLO_CLASS)
        .assertSuccessWithOutput(HELLO_EXPECTED);

    compareR8(helloCompiledWithR8, mode);
    return helloCompiledWithR8;
  }

  private void compareR8(R8Result referenceCompilation, CompilationMode mode) throws Exception {
    // Run R8 on r8.jar.
    Path r8CompiledByR8 = compileR8WithR8(mode);
    // Run the resulting compiler on hello.jar.
    R8Result runR8R8 = runExternalR8(r8CompiledByR8, getHelloInputs(), getHelloKeepRules(), mode);
    // Check that the process outputs (exit code, stdout, stderr) are the same.
    assertEquals(referenceCompilation.toString(), runR8R8.toString());
    // Check that the output jars are the same.
    assertProgramsEqual(referenceCompilation.outputJar, runR8R8.outputJar);
  }

  private Path compileR8WithR8(CompilationMode mode) throws Exception {
    return testForR8(parameters.getBackend())
        .setMode(mode)
        .addProgramFiles(R8_STABLE_JAR)
        .addKeepRules(TestBase.keepMainProguardConfiguration(R8.class))
        // The r8 stable/release hits open interface issues.
        .addOptionsModification(o -> o.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces())
        // The r8 stable/release contains missing com.google.errorprone annotation references.
        .addDontWarnGoogle()
        .compile()
        .writeToZip();
  }

  private R8Result runExternalR8(Path r8Jar, Path inputJar, String keepRules, CompilationMode mode)
      throws Exception {
    Path outputPath = temp.newFolder().toPath();
    Path pgConfigFile = outputPath.resolve("keep.rules");
    Path outputJar = outputPath.resolve("output.jar");
    Path pgMapFile = outputPath.resolve("map.txt");
    FileUtils.writeTextFile(pgConfigFile, keepRules);
    ProcessResult processResult =
        ToolHelper.runJava(
            parameters.getRuntime().asCf(),
            Collections.singletonList(r8Jar),
            R8.class.getTypeName(),
            "--lib",
            ToolHelper.getJava8RuntimeJar().toString(),
            "--classfile",
            inputJar.toString(),
            "--output",
            outputJar.toString(),
            "--pg-conf",
            pgConfigFile.toString(),
            mode == CompilationMode.DEBUG ? "--debug" : "--release",
            "--pg-map-output",
            pgMapFile.toString());
    if (processResult.exitCode != 0) {
      System.out.println(processResult);
    }
    assertEquals(processResult.toString(), 0, processResult.exitCode);
    String pgMap = FileUtils.readTextFile(pgMapFile, Charsets.UTF_8);
    return new R8Result(processResult, outputJar, pgMap);
  }
}
