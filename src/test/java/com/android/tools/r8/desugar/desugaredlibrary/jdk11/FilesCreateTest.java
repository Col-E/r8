// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdk11;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FilesCreateTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "ind3f class java.nio.file.NoSuchFileException",
          "ind4f class java.nio.file.NoSuchFileException",
          "ind5f class java.nio.file.NoSuchFileException",
          "ind6f class java.nio.file.NoSuchFileException",
          "true",
          "true",
          "notExisting1class java.nio.file.NoSuchFileException",
          "false",
          "false");
  private static final String EXPECTED_RESULT_DESUGARING =
      StringUtils.lines(
          "ind5f class java.io.FileNotFoundException",
          "ind6f class java.io.FileNotFoundException",
          "true",
          "true",
          "notExisting1class java.io.IOException",
          "false",
          "true",
          "ind4f/dir",
          "ind4f",
          "ind3f/dir",
          "ind3f");
  private static final String EXPECTED_FILES =
      StringUtils.lines(
          "ind2s/dir", "ind2s", "ind1s/dir", "ind1s", "f4.txt", "f3.txt", "dir2s", "dir1s");

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        // Skip Android 4.4.4 due to missing libjavacrypto.
        getTestParameters()
            .withCfRuntime(CfVm.JDK11)
            .withDexRuntime(Version.V4_0_4)
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withAllApiLevels()
            .build(),
        ImmutableList.of(JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public FilesCreateTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void test() throws Throwable {
    if (parameters.isCfRuntime()) {
      // Reference runtime, we use Jdk 11 since this is Jdk 11 desugared library, not that Jdk 8
      // behaves differently on this test.
      Assume.assumeTrue(parameters.isCfRuntime(CfVm.JDK11));
      testForJvm()
          .addInnerClasses(getClass())
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(getExpectedResult());
      return;
    }
    Assume.assumeFalse(
        "The command mkdir fails on Android 7.0, we need to investigate if this is an emulator"
            + "issue or a real issue.",
        parameters.getDexRuntimeVersion().equals(Version.V7_0_0));
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .compile()
        .withArt6Plus64BitsLib()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(getExpectedResult());
  }

  private String getExpectedResult() {
    if (parameters.isCfRuntime()) {
      return EXPECTED_RESULT + EXPECTED_FILES;
    }
    return (libraryDesugaringSpecification.usesPlatformFileSystem(parameters)
            ? EXPECTED_RESULT
            : EXPECTED_RESULT_DESUGARING)
        + EXPECTED_FILES;
  }

  public static class TestClass {

    public static void main(String[] args) throws Throwable {
      Path root = Files.createTempDirectory("tmp_test");
      Files.createDirectories(root.resolve("ind1s/dir"));
      Files.createDirectories(root.resolve("ind2s/dir"), getFileAttribute());
      try {
        Files.createDirectory(root.resolve("ind3f/dir"));
      } catch (Throwable t) {
        System.out.println("ind3f " + t.getClass());
      }
      try {
        Files.createDirectory(root.resolve("ind4f/dir"), getFileAttribute());
      } catch (Throwable t) {
        System.out.println("ind4f " + t.getClass());
      }
      Files.createDirectory(root.resolve("dir1s"));
      Files.createDirectory(root.resolve("dir2s"), getFileAttribute());
      try {
        Files.createFile(root.resolve("ind5f/f.txt"));
      } catch (Throwable t) {
        System.out.println("ind5f " + t.getClass());
      }
      try {
        Files.createFile(root.resolve("ind6f/f.txt"), getFileAttribute());
      } catch (Throwable t) {
        System.out.println("ind6f " + t.getClass());
      }
      Files.createFile(root.resolve("f1.txt"));
      Files.createFile(root.resolve("f2.txt"), getFileAttribute());

      System.out.println(Files.exists(root.resolve("f1.txt")));
      System.out.println(Files.exists(root.resolve("f1.txt"), LinkOption.NOFOLLOW_LINKS));

      Files.delete(root.resolve("f1.txt"));
      try {
        Files.delete(root.resolve("notExisting1.txt"));
      } catch (Throwable t) {
        System.out.println("notExisting1" + t.getClass());
      }
      Files.deleteIfExists(root.resolve("f2.txt"));
      Files.deleteIfExists(root.resolve("notExisting2.txt"));

      System.out.println(Files.exists(root.resolve("f1.txt")));
      System.out.println(Files.exists(root.resolve("f1.txt"), LinkOption.NOFOLLOW_LINKS));

      // Recreate for the final print.
      Files.createFile(root.resolve("f3.txt"));
      Files.createFile(root.resolve("f4.txt"), getFileAttribute());

      Files.walk(root)
          .sorted(Comparator.reverseOrder())
          .map(
              f -> {
                if (f != root) {
                  System.out.println(f.subpath(2, f.getNameCount()));
                }
                return f.toFile();
              })
          .forEach(File::delete);
    }

    public static FileAttribute<Set<PosixFilePermission>> getFileAttribute() {
      return PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x"));
    }
  }
}
