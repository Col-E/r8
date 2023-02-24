// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.google.common.collect.ImmutableList;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class J$ExtensionTest extends DesugaredLibraryTestBase {

  private static final String MAIN_CLASS_NAME = "Main";
  private static final String MAIN_CLASS =
      "import java.time.LocalTimeAccess;\n"
          + "public class Main {\n"
          + "   public static void main(String[] args) throws Exception { \n"
          + "     LocalTimeAccess.main(new String[0]);\n"
          + "   }\n"
          + "}";

  private static final String TIME_CLASS =
      "package java.time;\n"
          + "\n"
          + "import java.time.LocalTime;\n"
          + "\n"
          + "public class LocalTimeAccess {\n"
          + "\n"
          + "  public static void main(String[] args) throws Exception {\n"
          + "    System.out.println(LocalTime.readExternal(null));\n"
          + "  }\n"
          + "}";
  private static Path[] compiledClasses = new Path[2];

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        ImmutableList.of(D8_L8DEBUG));
  }

  public J$ExtensionTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @BeforeClass
  public static void compileClass() throws IOException {
    Assume.assumeFalse(ToolHelper.isWindows());

    File file1 = getStaticTemp().newFile("LocalTimeAccess.java");
    BufferedWriter writer1 = new BufferedWriter(new FileWriter(file1));
    writer1.write(TIME_CLASS);
    writer1.close();
    compiledClasses[0] =
        javac(TestRuntime.getCheckedInJdk8(), getStaticTemp())
            .addSourceFiles(file1.toPath())
            .compile();

    File file2 = getStaticTemp().newFile("Main.java");
    BufferedWriter writer2 = new BufferedWriter(new FileWriter(file2));
    writer2.write(MAIN_CLASS);
    writer2.close();
    compiledClasses[1] =
        javac(TestRuntime.getCheckedInJdk8(), getStaticTemp())
            .addSourceFiles(file2.toPath())
            .addClasspathFiles(compiledClasses[0])
            .compile();
  }

  @Test
  public void testJ$ExtensionNoDesugaring() throws Exception {
    String stderr;
    if (parameters.isCfRuntime()) {
      stderr =
          testForJvm(parameters)
              .addProgramFiles(compiledClasses)
              .run(parameters.getRuntime(), MAIN_CLASS_NAME)
              .assertFailure()
              .getStdErr();
    } else {
      stderr =
          testForD8()
              .addProgramFiles(compiledClasses)
              .setMinApi(parameters)
              .run(parameters.getRuntime(), MAIN_CLASS_NAME)
              .assertFailure()
              .getStdErr();
    }
    assertError(stderr, false);
  }

  private void assertError(String stderr, boolean desugaring) {
    if (parameters.isCfRuntime()) {
      if (parameters.getRuntime().asCf().getVm() == CfVm.JDK8) {
        assertTrue(
            stderr.contains("java.lang.SecurityException: Prohibited package name: java.time"));
      } else {
        assertTrue(stderr.contains("java.lang.ClassNotFoundException: java.time.LocalTimeAccess"));
      }
      return;
    }
    assert !parameters.isCfRuntime();
    if (!desugaring) {
      if (parameters.getDexRuntimeVersion().isOlderThanOrEqual(Version.V6_0_1)) {
        assertTrue(stderr.contains("java.lang.NoClassDefFoundError"));
      } else if (parameters.getDexRuntimeVersion() == Version.V7_0_0) {
        assertTrue(stderr.contains("java.lang.ClassNotFoundException"));
      }
      return;
    }
    if (parameters.getDexRuntimeVersion() == Version.V8_1_0) {
      // On Android 8 the library package private method is accessible.
      assertTrue(stderr.contains("java.lang.NullPointerException"));
    } else {
      assertTrue(stderr.contains("java.lang.IllegalAccessError"));
    }
  }

  @Test
  public void testJ$ExtensionDesugaring() throws Throwable {
    Assume.assumeFalse(parameters.isCfRuntime());
    Assume.assumeTrue(libraryDesugaringSpecification.hasTimeDesugaring(parameters));
    String stdErr =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addProgramFiles(compiledClasses)
            .run(parameters.getRuntime(), MAIN_CLASS_NAME)
            .assertFailure()
            .getStdErr();
    assertError(stdErr, true);
  }
}
