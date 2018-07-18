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
import com.android.tools.r8.utils.FileUtils;
import com.google.common.base.Charsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class BootstrapTest extends TestBase {

  private static final Path R8_STABLE_JAR = Paths.get("third_party/r8/r8.jar");

  private static final String R8_NAME = "com.android.tools.r8.R8";
  private static final String[] KEEP_R8 = {
      "-keep public class " + R8_NAME + " {",
      "  public static void main(...);",
      "}",
  };

  private static final String HELLO_NAME = "hello.Hello";
  private static final String[] KEEP_HELLO = {
      "-keep class " + HELLO_NAME + " {",
      "  public static void main(...);",
      "}",
  };

  private class R8Result {

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

  @Test
  public void test() throws Exception {
    // Run hello.jar to ensure it exists and is valid.
    Path hello = Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "hello" + JAR_EXTENSION);
    ProcessResult runHello = ToolHelper.runJava(hello, "hello.Hello");
    assertEquals(0, runHello.exitCode);

    // Run r8.jar on hello.jar to ensure that r8.jar is a working compiler.
    R8Result runInputR8 = runExternalR8(R8_STABLE_JAR, hello, "input", KEEP_HELLO, "--debug");
    ProcessResult runHelloR8 = ToolHelper.runJava(runInputR8.outputJar, "hello.Hello");
    assertEquals(runHello.toString(), runHelloR8.toString());

    compareR8(hello, runInputR8, CompilationMode.RELEASE, "r8-r8-rel", "--release", "output-rel");
    compareR8(hello, runInputR8, CompilationMode.DEBUG, "r8-r8", "--debug", "output");
  }

  private void compareR8(
      Path hello,
      R8Result runInputR8,
      CompilationMode internalMode,
      String internalOutput,
      String externalMode,
      String externalOutput)
      throws Exception {
    // Run R8 on r8.jar, and run the resulting compiler on hello.jar.
    Path output = runR8(R8_STABLE_JAR, internalOutput, KEEP_R8, internalMode);
    R8Result runR8R8 = runExternalR8(output, hello, externalOutput, KEEP_HELLO, externalMode);
    // Check that the process outputs (exit code, stdout, stderr) are the same.
    assertEquals(runInputR8.toString(), runR8R8.toString());
    // Check that the output jars are the same.
    assertProgramsEqual(runInputR8.outputJar, runR8R8.outputJar);
  }

  private Path runR8(Path inputJar, String outputFolder, String[] keepRules, CompilationMode mode)
      throws Exception {
    Path outputPath = temp.newFolder(outputFolder).toPath();
    Path outputJar = outputPath.resolve("output.jar");
    Path pgConfigFile = outputPath.resolve("keep.rules");
    FileUtils.writeTextFile(pgConfigFile, keepRules);
    ToolHelper.runR8(
        R8Command.builder()
            .setMode(mode)
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .setProgramConsumer(new ClassFileConsumer.ArchiveConsumer(outputJar, true))
            .addProgramFiles(inputJar)
            .addProguardConfigurationFiles(pgConfigFile)
            .build());
    return outputJar;
  }

  private R8Result runExternalR8(
      Path r8Jar, Path inputJar, String outputFolder, String[] keepRules, String mode)
      throws Exception {
    Path outputPath = temp.newFolder(outputFolder).toPath();
    Path pgConfigFile = outputPath.resolve("keep.rules");
    Path outputJar = outputPath.resolve("output.jar");
    Path pgMapFile = outputPath.resolve("map.txt");
    FileUtils.writeTextFile(pgConfigFile, keepRules);
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
            pgConfigFile.toString(),
            mode,
            "--pg-map-output",
            pgMapFile.toString());
    if (processResult.exitCode != 0) {
      System.out.println(processResult);
    }
    assertEquals(0, processResult.exitCode);
    String pgMap = FileUtils.readTextFile(pgMapFile, Charsets.UTF_8);
    return new R8Result(processResult, outputJar, pgMap);
  }

  private static void assertProgramsEqual(Path expectedJar, Path actualJar) throws Exception {
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
