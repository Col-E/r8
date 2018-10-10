// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static com.google.common.io.ByteStreams.toByteArray;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ArchiveClassFileProvider;
import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassHierarchyVerifier;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.base.Charsets;
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
 * This test relies on a freshly built from builds/libs/r8lib_with_deps.jar. If this test fails
 * rebuild r8lib_with_deps by calling test.py or gradle r8libWithdeps.
 */
public class BootstrapCurrentEqualityTest extends TestBase {

  private static final String R8_NAME = "com.android.tools.r8.R8";
  private static final Path MAIN_KEEP = Paths.get("src/main/keep.txt");

  private static final String HELLO_NAME = "hello.Hello";
  private static final String[] KEEP_HELLO = {
    "-keep class " + HELLO_NAME + " {", "  public static void main(...);", "}",
  };

  private static class R8Result {

    final ProcessResult processResult;
    final Path outputJar;
    final String pgMap;

    R8Result(ProcessResult processResult, Path outputJar, String pgMap) {
      this.processResult = processResult;
      this.outputJar = outputJar;
      this.pgMap = pgMap;
    }

    @Override
    public String toString() {
      return processResult.toString() + "\n\n" + pgMap;
    }
  }

  private static Path r8R8Debug;
  private static Path r8R8Release;

  @ClassRule public static TemporaryFolder testFolder = new TemporaryFolder();

  @BeforeClass
  public static void beforeAll() throws Exception {
    r8R8Debug = compileR8("--debug");
    r8R8Release = compileR8("--release");
  }

  private static Path compileR8(String mode) throws Exception {
    // Run R8 on r8.jar.
    R8Result output = runExternalR8(
        ToolHelper.R8_LIB_JAR, ToolHelper.R8_LIB_JAR, testFolder.newFolder().toPath(), MAIN_KEEP, mode);
    // Check that all non-abstract classes in the R8'd R8 implement all abstract/interface methods
    // from their supertypes. This is a sanity check for the tree shaking and minification.
    AndroidApp app = AndroidApp.builder().addProgramFile(output.outputJar).build();
    new ClassHierarchyVerifier(new CodeInspector(app)).run();
    return output.outputJar;
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
    R8Result runR8Debug =
        runExternalR8(ToolHelper.R8_LIB_JAR, program, temp.newFolder().toPath(), keep, "--debug");
    assertEquals(runResult.toString(), ToolHelper.runJava(runR8Debug.outputJar, args).toString());
    R8Result runR8Release =
        runExternalR8(ToolHelper.R8_LIB_JAR, program, temp.newFolder().toPath(), keep, "--release");
    assertEquals(runResult.toString(), ToolHelper.runJava(runR8Release.outputJar, args).toString());
    RunR8AndCheck(r8R8Debug, program, runR8Debug, keep, "--debug");
    RunR8AndCheck(r8R8Debug, program, runR8Release, keep, "--release");
    RunR8AndCheck(r8R8Release, program, runR8Debug, keep, "--debug");
    RunR8AndCheck(r8R8Release, program, runR8Release, keep, "--release");
  }

  private void RunR8AndCheck(Path r8, Path program, R8Result result, String[] keep, String mode)
      throws Exception {
    R8Result runR8R8 = runExternalR8(r8, program, temp.newFolder().toPath(), keep, mode);
    // Check that the process outputs (exit code, stdout, stderr) are the same.
    assertEquals(result.toString(), runR8R8.toString());
    // Check that the output jars are the same.
    assertProgramsEqual(result.outputJar, runR8R8.outputJar);
  }

  private static R8Result runExternalR8(
      Path r8Jar, Path inputJar, Path output, String[] keepRules, String mode) throws Exception {
    Path pgConfigFile = output.resolve("keep.rules");
    FileUtils.writeTextFile(pgConfigFile, keepRules);
    return runExternalR8(r8Jar, inputJar, output, pgConfigFile, mode);
  }

  private static R8Result runExternalR8(
      Path r8Jar, Path inputJar, Path output, Path keepRules, String mode) throws Exception {
    Path outputJar = output.resolve("output.jar");
    Path pgMapFile = output.resolve("map.txt");
    ProcessResult processResult =
        ToolHelper.runJava(
            r8Jar,
            R8_NAME,
            "--lib",
            ToolHelper.JAVA_8_RUNTIME,
            "--classfile",
            inputJar.toString(),
            "--output",
            outputJar.toString(),
            "--pg-conf",
            keepRules.toString(),
            mode,
            "--pg-map-output",
            pgMapFile.toString());
    assertEquals(0, processResult.exitCode);
    String pgMap = FileUtils.readTextFile(pgMapFile, Charsets.UTF_8);
    return new R8Result(processResult, outputJar, pgMap);
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
}
