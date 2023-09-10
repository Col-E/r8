// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.Version;
import com.android.tools.r8.retrace.stacktraces.ActualRetraceBotStackTrace;
import com.android.tools.r8.retrace.stacktraces.ActualRetraceBotStackTraceWithInfo;
import com.android.tools.r8.retrace.stacktraces.FoundMethodVerboseStackTrace;
import com.android.tools.r8.retrace.stacktraces.PGStackTrace;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceCommandLineTests extends TestBase {

  private static final String SMILEY_EMOJI = "\uD83D\uDE00";

  private static final String WAITING_MESSAGE =
      "Waiting for stack-trace input..." + StringUtils.LINE_SEPARATOR;

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private final boolean testExternal;
  private final boolean testPartition;

  @Parameters(name = "external: {0}, partition: {1}")
  public static List<Object[]> data() {
    return buildParameters(BooleanUtils.values(), BooleanUtils.values());
  }

  public RetraceCommandLineTests(boolean testExternal, boolean testPartition) {
    this.testExternal = testExternal;
    this.testPartition = testPartition;
  }

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
  public void testMissingStackTraceFile() throws IOException {
    Path mappingFile = folder.newFile("mapping.txt").toPath();
    Files.write(mappingFile, "foo.bar.baz -> foo:".getBytes());
    runAbortTest(containsString("NoSuchFileException"), mappingFile.toString(), "stacktrace.txt");
  }

  @Test
  public void testVerbose() throws IOException {
    FoundMethodVerboseStackTrace stackTrace = new FoundMethodVerboseStackTrace();
    runTest(
        stackTrace.mapping(),
        StringUtils.joinLines(stackTrace.obfuscatedStackTrace()),
        false,
        StringUtils.joinLines(stackTrace.retraceVerboseStackTrace()) + StringUtils.LINE_SEPARATOR,
        "--verbose");
  }

  @Test
  public void testVerboseSingleHyphen() throws IOException {
    FoundMethodVerboseStackTrace stackTrace = new FoundMethodVerboseStackTrace();
    runTest(
        stackTrace.mapping(),
        StringUtils.joinLines(stackTrace.obfuscatedStackTrace()),
        false,
        StringUtils.joinLines(stackTrace.retraceVerboseStackTrace()) + StringUtils.LINE_SEPARATOR,
        "-verbose");
  }

  @Test
  public void testWindowsLineEndings() throws IOException {
    ActualRetraceBotStackTrace stackTrace = new ActualRetraceBotStackTrace();
    runTest(
        stackTrace.mapping().replace("\n", "\r\n"),
        StringUtils.joinLines(stackTrace.obfuscatedStackTrace()),
        false,
        StringUtils.joinLines(stackTrace.retracedStackTrace()) + StringUtils.LINE_SEPARATOR);
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
    assertThat(processResult.stdout, containsString(Retrace.getUsageMessage()));
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
    assumeFalse(testPartition);
    ProcessResult processResult = runRetrace("", "", true);
    assertTrue(processResult.stdout.startsWith(WAITING_MESSAGE));
  }

  @Test
  public void testHelpMessageWithQuiet() throws IOException {
    ProcessResult processResult = runRetrace("", "", true, "--quiet");
    assertFalse(processResult.stdout.startsWith(WAITING_MESSAGE));
  }

  @Test
  public void testNoMappingFileHash() throws IOException {
    Path mappingFile = folder.newFile("mapping.txt").toPath();
    Files.write(mappingFile, ("# other header\n" + "foo.bar -> a.a\n").getBytes());
    ProcessResult result =
        runRetraceCommandLine(
            null, ImmutableList.of(mappingFile.toString(), "--verify-mapping-file-hash"));
    assertEquals(result.toString(), 0, result.exitCode);
    assertEquals("", result.stdout);
    assertThat(result.stderr, containsString("Failure to find map hash"));
  }

  @Test
  public void testValidMappingFileHash() throws IOException {
    Path mappingFile = folder.newFile("mapping.txt").toPath();
    Files.write(
        mappingFile,
        ("# pg_map_hash: SHA-256 aaf7c0230ea6fa768189170543c86ec202c6180d1e0a37b620e5c1fce1bd3ae7\n"
                + "foo.bar -> a.a\n")
            .getBytes());
    ProcessResult result =
        runRetraceCommandLine(
            null, ImmutableList.of(mappingFile.toString(), "--verify-mapping-file-hash"));
    assertEquals(result.toString(), 0, result.exitCode);
    assertEquals("", result.stdout);
    assertEquals("", result.stderr);
  }

  @Test
  public void testInvalidMappingFileHash() throws IOException {
    Path mappingFile = folder.newFile("mapping.txt").toPath();
    Files.write(mappingFile, ("# pg_map_hash: SHA-256 abcd1234\n" + "foo.bar -> a.a\n").getBytes());
    runAbortTest(
        containsString("Mismatching map hash"),
        mappingFile.toString(),
        "--verify-mapping-file-hash");
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
    if (testPartition) {
      mappingFile = runPartitionCommandLine(mappingFile);
    }
    File stackTraceFile = folder.newFile("stacktrace.txt");
    Files.write(stackTraceFile.toPath(), stackTrace.getBytes(StandardCharsets.UTF_8));

    Collection<String> args = new ArrayList<>();
    if (testPartition) {
      args.add("--partition-map");
    }
    args.add(mappingFile.toString());
    if (!stacktraceStdIn) {
      args.add(stackTraceFile.toPath().toString());
    }
    args.addAll(Arrays.asList(additionalArgs));
    return runRetraceCommandLine(stacktraceStdIn ? stackTraceFile : null, args);
  }

  private Path runPartitionCommandLine(Path mappingFile) throws IOException {
    Path partitionOutput = folder.newFile("partition.txt").toPath();
    ProcessResult processResult =
        runCommandLine(
            null,
            "com.android.tools.r8.retrace.Partition",
            Partition::run,
            ImmutableList.of("--output", partitionOutput.toString(), mappingFile.toString()));
    assertEquals(0, processResult.exitCode);
    return partitionOutput;
  }

  private ProcessResult runRetraceCommandLine(File stdInput, Collection<String> args)
      throws IOException {
    return runCommandLine(stdInput, "com.android.tools.r8.retrace.Retrace", Retrace::run, args);
  }

  private <E extends Exception> ProcessResult runCommandLine(
      File stdInput,
      String mainEntryPointExternal,
      ThrowingConsumer<String[], E> mainEntryPointInternal,
      Collection<String> args)
      throws IOException {
    if (testExternal) {
      // The external dependency is built on top of R8Lib. If test.py is run with
      // no r8lib, do not try and run the external R8 Retrace since it has not been built.
      assumeTrue(ToolHelper.isTestingR8Lib());
      assertTrue(Files.exists(ToolHelper.R8LIB_JAR));
      List<String> command = new ArrayList<>();
      command.add(ToolHelper.getSystemJavaExecutable());
      command.add("-ea");
      command.add("-cp");
      command.add(ToolHelper.getRetracePath().toString());
      command.add(mainEntryPointExternal);
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
        mainEntryPointInternal.accept(strArgs);
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
