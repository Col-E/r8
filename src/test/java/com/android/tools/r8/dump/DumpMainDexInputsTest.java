// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dump;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DumpMainDexInputsTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public DumpMainDexInputsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private Path newMainDexListPath() throws IOException {
    Path mainDexPath = temp.newFile("main-dex-list.txt").toPath();
    String mainDexList =
        StringUtils.lines(toMainDexFormat(MainDexFile1.class), toMainDexFormat(MainDexFile2.class));
    Files.write(mainDexPath, mainDexList.getBytes());
    return mainDexPath;
  }

  private static String toMainDexFormat(Class<?> clazz) {
    return clazz.getName().replace(".", "/") + FileUtils.CLASS_EXTENSION;
  }

  @Test
  public void testD8MainDexList() throws Exception {
    Assume.assumeTrue(
        "pre-native-multidex only",
        parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.K));
    Path dumpDir = temp.newFolder().toPath();
    testForD8()
        .setMinApi(parameters.getApiLevel())
        .addInnerClasses(DumpMainDexInputsTest.class)
        .addMainDexListFiles(Collections.singleton(newMainDexListPath()))
        .addMainDexListClasses(MainDexClass1.class, MainDexClass2.class, TestClass.class)
        .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
        .addOptionsModification(options -> options.dumpInputToDirectory = dumpDir.toString())
        .compile()
        .assertAllInfoMessagesMatch(containsString("Dumped compilation inputs to:"))
        .assertAllWarningMessagesMatch(
            containsString(
                "Dumping main dex list resources may have side effects due to I/O on Paths."))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
    verifyDumpDir(dumpDir);
  }

  private void verifyDumpDir(Path dumpDir) throws IOException {
    assertTrue(Files.isDirectory(dumpDir));
    List<Path> paths = Files.walk(dumpDir, 1).collect(toList());
    boolean hasVerified = false;
    for (Path path : paths) {
      if (!path.equals(dumpDir)) {
        // The non-external run here results in assert code calling application read.
        verifyDump(path);
        hasVerified = true;
      }
    }
    assertTrue(hasVerified);
  }

  private void verifyDump(Path dumpFile) throws IOException {
    assertTrue(Files.exists(dumpFile));
    Path unzipped = temp.newFolder().toPath();
    ZipUtils.unzip(dumpFile.toString(), unzipped.toFile());
    assertTrue(Files.exists(unzipped.resolve("r8-version")));
    assertTrue(Files.exists(unzipped.resolve("program.jar")));
    assertTrue(Files.exists(unzipped.resolve("library.jar")));
    assertTrue(Files.exists(unzipped.resolve("main-dex-list.txt")));
    String mainDex = new String(Files.readAllBytes(unzipped.resolve("main-dex-list.txt")));
    List<String> mainDexLines =
        Arrays.stream(mainDex.split("\n")).filter(s -> !s.isEmpty()).collect(toList());
    assertEquals(5, mainDexLines.size());
    List<String> expected =
        Stream.of(
                MainDexFile1.class,
                MainDexFile2.class,
                MainDexClass1.class,
                MainDexClass2.class,
                TestClass.class)
            .map(DumpMainDexInputsTest::toMainDexFormat)
            .collect(toList());
    assertEquals(expected, mainDexLines);
  }

  static class TestClass {
    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }

  static class MainDexClass1 {}

  static class MainDexClass2 {}

  static class MainDexFile1 {}

  static class MainDexFile2 {}

  static class NonMainDex1 {}

  static class NonMainDex2 {}
}
