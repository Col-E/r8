// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.bootstrap;

import static com.android.tools.r8.graph.GenericSignatureIdentityTest.testParseSignaturesInJar;
import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static com.google.common.io.ByteStreams.toByteArray;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.ArchiveClassFileProvider;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.ExternalR8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.retrace.Retrace;
import com.android.tools.r8.retrace.RetraceCommand;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.Lists;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * This test relies on a freshly built build/libs/r8lib_with_deps.jar. If this test fails remove
 * build directory and rebuild r8lib_with_deps by calling test.py or gradle r8libWithdeps.
 */
@RunWith(Parameterized.class)
public class BootstrapCurrentEqualityTest extends TestBase {

  private static final Path MAIN_KEEP = Paths.get("src/main/keep.txt");

  private static final String HELLO_NAME = "hello.Hello";
  private static final String[] KEEP_HELLO = {
    "-keep class " + HELLO_NAME + " {", "  public static void main(...);", "}",
  };

  private static Pair<Path, Path> r8R8Debug;
  private static Pair<Path, Path> r8R8Release;

  private final TestParameters parameters;
  private static boolean testExternal = false;

  @ClassRule public static TemporaryFolder testFolder = new TemporaryFolder();

  @BeforeClass
  public static void beforeAll() throws Exception {
    assertThrowsWithHorizontalClassMerging(
        () -> {
          if (data().stream().count() > 0) {
            r8R8Debug = compileR8(CompilationMode.DEBUG);
            r8R8Release = compileR8(CompilationMode.RELEASE);
          }
        });
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  public BootstrapCurrentEqualityTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static Pair<Path, Path> compileR8(CompilationMode mode) throws Exception {
    // Run R8 on r8.jar.
    final Path jar = testFolder.newFolder().toPath().resolve("out.jar");
    final Path map = testFolder.newFolder().toPath().resolve("out.map");
    if (testExternal) {
      testForExternalR8(newTempFolder(), Backend.CF, TestRuntime.getCheckedInJdk9())
          .useR8WithRelocatedDeps()
          .setMode(mode)
          .addProgramFiles(ToolHelper.R8_WITH_RELOCATED_DEPS_JAR)
          .addKeepRuleFiles(MAIN_KEEP)
          .compile()
          .apply(c -> FileUtils.writeTextFile(map, c.getProguardMap()))
          .writeToZip(jar);
    } else {
      testForR8(newTempFolder(), Backend.CF)
          .setMode(mode)
          .addProgramFiles(ToolHelper.R8_WITH_RELOCATED_DEPS_JAR)
          .addKeepRuleFiles(MAIN_KEEP)
          .allowDiagnosticInfoMessages(mode == CompilationMode.DEBUG)
          .compile()
          .assertAllInfoMessagesMatch(
              anyOf(
                  containsString("Stripped invalid locals information from 1 method."),
                  containsString("Methods with invalid locals information:"),
                  containsString(
                      "Some warnings are typically a sign of using an outdated Java toolchain.")))
          .apply(c -> FileUtils.writeTextFile(map, c.getProguardMap()))
          .writeToZip(jar);
    }
    return new Pair<>(jar, map);
  }

  @Test
  public void testRetrace() throws IOException {
    expectThrowsWithHorizontalClassMerging();
    ProcessResult processResult =
        ToolHelper.runProcess(
            new ProcessBuilder()
                .command(
                    parameters.getRuntime().asCf().getJavaExecutable().toString(),
                    "-DR8_THROW_EXCEPTION_FOR_TESTING_RETRACE=1",
                    "-cp",
                    r8R8Release.getFirst().toString(),
                    "com.android.tools.r8.R8",
                    "--help"));
    assertNotEquals(0, processResult.exitCode);
    assertThat(processResult.stderr, not(containsString("SelfRetraceTest")));

    List<String> expectedStackTrace =
        Lists.newArrayList(
            "Intentional exception for testing retrace.",
            "com.android.tools.r8.utils.SelfRetraceTest.foo3(SelfRetraceTest.java:13)",
            "com.android.tools.r8.utils.SelfRetraceTest.foo2(SelfRetraceTest.java:17)",
            "com.android.tools.r8.utils.SelfRetraceTest.foo1(SelfRetraceTest.java:21)",
            "com.android.tools.r8.utils.SelfRetraceTest.test(SelfRetraceTest.java:26)",
            "com.android.tools.r8.R8.run(R8.java:");

    RetraceCommand retraceCommand =
        RetraceCommand.builder()
            .setStackTrace(StringUtils.splitLines(processResult.stderr))
            .setProguardMapProducer(
                () -> {
                  Path mappingFile = r8R8Release.getSecond();
                  try {
                    return new String(Files.readAllBytes(mappingFile));
                  } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException(
                        "Could not read mapping file " + mappingFile.toString());
                  }
                })
            .setRetracedStackTraceConsumer(
                retraced -> {
                  int expectedIndex = -1;
                  for (String line : retraced) {
                    if (expectedIndex >= expectedStackTrace.size()) {
                      break;
                    } else if (expectedIndex == -1 && line.contains("java.lang.RuntimeException")) {
                      expectedIndex = 0;
                    }
                    if (expectedIndex > -1) {
                      assertThat(line, containsString(expectedStackTrace.get(expectedIndex++)));
                    }
                  }
                  assertEquals(expectedStackTrace.size(), expectedIndex);
                })
            .build();
    Retrace.run(retraceCommand);
  }

  @Test
  public void testR8LibCompatibility() throws IOException, CompilationFailedException {
    // Produce r81 = R8Lib(R8WithDeps) and r82 = R8LibNoDeps + Deps(R8WithDeps) and test that r81 is
    // equal to r82. This test should only run if we are testing r8lib and we expect both R8libs to
    // be built by gradle. If we are not testing with R8Lib, do not run this test.
    if (!ToolHelper.isTestingR8Lib()) {
      return;
    }
    Path runR81 =
        testForExternalR8(parameters.getBackend(), parameters.getRuntime())
            .useProvidedR8(ToolHelper.R8LIB_JAR)
            .addProgramFiles(ToolHelper.R8_WITH_RELOCATED_DEPS_JAR)
            .addKeepRuleFiles(MAIN_KEEP)
            .setMode(CompilationMode.RELEASE)
            .compile()
            .outputJar();
    Path runR82 =
        testForExternalR8(parameters.getBackend(), parameters.getRuntime())
            .useProvidedR8(ToolHelper.R8LIB_EXCLUDE_DEPS_JAR)
            .addR8ExternalDepsToClasspath()
            .addProgramFiles(ToolHelper.R8_WITH_RELOCATED_DEPS_JAR)
            .addKeepRuleFiles(MAIN_KEEP)
            .setMode(CompilationMode.RELEASE)
            .compile()
            .outputJar();
    assert filesAreEqual(runR81, runR82);
  }

  @Test
  public void testSignatures() throws Exception {
    expectThrowsWithHorizontalClassMerging();
    testParseSignaturesInJar(r8R8Release.getFirst());
  }

  @Test
  public void test() throws Exception {
    expectThrowsWithHorizontalClassMerging();
    Path helloJar = Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "hello" + JAR_EXTENSION);
    ProcessResult runResult = ToolHelper.runJava(helloJar, "hello.Hello");
    assertEquals(0, runResult.exitCode);
    compareR8(helloJar, runResult, KEEP_HELLO, "hello.Hello");
  }

  private void compareR8(Path program, ProcessResult runResult, String[] keep, String... args)
      throws Exception {
    ExternalR8TestCompileResult runR8Debug =
        testForExternalR8(newTempFolder(), parameters.getBackend(), parameters.getRuntime())
            .useR8WithRelocatedDeps()
            .addProgramFiles(program)
            .addKeepRules(keep)
            .setMode(CompilationMode.DEBUG)
            .compile();
    assertEquals(runResult.toString(), ToolHelper.runJava(runR8Debug.outputJar(), args).toString());
    ExternalR8TestCompileResult runR8Release =
        testForExternalR8(newTempFolder(), parameters.getBackend(), parameters.getRuntime())
            .useR8WithRelocatedDeps()
            .addProgramFiles(program)
            .addKeepRules(keep)
            .setMode(CompilationMode.RELEASE)
            .compile();
    assertEquals(
        runResult.toString(), ToolHelper.runJava(runR8Release.outputJar(), args).toString());
    RunR8AndCheck(r8R8Debug, program, runR8Debug, keep, CompilationMode.DEBUG);
    RunR8AndCheck(r8R8Debug, program, runR8Release, keep, CompilationMode.RELEASE);
    RunR8AndCheck(r8R8Release, program, runR8Debug, keep, CompilationMode.DEBUG);
    RunR8AndCheck(r8R8Release, program, runR8Release, keep, CompilationMode.RELEASE);
  }

  private void RunR8AndCheck(
      Pair<Path, Path> r8,
      Path program,
      ExternalR8TestCompileResult result,
      String[] keep,
      CompilationMode mode)
      throws Exception {
    ExternalR8TestCompileResult runR8R8 =
        testForExternalR8(newTempFolder(), parameters.getBackend(), parameters.getRuntime())
            .useProvidedR8(r8.getFirst())
            .addProgramFiles(program)
            .addKeepRules(keep)
            .setMode(mode)
            .compile();
    // Check that the process outputs (exit code, stdout, stderr) are the same.
    assertEquals(result.stdout(), runR8R8.stdout());
    assertEquals(result.stderr(), runR8R8.stderr());
    // Check that the output jars are the same.
    assertProgramsEqual(result.outputJar(), runR8R8.outputJar());
  }

  public static void assertProgramsEqual(Path expectedJar, Path actualJar) throws Exception {
    if (filesAreEqual(expectedJar, actualJar)) {
      return;
    }
    ArchiveClassFileProvider expected = new ArchiveClassFileProvider(expectedJar);
    ArchiveClassFileProvider actual = new ArchiveClassFileProvider(actualJar);
    assertEquals(getSortedDescriptorList(expected), getSortedDescriptorList(actual));
    for (String descriptor : expected.getClassDescriptors()) {
      assertArrayEquals(
          "Class " + descriptor + " differs",
          getClassAsBytes(expected, descriptor),
          getClassAsBytes(actual, descriptor));
    }
  }

  public static boolean filesAreEqual(Path file1, Path file2) throws IOException {
    long size = Files.size(file1);
    long sizeOther = Files.size(file2);
    if (size != sizeOther) {
      return false;
    }
    if (size < 4096) {
      return Arrays.equals(Files.readAllBytes(file1), Files.readAllBytes(file2));
    }
    int byteRead1 = 0;
    int byteRead2 = 0;
    try (FileInputStream fs1 = new FileInputStream(file1.toString());
        FileInputStream fs2 = new FileInputStream(file2.toString())) {
      BufferedInputStream bs1 = new BufferedInputStream(fs1);
      BufferedInputStream bs2 = new BufferedInputStream(fs2);
      while (byteRead1 == byteRead2 && byteRead1 != -1) {
        byteRead1 = bs1.read();
        byteRead2 = bs2.read();
      }
    }
    return byteRead1 == byteRead2;
  }

  private static List<String> getSortedDescriptorList(ArchiveClassFileProvider inputJar) {
    ArrayList<String> descriptorList = new ArrayList<>(inputJar.getClassDescriptors());
    Collections.sort(descriptorList);
    return descriptorList;
  }

  private static byte[] getClassAsBytes(ArchiveClassFileProvider inputJar, String descriptor)
      throws Exception {
    return toByteArray(inputJar.getProgramResource(descriptor).getByteStream());
  }

  private static TemporaryFolder newTempFolder() throws IOException {
    TemporaryFolder tempFolder = new TemporaryFolder(testFolder.newFolder());
    tempFolder.create();
    return tempFolder;
  }
}
