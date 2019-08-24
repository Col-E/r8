// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.ToolHelper.LINE_SEPARATOR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.StringUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RetraceCommandLineTests {

  private static final boolean testExternal = true;

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void testPrintIdentityStackTraceFile() throws IOException {
    runTest("", nonMappableStackTrace, false, nonMappableStackTrace);
  }

  @Test
  public void testPrintIdentityStackTraceInput() throws IOException {
    runTest("", nonMappableStackTrace, true, nonMappableStackTrace);
  }

  @Test
  public void testNoMappingFileSpecified() throws IOException {
    runAbortTest(containsString("Mapping file not specified"));
  }

  @Test
  public void testMissingMappingFile() throws IOException {
    runAbortTest(containsString("Could not find mapping file 'foo.txt'"), "foo.txt");
  }

  @Test
  public void testVerbose() throws IOException {
    runAbortTest(containsString("Currently no support for --verbose"), "--verbose");
  }

  @Test
  public void testEmpty() throws IOException {
    runTest("", "", false, "");
  }

  @Test
  public void testHelp() throws IOException {
    ProcessResult processResult = runRetraceCommandLine(null, "--help");
    assertEquals(0, processResult.exitCode);
    assertEquals(Retrace.USAGE_MESSAGE, processResult.stdout);
  }

  private final String nonMappableStackTrace =
      StringUtils.lines(
          "com.android.r8.R8Exception: Problem when compiling program",
          "    at r8.a.a(App:42)",
          "    at r8.a.b(App:10)",
          "    at r8.a.c(App:266)",
          "    at r8.main(App:800)",
          "Caused by: com.android.r8.R8InnerException: You have to write the program first",
          "    at r8.retrace(App:184)",
          "    ... 7 more");

  private void runTest(String mapping, String stackTrace, boolean stacktraceStdIn, String expected)
      throws IOException {
    ProcessResult result = runRetrace(mapping, stackTrace, stacktraceStdIn);
    assertEquals(0, result.exitCode);
    assertEquals(expected, result.stdout);
  }

  private void runAbortTest(Matcher<String> errorMatch, String... args) throws IOException {
    ProcessResult result = runRetraceCommandLine(null, args);
    assertEquals(1, result.exitCode);
    assertThat(result.stderr, errorMatch);
  }

  private ProcessResult runRetrace(String mapping, String stackTrace, boolean stacktraceStdIn)
      throws IOException {
    Path mappingFile = folder.newFile("mapping.txt").toPath();
    Files.write(mappingFile, mapping.getBytes());
    File stackTraceFile = folder.newFile("stacktrace.txt");
    Files.write(stackTraceFile.toPath(), stackTrace.getBytes());
    if (stacktraceStdIn) {
      return runRetraceCommandLine(stackTraceFile, mappingFile.toString());
    } else {
      return runRetraceCommandLine(
          null, mappingFile.toString(), stackTraceFile.toPath().toString());
    }
  }

  private ProcessResult runRetraceCommandLine(File stdInput, String... args) throws IOException {
    if (testExternal) {
      List<String> command = new ArrayList<>();
      command.add(ToolHelper.getSystemJavaExecutable());
      command.add("-ea");
      command.add("-cp");
      command.add(ToolHelper.R8_JAR.toString());
      command.add("com.android.tools.r8.retrace.Retrace");
      command.addAll(Arrays.asList(args));
      ProcessBuilder builder = new ProcessBuilder(command);
      if (stdInput != null) {
        builder.redirectInput(stdInput);
      }
      return ToolHelper.runProcess(builder);
    } else {
      InputStream originalIn = System.in;
      PrintStream originalOut = System.out;
      PrintStream originalErr = System.err;
      if (stdInput != null) {
        System.setIn(new FileInputStream(stdInput));
      }
      ByteArrayOutputStream outputByteStream = new ByteArrayOutputStream();
      System.setOut(new PrintStream(outputByteStream));
      ByteArrayOutputStream errorByteStream = new ByteArrayOutputStream();
      System.setErr(new PrintStream(errorByteStream));
      int exitCode = 0;
      try {
        Retrace.run(args);
      } catch (Throwable t) {
        exitCode = 1;
      }
      if (originalIn != null) {
        System.setIn(originalIn);
      }
      System.setOut(originalOut);
      System.setErr(originalErr);
      return new ProcessResult(
          exitCode,
          outputByteStream.toString(),
          errorByteStream.toString(),
          StringUtils.join(LINE_SEPARATOR, args));
    }
  }
}
