// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.utils.ExceptionUtils.failWithFakeEntry;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ParseFlagInfo;
import com.android.tools.r8.ParseFlagInfoImpl;
import com.android.tools.r8.ParseFlagPrinter;
import com.android.tools.r8.Version;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.retrace.internal.RetraceAbortException;
import com.android.tools.r8.retrace.internal.RetraceBase;
import com.android.tools.r8.retrace.internal.StackTraceElementStringProxy;
import com.android.tools.r8.retrace.internal.StackTraceRegularExpressionParser;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.OptionsParsing;
import com.android.tools.r8.utils.OptionsParsing.ParseContext;
import com.android.tools.r8.utils.PartitionMapZipContainer;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * A retrace tool for obfuscated stack traces.
 *
 * <p>This is the interface for getting de-obfuscating stack traces, similar to the proguard retrace
 * tool.
 */
@KeepForApi
public class Retrace<T, ST extends StackTraceElementProxy<T, ST>> extends RetraceBase<T, ST> {

  private static final String USAGE_MESSAGE =
      StringUtils.lines(
          "Usage: retrace [options] <proguard-map> [stack-trace-file] "
              + "where <proguard-map> is a generated mapping file and options are:");

  public static List<ParseFlagInfo> getFlags() {
    return ImmutableList.<ParseFlagInfo>builder()
        .add(
            ParseFlagInfoImpl.flag1(
                "--regex", "<regexp>", "Regular expression for parsing stack-trace-file as lines"))
        .add(ParseFlagInfoImpl.flag0("--verbose", "Get verbose retraced output"))
        .add(ParseFlagInfoImpl.flag0("--info", "Write information messages to stdout"))
        .add(ParseFlagInfoImpl.flag0("--quiet", "Silence ordinary messages printed to stdout"))
        .add(ParseFlagInfoImpl.flag0("--verify-mapping-file-hash", "Verify the mapping file hash"))
        .add(ParseFlagInfoImpl.getHelp())
        .build();
  }

  static String getUsageMessage() {
    StringBuilder builder = new StringBuilder();
    StringUtils.appendLines(builder, USAGE_MESSAGE);
    new ParseFlagPrinter().addFlags(getFlags()).appendLinesToBuilder(builder);
    return builder.toString();
  }

  private static RetraceCommand.Builder parseArguments(
      String[] args, DiagnosticsHandler diagnosticsHandler) {
    ParseContext context = new ParseContext(args);
    RetraceCommand.Builder builder = RetraceCommand.builder(diagnosticsHandler);
    boolean hasSetProguardMap = false;
    boolean hasSetStackTrace = false;
    boolean hasSetQuiet = false;
    while (context.head() != null) {
      Boolean help = OptionsParsing.tryParseBoolean(context, "--help");
      if (help != null) {
        return null;
      }
      Boolean version = OptionsParsing.tryParseBoolean(context, "--version");
      if (version != null) {
        return null;
      }
      Boolean info = OptionsParsing.tryParseBoolean(context, "--info");
      if (info != null) {
        // This is already set in the diagnostics handler.
        continue;
      }
      Boolean verbose = OptionsParsing.tryParseBoolean(context, "--verbose");
      if (verbose != null) {
        builder.setVerbose(true);
        continue;
      }
      Boolean quiet = OptionsParsing.tryParseBoolean(context, "--quiet");
      if (quiet != null) {
        hasSetQuiet = true;
        continue;
      }
      String regex = OptionsParsing.tryParseSingle(context, "--regex", "--r");
      if (regex != null && !regex.isEmpty()) {
        builder.setRegularExpression(regex);
        continue;
      }
      Boolean verify = OptionsParsing.tryParseBoolean(context, "--verify-mapping-file-hash");
      if (verify != null) {
        builder.setVerifyMappingFileHash(true);
        hasSetStackTrace = true;
        continue;
      }
      String partitionMap = OptionsParsing.tryParseSingle(context, "--partition-map", "--p");
      if (partitionMap != null && !partitionMap.isEmpty()) {
        builder.setMappingSupplier(getPartitionMappingSupplier(partitionMap, diagnosticsHandler));
        hasSetProguardMap = true;
        continue;
      }
      if (!hasSetProguardMap) {
        builder.setMappingSupplier(getMappingSupplier(context.head(), diagnosticsHandler));
        context.next();
        hasSetProguardMap = true;
      } else if (!hasSetStackTrace) {
        builder.setStackTrace(getStackTraceFromFile(context.head(), diagnosticsHandler));
        context.next();
        hasSetStackTrace = true;
      } else {
        diagnosticsHandler.error(
            new StringDiagnostic(
                String.format("Too many arguments specified for builder at '%s'", context.head())));
        diagnosticsHandler.error(new StringDiagnostic(getUsageMessage()));
        throw new RetraceAbortException();
      }
    }
    if (!hasSetProguardMap) {
      diagnosticsHandler.error(new StringDiagnostic("Mapping file not specified"));
      throw new RetraceAbortException();
    }
    if (!hasSetStackTrace) {
      builder.setStackTrace(getStackTraceFromStandardInput(hasSetQuiet));
    }
    return builder;
  }

  private static MappingSupplier<?> getPartitionMappingSupplier(
      String partitionMap, DiagnosticsHandler diagnosticsHandler) {
    Path path = Paths.get(partitionMap);
    if (!Files.exists(path)) {
      diagnosticsHandler.error(
          new StringDiagnostic(String.format("Could not find mapping file '%s'.", partitionMap)));
      throw new RetraceAbortException();
    }
    try {
      return PartitionMapZipContainer.createPartitionMapZipContainerSupplier(path);
    } catch (Exception e) {
      diagnosticsHandler.error(new ExceptionDiagnostic(e));
      throw new RetraceAbortException();
    }
  }

  private static ProguardMappingSupplier getMappingSupplier(
      String mappingPath, DiagnosticsHandler diagnosticsHandler) {
    Path path = Paths.get(mappingPath);
    if (!Files.exists(path)) {
      diagnosticsHandler.error(
          new StringDiagnostic(String.format("Could not find mapping file '%s'.", mappingPath)));
      throw new RetraceAbortException();
    }
    boolean allowExperimentalMapVersion =
        System.getProperty("com.android.tools.r8.experimentalmapping") != null;
    return ProguardMappingSupplier.builder()
        .setProguardMapProducer(ProguardMapProducer.fromPath(Paths.get(mappingPath)))
        .setAllowExperimental(allowExperimentalMapVersion)
        .setLoadAllDefinitions(false)
        .build();
  }

  private static List<String> getStackTraceFromFile(
      String stackTracePath, DiagnosticsHandler diagnostics) {
    try {
      return Files.readAllLines(Paths.get(stackTracePath), Charsets.UTF_8);
    } catch (IOException e) {
      diagnostics.error(new ExceptionDiagnostic(e));
      throw new RetraceAbortException();
    }
  }

  private final MappingSupplier<?> mappingSupplier;
  private final DiagnosticsHandler diagnosticsHandler;

  Retrace(
      StackTraceLineParser<T, ST> stackTraceLineParser,
      MappingSupplier<?> mappingSupplier,
      DiagnosticsHandler diagnosticsHandler,
      boolean isVerbose) {
    super(stackTraceLineParser, mappingSupplier, diagnosticsHandler, isVerbose);
    this.mappingSupplier = mappingSupplier;
    this.diagnosticsHandler = diagnosticsHandler;
  }

  /**
   * Retraces a complete stack frame and returns a list of retraced stack traces.
   *
   * @param stackTrace the stack trace to be retrace
   * @param context The context to retrace the stack trace in
   * @return list of potentially ambiguous stack traces.
   */
  public RetraceStackTraceResult<T> retraceStackTrace(
      List<T> stackTrace, RetraceStackTraceContext context) {
    return retraceStackTraceParsed(parse(stackTrace), context);
  }

  /**
   * Retraces a complete stack frame and returns a list of retraced stack traces.
   *
   * @param stackTrace the stack trace to be retrace
   * @param context The context to retrace the stack trace in
   * @return list of potentially ambiguous stack traces.
   */
  public RetraceStackTraceResult<T> retraceStackTraceParsed(
      List<ST> stackTrace, RetraceStackTraceContext context) {
    registerUses(stackTrace);
    return retraceStackTraceParsedWithRetracer(
        mappingSupplier.createRetracer(diagnosticsHandler), stackTrace, context);
  }

  /**
   * Retraces a stack trace frame with support for splitting up ambiguous results.
   *
   * @param stackTraceFrame The frame to retrace that can give rise to ambiguous results
   * @param context The context to retrace the stack trace in
   * @return A collection of potentially ambiguous retraced frames
   */
  public RetraceStackFrameAmbiguousResultWithContext<T> retraceFrame(
      T stackTraceFrame, RetraceStackTraceContext context) {
    ST parsedFrame = parse(stackTraceFrame);
    registerUses(parsedFrame);
    return retraceFrameWithRetracer(
        mappingSupplier.createRetracer(diagnosticsHandler), parsedFrame, context);
  }

  /**
   * Utility method for tracing a single line that also retraces ambiguous lines without being able
   * to distinguish them. For retracing with ambiguous results separated, use {@link #retraceFrame}
   *
   * @param stackTraceLine the stack trace line to retrace
   * @param context The context to retrace the stack trace in
   * @return the retraced stack trace line
   */
  public RetraceStackFrameResultWithContext<T> retraceLine(
      T stackTraceLine, RetraceStackTraceContext context) {
    ST parsedFrame = parse(stackTraceLine);
    registerUses(parsedFrame);
    return retraceLineWithRetracer(
        mappingSupplier.createRetracer(diagnosticsHandler), parsedFrame, context);
  }

  /**
   * The main entry point for running retrace.
   *
   * @param command The command that describes the desired behavior of this retrace invocation.
   */
  public static void run(RetraceCommand command) {
    try {
      Timing timing = Timing.create("R8 retrace", command.printMemory());
      RetraceOptions options = command.getOptions();
      MappingSupplier<?> mappingSupplier = options.getMappingSupplier();
      if (command.getOptions().isVerifyMappingFileHash()) {
        mappingSupplier.verifyMappingFileHash(options.getDiagnosticsHandler());
        return;
      }
      DiagnosticsHandler diagnosticsHandler = options.getDiagnosticsHandler();
      StackTraceRegularExpressionParser stackTraceLineParser =
          new StackTraceRegularExpressionParser(options.getRegularExpression());
      StackTraceSupplier stackTraceSupplier = command.getStacktraceSupplier();
      int lineNumber = 0;
      RetraceStackTraceContext context = RetraceStackTraceContext.empty();
      List<String> currentStackTrace;
      while ((currentStackTrace = stackTraceSupplier.get()) != null) {
        timing.begin("Parsing");
        List<StackTraceElementStringProxy> parsedStackTrace = new ArrayList<>();
        for (String line : currentStackTrace) {
          if (line == null) {
            diagnosticsHandler.error(
                RetraceInvalidStackTraceLineDiagnostics.createNull(lineNumber));
            throw new RetraceAbortException();
          }
          parsedStackTrace.add(stackTraceLineParser.parse(line));
          lineNumber += 1;
        }
        timing.end();
        timing.begin("Read proguard map");
        StringRetrace stringRetracer =
            new StringRetrace(
                stackTraceLineParser, mappingSupplier, diagnosticsHandler, options.isVerbose());
        timing.end();
        timing.begin("Retracing");
        RetraceStackFrameResultWithContext<String> result =
            stringRetracer.retraceParsed(parsedStackTrace, context);
        timing.end();
        timing.begin("Report result");
        context = result.getContext();
        if (!result.isEmpty() || currentStackTrace.isEmpty()) {
          command.getRetracedStackTraceConsumer().accept(result.getResult());
        }
        timing.end();
      }
      if (command.printTimes()) {
        timing.report();
      }
      mappingSupplier
          .getMapVersions(diagnosticsHandler)
          .forEach(
              mapVersionInfo -> {
                if (mapVersionInfo.getMapVersion().isUnknown()) {
                  diagnosticsHandler.warning(
                      RetraceUnknownMapVersionDiagnostic.create(mapVersionInfo.getValue()));
                }
              });
      mappingSupplier.finished(diagnosticsHandler);
    } catch (InvalidMappingFileException e) {
      command.getOptions().getDiagnosticsHandler().error(new ExceptionDiagnostic(e));
      throw e;
    }
  }

  public static void run(String[] args) throws RetraceFailedException {
    // To be compatible with standard retrace and remapper, we translate -arg into --arg.
    String[] mappedArgs = new String[args.length];
    boolean printInfo = false;
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg == null || arg.length() < 2) {
        mappedArgs[i] = arg;
        continue;
      }
      if (arg.charAt(0) == '-' && arg.charAt(1) != '-') {
        mappedArgs[i] = "-" + arg;
      } else {
        mappedArgs[i] = arg;
      }
      if (mappedArgs[i].equals("--info")) {
        printInfo = true;
      }
    }
    RetraceDiagnosticsHandler retraceDiagnosticsHandler =
        new RetraceDiagnosticsHandler(new DiagnosticsHandler() {}, printInfo);
    try {
      run(mappedArgs, retraceDiagnosticsHandler);
    } catch (Throwable t) {
      throw failWithFakeEntry(
          retraceDiagnosticsHandler,
          t,
          (message, cause, ignore) -> new RetraceFailedException(message, cause),
          RetraceAbortException.class);
    }
  }

  private static void run(String[] args, DiagnosticsHandler diagnosticsHandler) {
    RetraceCommand.Builder builder = parseArguments(args, diagnosticsHandler);
    if (builder == null) {
      // --help or --version was an argument to list
      if (Arrays.asList(args).contains("--version")) {
        System.out.println("Retrace " + Version.getVersionString());
        return;
      }
      assert Arrays.asList(args).contains("--help");
      System.out.println("Retrace " + Version.getVersionString());
      System.out.print(getUsageMessage());
      return;
    }
    builder.setRetracedStackTraceConsumer(
        retraced -> {
          try (PrintStream printStream = new PrintStream(System.out, true, Charsets.UTF_8.name())) {
            for (String line : retraced) {
              printStream.println(line);
            }
          } catch (UnsupportedEncodingException e) {
            diagnosticsHandler.error(new StringDiagnostic(e.getMessage()));
          }
        });
    run(builder.build());
  }

  /**
   * The main entry point for running a legacy compatible retrace from the command line.
   *
   * @param args The argument that describes this command.
   */
  public static void main(String... args) {
    withMainProgramHandler(() -> run(args));
  }

  private static List<String> getStackTraceFromStandardInput(boolean printWaitingMessage) {
    if (!printWaitingMessage) {
      System.out.println("Waiting for stack-trace input...");
    }
    Scanner sc = new Scanner(new InputStreamReader(System.in, Charsets.UTF_8));
    List<String> readLines = new ArrayList<>();
    while (sc.hasNext()) {
      readLines.add(sc.nextLine());
    }
    return readLines;
  }

  private interface MainAction {
    void run() throws RetraceFailedException;
  }

  private static void withMainProgramHandler(MainAction action) {
    try {
      action.run();
    } catch (RetraceFailedException | RetraceAbortException e) {
      // Detail of the errors were already reported
      throw new RuntimeException("Retrace failed", e);
    } catch (Throwable t) {
      throw new RuntimeException("Retrace failed with an internal error.", t);
    }
  }

  public static <T, ST extends StackTraceElementProxy<T, ST>> Builder<T, ST> builder() {
    return new Builder<>();
  }

  @KeepForApi
  public static class Builder<T, ST extends StackTraceElementProxy<T, ST>>
      extends RetraceBuilderBase<Builder<T, ST>, T, ST> {

    private MappingSupplier<?> mappingSupplier;

    @Override
    public Builder<T, ST> self() {
      return this;
    }

    public Builder<T, ST> setMappingSupplier(MappingSupplier<?> mappingSupplier) {
      this.mappingSupplier = mappingSupplier;
      return this;
    }

    public Retrace<T, ST> build() {
      return new Retrace<>(stackTraceLineParser, mappingSupplier, diagnosticsHandler, isVerbose);
    }
  }

  private static class RetraceDiagnosticsHandler implements DiagnosticsHandler {

    private final DiagnosticsHandler diagnosticsHandler;
    private final boolean printInfo;

    public RetraceDiagnosticsHandler(DiagnosticsHandler diagnosticsHandler, boolean printInfo) {
      this.diagnosticsHandler = diagnosticsHandler;
      this.printInfo = printInfo;
      assert diagnosticsHandler != null;
    }

    @Override
    public void error(Diagnostic error) {
      diagnosticsHandler.error(error);
    }

    @Override
    public void warning(Diagnostic warning) {
      diagnosticsHandler.warning(warning);
    }

    @Override
    public void info(Diagnostic info) {
      if (printInfo) {
        diagnosticsHandler.info(info);
      }
    }
  }
}
