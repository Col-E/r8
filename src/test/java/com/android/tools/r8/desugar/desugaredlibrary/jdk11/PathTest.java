// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary.jdk11;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PathTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final String EXPECTED_RESULT_DESUGARING =
      StringUtils.lines(
          "x.txt", "dir", "dir/x.txt", "/", "class j$.desugar.sun.nio.fs.DesugarLinuxFileSystem");
  private static final String EXPECTED_RESULT_DESUGARING_PLATFORM_FILE_SYSTEM =
      StringUtils.lines(
          "x.txt", "dir", "dir/x.txt", "/", "class j$.nio.file.FileSystem$VivifiedWrapper");
  private static final String EXPECTED_RESULT_NO_DESUGARING =
      StringUtils.lines("x.txt", "dir", "dir/x.txt", "/", "class sun.nio.fs.LinuxFileSystem");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        ImmutableList.of(JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public PathTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  private String getExpectedResult() {
    if (!libraryDesugaringSpecification.hasNioFileDesugaring(parameters)) {
      return EXPECTED_RESULT_NO_DESUGARING;
    }
    return libraryDesugaringSpecification.usesPlatformFileSystem(parameters)
        ? EXPECTED_RESULT_DESUGARING_PLATFORM_FILE_SYSTEM
        : EXPECTED_RESULT_DESUGARING;
  }

  @Test
  public void test() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .applyIf(
            libraryDesugaringSpecification.hasNioFileDesugaring(parameters),
            b ->
                b.addL8KeepRules("-keepnames class j$.desugar.sun.nio.fs.**")
                    .addL8KeepRules("-keepnames class j$.nio.file.FileSystem**"))
        .addInnerClasses(PathTest.class)
        .addKeepMainRule(TestClass.class)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(getExpectedResult());
  }

  public static class TestClass {

    public static void main(String[] args) {
      File file = new File("x.txt");
      Path path1 = file.toPath();
      System.out.println(path1);
      Path path2 = Paths.get("dir/");
      System.out.println(path2);
      Path resolve = path2.resolve(path1);
      System.out.println(resolve);
      System.out.println(resolve.getFileSystem().getSeparator());
      System.out.println(resolve.getFileSystem().getClass());
    }
  }
}
