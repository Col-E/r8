// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdk11;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FilesVisitTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "Find f1",
          "[f1.txt]",
          "[f1.txt]",
          "[f1.txt]",
          "Find f4",
          "[]",
          "[f4.txt]",
          "[f4.txt]",
          "List",
          "[f1.txt, f2.txt, f3.txt, innerDir]",
          "Walk 1",
          "[f1.txt, f2.txt, f3.txt, innerDir, root]",
          "[f1.txt, f2.txt, f3.txt, innerDir, root]",
          "Walk 7",
          "[f1.txt, f2.txt, f3.txt, f4.txt, innerDir, root]",
          "[f1.txt, f2.txt, f3.txt, f4.txt, innerDir, root]");

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

  public FilesVisitTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void test() throws Throwable {
    if (parameters.isCfRuntime() && !ToolHelper.isWindows()) {
      // Reference runtime, we use Jdk 11 since this is Jdk 11 desugared library, not that Jdk 8
      // behaves differently on this test.
      Assume.assumeTrue(parameters.isCfRuntime(CfVm.JDK11) && !ToolHelper.isWindows());
      testForJvm(parameters)
          .addInnerClasses(getClass())
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED_RESULT);
      return;
    }
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .compile()
        .withArt6Plus64BitsLib()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  public static class TestClass {

    private static Path root;

    public static void main(String[] args) throws IOException {
      createDirStructure();
      findTest();
      listTest();
      walkTest();
    }

    /** Creates the following structure root | f1 | f2 | f3 | innerDir | f4 */
    private static void createDirStructure() throws IOException {
      root = Files.createTempDirectory("rootTemp");
      Files.createFile(root.resolve("f1.txt"));
      Files.createFile(root.resolve("f2.txt"));
      Files.createFile(root.resolve("f3.txt"));
      Path innerDir = Files.createDirectory(root.resolve("innerDir"));
      Files.createFile(root.resolve(innerDir.resolve("f4.txt")));
    }

    private static void meaningfulPrint(Stream<Path> paths) {
      System.out.println(
          paths
              .map(f -> f == root ? Paths.get("root") : f)
              .map(Path::getFileName)
              .sorted()
              .collect(Collectors.toList()));
    }

    private static void findTest() throws IOException {
      System.out.println("Find f1");
      meaningfulPrint(Files.find(root, 1, (p, a) -> p.getFileName().toString().contains("f1")));
      meaningfulPrint(Files.find(root, 7, (p, a) -> p.getFileName().toString().contains("f1")));
      meaningfulPrint(
          Files.find(
              root,
              7,
              (p, a) -> p.getFileName().toString().contains("f1"),
              FileVisitOption.FOLLOW_LINKS));

      System.out.println("Find f4");
      meaningfulPrint(Files.find(root, 1, (p, a) -> p.getFileName().toString().contains("f4")));
      meaningfulPrint(Files.find(root, 7, (p, a) -> p.getFileName().toString().contains("f4")));
      meaningfulPrint(
          Files.find(
              root,
              7,
              (p, a) -> p.getFileName().toString().contains("f4"),
              FileVisitOption.FOLLOW_LINKS));
    }

    private static void listTest() throws IOException {
      System.out.println("List");
      meaningfulPrint(Files.list(root));
    }

    private static void walkTest() throws IOException {
      System.out.println("Walk 1");
      meaningfulPrint(Files.walk(root, 1));
      meaningfulPrint(Files.walk(root, 1, FileVisitOption.FOLLOW_LINKS));
      System.out.println("Walk 7");
      meaningfulPrint(Files.walk(root, 7));
      meaningfulPrint(Files.walk(root, 7, FileVisitOption.FOLLOW_LINKS));
    }
  }
}
