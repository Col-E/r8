// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static com.google.common.io.ByteStreams.toByteArray;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ArchiveClassFileProvider;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.ExternalR8TestCompileResult;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
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

/**
 * This test relies on a freshly built build/libs/r8lib_with_deps.jar. If this test fails remove
 * build directory and rebuild r8lib_with_deps by calling test.py or gradle r8libWithdeps.
 */
public class BootstrapCurrentEqualityTest extends TestBase {

  private static final Path MAIN_KEEP = Paths.get("src/main/keep.txt");

  private static final String HELLO_NAME = "hello.Hello";
  private static final String[] KEEP_HELLO = {
    "-keep class " + HELLO_NAME + " {", "  public static void main(...);", "}",
  };

  private static Path r8R8Debug;
  private static Path r8R8Release;

  private static boolean testExternal = true;

  @ClassRule public static TemporaryFolder testFolder = new TemporaryFolder();

  @BeforeClass
  public static void beforeAll() throws Exception {
    r8R8Debug = compileR8(CompilationMode.DEBUG);
    r8R8Release = compileR8(CompilationMode.RELEASE);
  }

  private static Path compileR8(CompilationMode mode) throws Exception {
    // Run R8 on r8.jar.
    Path jar;
    if (testExternal) {
      jar =
          testForExternalR8(newTempFolder(), Backend.CF)
              .useR8WithRelocatedDeps()
              .addProgramFiles(ToolHelper.R8_WITH_RELOCATED_DEPS_JAR)
              .addKeepRuleFiles(MAIN_KEEP)
              .setMode(mode)
              .compile()
              .outputJar();
    } else {
      jar = testFolder.newFolder().toPath().resolve("out.jar");
      R8.run(
          R8Command.builder()
              .setMode(mode)
              .addProgramFiles(ToolHelper.R8_WITH_RELOCATED_DEPS_JAR)
              .addProguardConfigurationFiles(MAIN_KEEP)
              .addLibraryFiles(runtimeJar(Backend.CF))
              .setOutput(jar, OutputMode.ClassFile)
              .build());
    }
    return jar;
  }

  @Test
  public void testRetrace() throws IOException {
    ProcessResult result =
        ToolHelper.runProcess(
            new ProcessBuilder(
                "python",
                Paths.get(ToolHelper.TOOLS_DIR, "test_self_retrace.py").toString(),
                r8R8Release.toString()));
    assertEquals(0, result.exitCode);
  }

  @Test
  public void test() throws Exception {
    Path helloJar = Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "hello" + JAR_EXTENSION);
    ProcessResult runResult = ToolHelper.runJava(helloJar, "hello.Hello");
    assertEquals(0, runResult.exitCode);
    compareR8(helloJar, runResult, KEEP_HELLO, "hello.Hello");
  }

  private void compareR8(Path program, ProcessResult runResult, String[] keep, String... args)
      throws Exception {
    ExternalR8TestCompileResult runR8Debug =
        testForExternalR8(newTempFolder(), Backend.CF)
            .useR8WithRelocatedDeps()
            .addProgramFiles(program)
            .addKeepRules(keep)
            .setMode(CompilationMode.DEBUG)
            .compile();
    assertEquals(runResult.toString(), ToolHelper.runJava(runR8Debug.outputJar(), args).toString());
    ExternalR8TestCompileResult runR8Release =
        testForExternalR8(newTempFolder(), Backend.CF)
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
      Path r8,
      Path program,
      ExternalR8TestCompileResult result,
      String[] keep,
      CompilationMode mode)
      throws Exception {
    ExternalR8TestCompileResult runR8R8 =
        testForExternalR8(newTempFolder(), Backend.CF)
            .useProvidedR8(r8)
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

  private static void assertProgramsEqual(Path expectedJar, Path actualJar) throws Exception {
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

  private static boolean filesAreEqual(Path file1, Path file2) throws IOException {
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
