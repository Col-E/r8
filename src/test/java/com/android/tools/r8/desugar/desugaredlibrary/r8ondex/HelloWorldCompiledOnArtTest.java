// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.r8ondex;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.R8;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class HelloWorldCompiledOnArtTest extends DesugaredLibraryTestBase {

  // TODO(b/142621961): Create an abstraction to easily run tests on External DexR8.
  // Manage pathMock in the abstraction.
  private static Path pathMock;

  @BeforeClass
  public static void compilePathBackport() throws Exception {
    assumeTrue("JDK8 is not checked-in on Windows", !ToolHelper.isWindows());
    pathMock = getStaticTemp().newFolder("PathMock").toPath();
    javac(TestRuntime.getCheckedInJdk8(), getStaticTemp())
        .setOutputPath(pathMock)
        .addSourceFiles(
            getAllFilesWithSuffixInDirectory(Paths.get("src/test/r8OnArtBackport"), "java"))
        .compile();
  }

  public static Path[] getPathBackport() throws Exception {
    return getAllFilesWithSuffixInDirectory(pathMock, "class");
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V7_0_0)
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.L)
        .build();
  }

  public HelloWorldCompiledOnArtTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static String commandLinePathFor(String string) {
    return Paths.get(string).toAbsolutePath().toString();
  }

  private static final String HELLO_KEEP =
      commandLinePathFor("src/test/examples/hello/keep-rules.txt");
  private static final String HELLO_PATH =
      commandLinePathFor(ToolHelper.EXAMPLES_BUILD_DIR + "hello" + JAR_EXTENSION);

  @Test
  public void testHelloCompiledWithR8Dex() throws Exception {
    Path helloOutput = temp.newFolder("helloOutput").toPath().resolve("out.zip").toAbsolutePath();
    compileR8ToDexWithD8()
        .run(
            parameters.getRuntime(),
            R8.class,
            "--release",
            "--output",
            helloOutput.toString(),
            "--lib",
            commandLinePathFor(ToolHelper.JAVA_8_RUNTIME),
            "--pg-conf",
            HELLO_KEEP,
            HELLO_PATH)
        .assertSuccess();
    verifyResult(helloOutput);
  }

  @Test
  public void testHelloCompiledWithD8Dex() throws Exception {
    Path helloOutput = temp.newFolder("helloOutput").toPath().resolve("out.zip").toAbsolutePath();
    D8TestRunResult run =
        compileR8ToDexWithD8()
            .run(
                parameters.getRuntime(),
                D8.class,
                "--release",
                "--output",
                helloOutput.toString(),
                "--lib",
                commandLinePathFor(ToolHelper.JAVA_8_RUNTIME),
                HELLO_PATH);
    run.assertSuccess();
    verifyResult(helloOutput);
  }

  private void verifyResult(Path helloOutput) throws IOException {
    ProcessResult processResult = ToolHelper.runArtRaw(helloOutput.toString(), "hello.Hello");
    assertEquals(StringUtils.lines("Hello, world"), processResult.stdout);
  }

  private D8TestCompileResult compileR8ToDexWithD8() throws Exception {
    D8TestBuilder d8TestBuilder =
        testForD8().addProgramFiles(ToolHelper.R8_WITH_RELOCATED_DEPS_JAR);
    if (parameters.getApiLevel().getLevel() < AndroidApiLevel.O.getLevel()) {
      d8TestBuilder.addProgramFiles(getPathBackport());
    }
    D8TestCompileResult compile =
        d8TestBuilder
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(parameters.getApiLevel())
            .addOptionsModification(opt -> opt.testing.trackDesugaredAPIConversions = true)
            .compile();
    TestDiagnosticMessages diagnosticMessages = compile.getDiagnosticMessages();
    assertTrue(
        diagnosticMessages.getWarnings().isEmpty()
            || diagnosticMessages.getWarnings().stream()
                .noneMatch(x -> x.getDiagnosticMessage().contains("andThen")));
    return compile
        .addDesugaredCoreLibraryRunClassPath(this::buildDesugaredLibrary, parameters.getApiLevel())
        .withArt6Plus64BitsLib()
        .withArtFrameworks();
  }
}
