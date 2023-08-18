// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bootstrap;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.D8;
import com.android.tools.r8.R8;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.DesugaredLibraryTestCompileResult;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.examples.hello.HelloTestRunner;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class HelloWorldCompiledOnArtTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withApiLevelsStartingAtIncluding(AndroidApiLevel.L)
            .build(),
        ImmutableList.of(JDK11_PATH),
        ImmutableList.of(D8_L8DEBUG));
  }

  public HelloWorldCompiledOnArtTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  private static String commandLinePathFor(String string) {
    return Paths.get(string).toAbsolutePath().toString();
  }

  private static final String HELLO_NAME = typeName(HelloTestRunner.getHelloClass());

  @Test
  public void testHelloCompiledWithR8Dex() throws Exception {
    Path keepRules =
        writeTextToTempFile(keepMainProguardConfiguration(HELLO_NAME)).toAbsolutePath();
    Path helloInput = HelloTestRunner.writeHelloProgramJar(temp);
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
            keepRules.toString(),
            helloInput.toString())
        .assertSuccess();
    verifyResult(helloOutput);
  }

  @Test
  public void testHelloCompiledWithD8Dex() throws Exception {
    Path helloInput = HelloTestRunner.writeHelloProgramJar(temp).toAbsolutePath();
    Path helloOutput = temp.newFolder("helloOutput").toPath().resolve("out.zip").toAbsolutePath();
    compileR8ToDexWithD8()
        .run(
            parameters.getRuntime(),
            D8.class,
            "--release",
            "--output",
            helloOutput.toString(),
            "--lib",
            commandLinePathFor(ToolHelper.JAVA_8_RUNTIME),
            helloInput.toString())
        .assertSuccess();
    verifyResult(helloOutput);
  }

  private void verifyResult(Path helloOutput) throws IOException {
    ProcessResult processResult = ToolHelper.runArtRaw(helloOutput.toString(), HELLO_NAME);
    assertEquals(HelloTestRunner.getExpectedOutput(), processResult.stdout);
  }

  private DesugaredLibraryTestCompileResult<?> compileR8ToDexWithD8() throws Exception {
    assert parameters.getApiLevel().getLevel() >= AndroidApiLevel.O.getLevel()
        || libraryDesugaringSpecification.hasNioFileDesugaring(parameters);
    return testForDesugaredLibrary(
            parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramFiles(ToolHelper.getR8WithRelocatedDeps())
        .addOptionsModification(
            options -> {
              options.testing.enableD8ResourcesPassThrough = true;
              options.dataResourceConsumer = options.programConsumer.getDataResourceConsumer();
              options.testing.trackDesugaredAPIConversions = true;
            })
        .compile()
        .inspectDiagnosticMessages(
            diagnosticMessages -> {
              assertTrue(
                  diagnosticMessages.getWarnings().isEmpty()
                      || diagnosticMessages.getWarnings().stream()
                          .noneMatch(x -> x.getDiagnosticMessage().contains("andThen")));
            })
        .withArt6Plus64BitsLib();
  }
}
