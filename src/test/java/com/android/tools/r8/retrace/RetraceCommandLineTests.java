// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.Version;
import com.android.tools.r8.retrace.stacktraces.ActualRetraceBotStackTrace;
import com.android.tools.r8.retrace.stacktraces.ActualRetraceBotStackTraceWithInfo;
import com.android.tools.r8.retrace.stacktraces.FoundMethodVerboseStackTrace;
import com.android.tools.r8.retrace.stacktraces.PGStackTrace;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.base.Charsets;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RetraceCommandLineTests {

  private static final boolean testExternal = false;
  private static final String WAITING_MESSAGE =
      "Waiting for stack-trace input..." + StringUtils.LINE_SEPARATOR;

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private static String SMILEY_EMOJI = "\uD83D\uDE00";

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
  public void testInvalidMappingFile() throws IOException {
    Path mappingFile = folder.newFile("mapping.txt").toPath();
    Files.write(mappingFile, "foo.bar.baz <- is invalid mapping".getBytes());
    Path stackTraceFile = folder.newFile("stacktrace.txt").toPath();
    Files.write(stackTraceFile, new byte[0]);
    runAbortTest(
        containsString("Unable to parse mapping file"),
        mappingFile.toString(),
        stackTraceFile.toString());
  }

  @Test
  public void testVerbose() throws IOException {
    FoundMethodVerboseStackTrace stackTrace = new FoundMethodVerboseStackTrace();
    runTest(
        stackTrace.mapping(),
        StringUtils.joinLines(stackTrace.obfuscatedStackTrace()),
        false,
        StringUtils.joinLines(stackTrace.retracedStackTrace()) + StringUtils.LINE_SEPARATOR,
        "--verbose");
  }

  @Test
  public void testVerboseSingleHyphen() throws IOException {
    FoundMethodVerboseStackTrace stackTrace = new FoundMethodVerboseStackTrace();
    runTest(
        stackTrace.mapping(),
        StringUtils.joinLines(stackTrace.obfuscatedStackTrace()),
        false,
        StringUtils.joinLines(stackTrace.retracedStackTrace()) + StringUtils.LINE_SEPARATOR,
        "-verbose");
  }

  @Test
  public void testRegularExpression() throws IOException {
    ActualRetraceBotStackTrace stackTrace = new ActualRetraceBotStackTrace();
    runTest(
        stackTrace.mapping(),
        StringUtils.joinLines(stackTrace.obfuscatedStackTrace()),
        false,
        StringUtils.joinLines(stackTrace.retracedStackTrace()) + StringUtils.LINE_SEPARATOR);
  }

  @Test
  public void testRegularExpressionSingleHyphen() throws IOException {
    ActualRetraceBotStackTrace stackTrace = new ActualRetraceBotStackTrace();
    runTest(
        stackTrace.mapping(),
        StringUtils.joinLines(stackTrace.obfuscatedStackTrace()),
        false,
        StringUtils.joinLines(stackTrace.retracedStackTrace()) + StringUtils.LINE_SEPARATOR);
  }

  @Test
  public void testRegularExpressionWithInfo() throws IOException {
    ActualRetraceBotStackTraceWithInfo stackTrace = new ActualRetraceBotStackTraceWithInfo();
    runTest(
        stackTrace.mapping(),
        StringUtils.joinLines(stackTrace.obfuscatedStackTrace()),
        false,
        StringUtils.joinLines(stackTrace.retracedStackTrace()) + StringUtils.LINE_SEPARATOR,
        "--info");
  }

  @Test
  public void testPGStackTrace() throws Exception {
    PGStackTrace pgStackTrace = new PGStackTrace();
    runTest(
        pgStackTrace.mapping(),
        StringUtils.joinLines(pgStackTrace.obfuscatedStackTrace()),
        false,
        StringUtils.joinLines(pgStackTrace.retracedStackTrace()) + StringUtils.LINE_SEPARATOR);
  }

  @Test
  public void testEmpty() throws IOException {
    runTest("", "", false, "");
  }

  @Test
  public void testHelp() throws IOException {
    ProcessResult processResult = runRetraceCommandLine(null, Arrays.asList("--help"));
    assertEquals(0, processResult.exitCode);
    assertEquals(Retrace.USAGE_MESSAGE, processResult.stdout);
  }

  @Test
  public void testVersion() throws Exception {
    ProcessResult processResult = runRetraceCommandLine(null, Arrays.asList("--version"));
    assertEquals(0, processResult.exitCode);
    assertEquals(StringUtils.lines("Retrace " + Version.getVersionString()), processResult.stdout);
  }

  @Test
  public void testNonAscii() throws IOException {
    runTest("", SMILEY_EMOJI, false, SMILEY_EMOJI + StringUtils.LINE_SEPARATOR);
  }

  @Test
  public void testNonAsciiStdIn() throws IOException {
    runTest("", SMILEY_EMOJI, true, SMILEY_EMOJI + StringUtils.LINE_SEPARATOR);
  }

  @Test
  public void testHelpMessageOnStdIn() throws IOException {
    ProcessResult processResult = runRetrace("", "", true);
    assertTrue(processResult.stdout.startsWith(WAITING_MESSAGE));
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

  private void runTest(
      String mapping, String stackTrace, boolean stacktraceStdIn, String expected, String... args)
      throws IOException {
    ProcessResult result = runRetrace(mapping, stackTrace, stacktraceStdIn, args);
    assertEquals(0, result.exitCode);
    String stdOut = result.stdout;
    if (stacktraceStdIn) {
      assertTrue(result.stdout.startsWith(WAITING_MESSAGE));
      stdOut = result.stdout.substring(WAITING_MESSAGE.length());
    }
    assertEquals(expected, stdOut);
  }

  private void runAbortTest(Matcher<String> errorMatch, String... args) throws IOException {
    ProcessResult result = runRetraceCommandLine(null, Arrays.asList(args));
    assertEquals(1, result.exitCode);
    assertThat(result.stderr, errorMatch);
  }

  private ProcessResult runRetrace(
      String mapping, String stackTrace, boolean stacktraceStdIn, String... additionalArgs)
      throws IOException {
    Path mappingFile = folder.newFile("mapping.txt").toPath();
    Files.write(mappingFile, mapping.getBytes());
    File stackTraceFile = folder.newFile("stacktrace.txt");
    Files.write(stackTraceFile.toPath(), stackTrace.getBytes(StandardCharsets.UTF_8));

    Collection<String> args = new ArrayList<>();
    args.add(mappingFile.toString());
    if (!stacktraceStdIn) {
      args.add(stackTraceFile.toPath().toString());
    }
    args.addAll(Arrays.asList(additionalArgs));
    return runRetraceCommandLine(stacktraceStdIn ? stackTraceFile : null, args);
  }

  private ProcessResult runRetraceCommandLine(File stdInput, Collection<String> args)
      throws IOException {
    if (testExternal) {
      List<String> command = new ArrayList<>();
      command.add(ToolHelper.getSystemJavaExecutable());
      command.add("-ea");
      command.add("-cp");
      command.add(ToolHelper.R8_JAR.toString());
      command.add("com.android.tools.r8.retrace.Retrace");
      command.addAll(args);
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
        String[] strArgs = new String[0];
        strArgs = args.toArray(strArgs);
        Retrace.run(strArgs);
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
          outputByteStream.toString(Charsets.UTF_8.name()),
          errorByteStream.toString(Charsets.UTF_8.name()),
          StringUtils.joinLines(args));
    }
  }
}
