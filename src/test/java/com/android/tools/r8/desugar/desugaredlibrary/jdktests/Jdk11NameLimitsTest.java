// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11TestLibraryDesugaringSpecification.EXTENSION_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Jdk11NameLimitsTest extends DesugaredLibraryTestBase {

  private static final Path TEST_PATH =
      Paths.get(ToolHelper.JDK_11_TESTS_DIR).resolve("java/nio/file/Files/NameLimits.java");
  private static Path[] COMPILED_TEST_PATH;

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        ImmutableList.of(LibraryDesugaringSpecification.JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public Jdk11NameLimitsTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @BeforeClass
  public static void compileJdk11NioTests() throws Exception {
    List<String> options =
        Arrays.asList(
            "--add-reads",
            "java.base=ALL-UNNAMED",
            "--patch-module",
            "java.base=" + EXTENSION_PATH);
    Path tmpDirectory = getStaticTemp().newFolder("cmp").toPath();
    javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addOptions(options)
        .addSourceFiles(TEST_PATH)
        .setOutputPath(tmpDirectory)
        .compile();
    COMPILED_TEST_PATH = getAllFilesWithSuffixInDirectory(tmpDirectory, CLASS_EXTENSION);
    assert COMPILED_TEST_PATH.length > 0;
  }

  @Test
  public void test() throws Throwable {
    Assume.assumeFalse(
        "The behavior is invalid on 13 even without desugared library.",
        parameters.isDexRuntime() && parameters.getDexRuntimeVersion().equals(Version.V13_0_0));
    Assume.assumeFalse(
        "Dead lock issue.",
        parameters.isDexRuntime()
            && libraryDesugaringSpecification.hasNioFileDesugaring(parameters)
            && parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V10_0_0));
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramFiles(COMPILED_TEST_PATH)
        .addKeepMainRule("NameLimits")
        .run(parameters.getRuntime(), "NameLimits")
        .assertSuccess();
  }
}
