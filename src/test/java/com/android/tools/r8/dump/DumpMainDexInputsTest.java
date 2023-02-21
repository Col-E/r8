// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dump;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DumpMainDexInputsTest extends TestBase {

  private final TestParameters parameters;
  private final String mainDexRulesForMainDexFile1AndMainDexFile2 =
      StringUtils.lines(
          "-keep class " + MainDexFile1.class.getTypeName() + " {",
          "  *;",
          "}",
          "-keep class " + MainDexFile2.class.getTypeName() + " {",
          "  *;",
          "}");
  private final String mainDexRulesForMainDexFile3 =
      "-keep class " + MainDexFile3.class.getTypeName();

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public DumpMainDexInputsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private Path mainDexListForMainDexFile1AndMainDexFile2() throws IOException {
    Path mainDexPath = temp.newFile("mylist1.txt").toPath();
    String mainDexList =
        StringUtils.lines(toMainDexFormat(MainDexFile1.class), toMainDexFormat(MainDexFile2.class));
    Files.write(mainDexPath, mainDexList.getBytes());
    return mainDexPath;
  }

  private Path mainDexListForMainDexFile3() throws IOException {
    Path mainDexPath = temp.newFile("mylist2.txt").toPath();
    String mainDexList = StringUtils.lines(toMainDexFormat(MainDexFile3.class));
    Files.write(mainDexPath, mainDexList.getBytes());
    return mainDexPath;
  }

  private Path newMainDexRulesPath1() throws IOException {
    Path mainDexPath = temp.newFile("myrules1.txt").toPath();
    Files.write(mainDexPath, mainDexRulesForMainDexFile1AndMainDexFile2.getBytes());
    return mainDexPath;
  }

  private Path newMainDexRulesPath2() throws IOException {
    Path mainDexPath = temp.newFile("myrules2.txt").toPath();
    Files.write(mainDexPath, mainDexRulesForMainDexFile3.getBytes());
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
    // Pre-compile to DEX and do main-dex list on DEX inputs only.
    Path dexed =
        testForD8()
            .addInnerClasses(DumpMainDexInputsTest.class)
            .setMinApi(parameters)
            .compile()
            .writeToZip();

    testForD8()
        .addProgramFiles(dexed)
        .setMinApi(parameters)
        .addMainDexListFiles(
            mainDexListForMainDexFile1AndMainDexFile2(), mainDexListForMainDexFile3())
        .addMainDexListClasses(MainDexClass1.class, MainDexClass2.class, TestClass.class)
        .addOptionsModification(
            options -> options.setDumpInputFlags(DumpInputFlags.dumpToDirectory(dumpDir)))
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics
                    .assertNoErrors()
                    .assertInfosMatch(
                        diagnosticMessage(containsString("Dumped compilation inputs to:")))
                    .assertWarningsMatch(
                        diagnosticMessage(
                            containsString(
                                "Dumping main dex list resources may have side effects due to I/O"
                                    + " on Paths."))))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
    verifyDumpDir(dumpDir, true);
  }

  @Test
  public void testD8MainDexRules() throws Exception {
    Assume.assumeTrue(
        "pre-native-multidex only",
        parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.K));
    Path dumpDir = temp.newFolder().toPath();
    testForD8()
        .setMinApi(parameters)
        .addInnerClasses(DumpMainDexInputsTest.class)
        .addMainDexRulesFiles(newMainDexRulesPath1(), newMainDexRulesPath2())
        .addMainDexKeepClassRules(MainDexClass1.class, MainDexClass2.class, TestClass.class)
        .addOptionsModification(
            options -> options.setDumpInputFlags(DumpInputFlags.dumpToDirectory(dumpDir)))
        .compile()
        .assertAllInfoMessagesMatch(containsString("Dumped compilation inputs to:"))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
    verifyDumpDir(dumpDir, false);
  }

  @Test
  public void testR8MainDexRules() throws Exception {
    Assume.assumeTrue(
        "pre-native-multidex only",
        parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.K));
    Path dumpDir = temp.newFolder().toPath();
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addInnerClasses(DumpMainDexInputsTest.class)
        .addMainDexRuleFiles(newMainDexRulesPath1(), newMainDexRulesPath2())
        .addMainDexKeepClassRules(MainDexClass1.class, MainDexClass2.class, TestClass.class)
        .addOptionsModification(
            options -> options.setDumpInputFlags(DumpInputFlags.dumpToDirectory(dumpDir)))
        .addKeepAllClassesRule()
        .allowDiagnosticMessages()
        .compile()
        .assertAllInfoMessagesMatch(containsString("Dumped compilation inputs to:"))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
    verifyDumpDir(dumpDir, false);
  }

  private void verifyDumpDir(Path dumpDir, boolean checkMainDexList) throws IOException {
    assertTrue(Files.isDirectory(dumpDir));
    List<Path> paths = Files.walk(dumpDir, 1).collect(toList());
    boolean hasVerified = false;
    for (Path path : paths) {
      if (!path.equals(dumpDir)) {
        // The non-external run here results in assert code calling application read.
        verifyDump(path, checkMainDexList);
        hasVerified = true;
      }
    }
    assertTrue(hasVerified);
  }

  private void verifyDump(Path dumpFile, boolean checkMainDexList) throws IOException {
    assertTrue(Files.exists(dumpFile));
    Path unzipped = temp.newFolder().toPath();
    ZipUtils.unzip(dumpFile.toString(), unzipped.toFile());
    assertTrue(Files.exists(unzipped.resolve("r8-version")));
    assertTrue(Files.exists(unzipped.resolve("program.jar")));
    assertTrue(Files.exists(unzipped.resolve("library.jar")));
    if (checkMainDexList) {
      Path mainDexListFile = unzipped.resolve("main-dex-list.txt");
      assertTrue(Files.exists(mainDexListFile));
      String mainDex = new String(Files.readAllBytes(mainDexListFile));
      List<String> mainDexLines =
          Arrays.stream(mainDex.split("\n")).filter(s -> !s.isEmpty()).collect(toList());
      assertEquals(6, mainDexLines.size());
      List<String> expected =
          Stream.of(
                  MainDexFile1.class,
                  MainDexFile2.class,
                  MainDexFile3.class,
                  MainDexClass1.class,
                  MainDexClass2.class,
                  TestClass.class)
              .map(DumpMainDexInputsTest::toMainDexFormat)
              .collect(toList());
      assertEquals(expected, mainDexLines);
    } else {
      Path mainDexRulesFile = unzipped.resolve("main-dex-rules.txt");
      assertTrue(Files.exists(mainDexRulesFile));
      String mainDexRules = new String(Files.readAllBytes(mainDexRulesFile));
      assertThat(mainDexRules, containsString(mainDexRulesForMainDexFile1AndMainDexFile2));
      assertThat(mainDexRules, containsString(mainDexRulesForMainDexFile3));
    }
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

  static class MainDexFile3 {}

  static class NonMainDex1 {}

  static class NonMainDex2 {}
}
