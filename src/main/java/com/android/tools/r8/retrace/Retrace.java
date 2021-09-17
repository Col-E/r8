// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.utils.ExceptionUtils.failWithFakeEntry;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Keep;
import com.android.tools.r8.Version;
import com.android.tools.r8.retrace.RetraceCommand.Builder;
import com.android.tools.r8.retrace.internal.RetraceAbortException;
import com.android.tools.r8.retrace.internal.RetracerImpl;
import com.android.tools.r8.retrace.internal.StackTraceRegularExpressionParser;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.OptionsParsing;
import com.android.tools.r8.utils.OptionsParsing.ParseContext;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Charsets;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * A retrace tool for obfuscated stack traces.
 *
 * <p>This is the interface for getting de-obfuscating stack traces, similar to the proguard retrace
 * tool.
 */
@Keep
public class Retrace<T, ST extends StackTraceElementProxy<T, ST>> {

  public static final String USAGE_MESSAGE =
      StringUtils.lines(
          "Usage: retrace <proguard-map> [stack-trace-file] "
              + "[--regex <regexp>, --verbose, --info, --quiet]",
          "  where <proguard-map> is an r8 generated mapping file.");

  private static Builder parseArguments(String[] args, DiagnosticsHandler diagnosticsHandler) {
    ParseContext context = new ParseContext(args);
    Builder builder = RetraceCommand.builder(diagnosticsHandler);
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
      String regex = OptionsParsing.tryParseSingle(context, "--regex", "r");
      if (regex != null && !regex.isEmpty()) {
        builder.setRegularExpression(regex);
        continue;
      }
      if (!hasSetProguardMap) {
        builder.setProguardMapProducer(getMappingSupplier(context.head(), diagnosticsHandler));
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
        diagnosticsHandler.error(new StringDiagnostic(USAGE_MESSAGE));
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

  private static ProguardMapProducer getMappingSupplier(
      String mappingPath, DiagnosticsHandler diagnosticsHandler) {
    Path path = Paths.get(mappingPath);
    if (!Files.exists(path)) {
      diagnosticsHandler.error(
          new StringDiagnostic(String.format("Could not find mapping file '%s'.", mappingPath)));
      throw new RetraceAbortException();
    }
    return ProguardMapProducer.fromPath(Paths.get(mappingPath));
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

  private final StackTraceLineParser<T, ST> stackTraceLineParser;
  private final StackTraceElementProxyRetracer<T, ST> proxyRetracer;
  private final DiagnosticsHandler diagnosticsHandler;
  protected final boolean isVerbose;

  Retrace(
      StackTraceLineParser<T, ST> stackTraceLineParser,
      StackTraceElementProxyRetracer<T, ST> proxyRetracer,
      DiagnosticsHandler diagnosticsHandler,
      boolean isVerbose) {
    this.stackTraceLineParser = stackTraceLineParser;
    this.proxyRetracer = proxyRetracer;
    this.diagnosticsHandler = diagnosticsHandler;
    this.isVerbose = isVerbose;
  }

  /**
   * Retraces a complete stack frame and returns a list of retraced stack traces.
   *
   * @param stackTrace the stack trace to be retrace
   * @return list of potentially ambiguous stack traces.
   */
  public List<List<T>> retraceStackTrace(List<T> stackTrace) {
    ListUtils.forEachWithIndex(
        stackTrace,
        (line, lineNumber) -> {
          if (line == null) {
            diagnosticsHandler.error(
                RetraceInvalidStackTraceLineDiagnostics.createNull(lineNumber));
            throw new RetraceAbortException();
          }
        });
    List<Pair<List<T>, RetraceStackTraceContext>> retracedStackTraces = new ArrayList<>();
    retracedStackTraces.add(
        new Pair<>(new ArrayList<>(), RetraceStackTraceContext.getInitialContext()));
    retracedStackTraces =
        ListUtils.fold(
            stackTrace,
            retracedStackTraces,
            (acc, stackTraceLine) -> {
              ST parsedLine = stackTraceLineParser.parse(stackTraceLine);
              List<Pair<List<T>, RetraceStackTraceContext>> newRetracedStackTraces =
                  new ArrayList<>();
              for (Pair<List<T>, RetraceStackTraceContext> retracedStackTrace : acc) {
                Map<
                        RetraceStackTraceElementProxy<T, ST>,
                        List<RetraceStackTraceElementProxy<T, ST>>>
                    ambiguousBlocks = new HashMap<>();
                List<RetraceStackTraceElementProxy<T, ST>> ambiguousKeys = new ArrayList<>();
                proxyRetracer
                    .retrace(parsedLine, retracedStackTrace.getSecond())
                    .forEach(
                        retracedElement -> {
                          if (retracedElement.isTopFrame() || !retracedElement.hasRetracedClass()) {
                            ambiguousKeys.add(retracedElement);
                            ambiguousBlocks.put(retracedElement, new ArrayList<>());
                          }
                          ambiguousBlocks.get(ListUtils.last(ambiguousKeys)).add(retracedElement);
                        });
                if (ambiguousKeys.isEmpty()) {
                  // This happens when there is nothing to report.
                  newRetracedStackTraces.add(
                      new Pair<>(
                          retracedStackTrace.getFirst(),
                          RetraceStackTraceContext.getInitialContext()));
                  continue;
                }
                Collections.sort(ambiguousKeys);
                ambiguousKeys.forEach(
                    key -> {
                      List<T> resultList = new ArrayList<>();
                      resultList.addAll(retracedStackTrace.getFirst());
                      resultList.addAll(
                          ListUtils.map(
                              ambiguousBlocks.get(key),
                              retracedElement ->
                                  parsedLine.toRetracedItem(retracedElement, isVerbose)));
                      newRetracedStackTraces.add(new Pair<>(resultList, key.getContext()));
                    });
              }
              return newRetracedStackTraces;
            });
    return ListUtils.map(retracedStackTraces, Pair::getFirst);
  }

  /**
   * Retraces a stack trace frame with support for splitting up ambiguous results.
   *
   * @param stackTraceFrame The frame to retrace that can give rise to ambiguous results
   * @return A collection of retraced frame where each entry in the outer list is ambiguous
   */
  public List<List<T>> retraceFrame(T stackTraceFrame) {
    Map<RetraceStackTraceElementProxy<T, ST>, List<T>> ambiguousBlocks = new HashMap<>();
    List<RetraceStackTraceElementProxy<T, ST>> ambiguousKeys = new ArrayList<>();
    ST parsedLine = stackTraceLineParser.parse(stackTraceFrame);
    proxyRetracer
        .retrace(parsedLine, RetraceStackTraceContext.getInitialContext())
        .forEach(
            retracedElement -> {
              if (retracedElement.isTopFrame() || !retracedElement.hasRetracedClass()) {
                ambiguousKeys.add(retracedElement);
                ambiguousBlocks.put(retracedElement, new ArrayList<>());
              }
              ambiguousBlocks
                  .get(ListUtils.last(ambiguousKeys))
                  .add(parsedLine.toRetracedItem(retracedElement, isVerbose));
            });
    Collections.sort(ambiguousKeys);
    List<List<T>> retracedList = new ArrayList<>();
    ambiguousKeys.forEach(key -> retracedList.add(ambiguousBlocks.get(key)));
    return retracedList;
  }

  /**
   * Utility method for tracing a single line that also retraces ambiguous lines without being able
   * to distinguish them. For retracing with ambiguous results separated, use {@link #retraceFrame}
   *
   * @param stackTraceLine the stack trace line to retrace
   * @return the retraced stack trace line
   */
  public List<T> retraceLine(T stackTraceLine) {
    ST parsedLine = stackTraceLineParser.parse(stackTraceLine);
    return proxyRetracer
        .retrace(parsedLine, RetraceStackTraceContext.getInitialContext())
        .map(
            retraceFrame -> {
              retraceFrame.getOriginalItem().toRetracedItem(retraceFrame, isVerbose);
              return parsedLine.toRetracedItem(retraceFrame, isVerbose);
            })
        .collect(Collectors.toList());
  }

  /**
   * The main entry point for running retrace.
   *
   * @param command The command that describes the desired behavior of this retrace invocation.
   */
  public static void run(RetraceCommand command) {
    boolean allowExperimentalMapVersion =
        System.getProperty("com.android.tools.r8.experimentalmapping") != null;
    runForTesting(command, allowExperimentalMapVersion);
  }

  static void runForTesting(RetraceCommand command, boolean allowExperimentalMapping) {
    try {
      Timing timing = Timing.create("R8 retrace", command.printMemory());
      timing.begin("Read proguard map");
      RetraceOptions options = command.getOptions();
      DiagnosticsHandler diagnosticsHandler = options.getDiagnosticsHandler();
      // The setup of a retracer should likely also follow a builder pattern instead of having
      // static create methods. That would avoid the need to method overload the construction here
      // and the default create would become the default build of a retracer.
      Retracer retracer =
          RetracerImpl.create(
              options.getProguardMapProducer(),
              options.getDiagnosticsHandler(),
              allowExperimentalMapping);
      timing.end();
      timing.begin("Report result");
      StringRetrace stringRetrace =
          new StringRetrace(
              new StackTraceRegularExpressionParser(options.getRegularExpression()),
              StackTraceElementProxyRetracer.createDefault(retracer),
              diagnosticsHandler,
              options.isVerbose());
      command
          .getRetracedStackTraceConsumer()
          .accept(stringRetrace.retrace(command.getStackTrace()));
      timing.end();
      if (command.printTimes()) {
        timing.report();
      }
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
          retraceDiagnosticsHandler, t, RetraceFailedException::new, RetraceAbortException.class);
    }
  }

  private static void run(String[] args, DiagnosticsHandler diagnosticsHandler) {
    Builder builder = parseArguments(args, diagnosticsHandler);
    if (builder == null) {
      // --help or --version was an argument to list
      if (Arrays.asList(args).contains("--version")) {
        System.out.println("Retrace " + Version.getVersionString());
        return;
      }
      assert Arrays.asList(args).contains("--help");
      System.out.println("Retrace " + Version.getVersionString());
      System.out.print(USAGE_MESSAGE);
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
