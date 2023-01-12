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
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PathTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final String EXPECTED_RESULT_FORMAT =
      StringUtils.lines(
          "x.txt",
          "dir",
          "dir/x.txt",
          "/",
          "%s",
          "class java.nio.file.NoSuchFileException :: notExisting",
          "class java.nio.file.NoSuchFileException :: notExisting",
          "x.txt",
          "",
          "");

  private static final String EXPECTED_RESULT_DESUGARING =
      "class j$.desugar.sun.nio.fs.DesugarLinuxFileSystem";
  private static final String EXPECTED_RESULT_DESUGARING_PLATFORM_FILE_SYSTEM =
      "class j$.nio.file.FileSystem$VivifiedWrapper";
  private static final String EXPECTED_RESULT_NO_DESUGARING = "class sun.nio.fs.LinuxFileSystem";

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
      return String.format(EXPECTED_RESULT_FORMAT, EXPECTED_RESULT_NO_DESUGARING);
    }
    return String.format(
        EXPECTED_RESULT_FORMAT,
        libraryDesugaringSpecification.usesPlatformFileSystem(parameters)
            ? EXPECTED_RESULT_DESUGARING_PLATFORM_FILE_SYSTEM
            : EXPECTED_RESULT_DESUGARING);
  }

  @Ignore("b/265268776")
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

    private static void printError(Throwable t) {
      String[] split =
          t.getMessage() == null ? new String[] {"no-message"} : t.getMessage().split("/");
      System.out.println(t.getClass() + " :: " + split[split.length - 1]);
    }

    public static void main(String[] args) {
      File file = new File("x.txt");
      Path path1 = file.toPath();
      System.out.println(path1);
      Path dir = Paths.get("dir/");
      System.out.println(dir);
      Path resolve = dir.resolve(path1);
      System.out.println(resolve);
      System.out.println(resolve.getFileSystem().getSeparator());
      System.out.println(resolve.getFileSystem().getClass());

      Path notExisting = dir.resolve("notExisting");
      try {
        notExisting.toRealPath();
        System.out.println("IOException not raised!");
      } catch (IOException e) {
        printError(e);
      }
      try {
        notExisting.toRealPath(LinkOption.NOFOLLOW_LINKS);
        System.out.println("IOException not raised!");
      } catch (IOException e) {
        printError(e);
      }

      System.out.println(path1.getFileName());
      Path relativize = path1.relativize(path1);
      System.out.println(relativize);
      System.out.println(relativize.getFileName());
    }
  }
}
