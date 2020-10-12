// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.utils.ExceptionUtils.STATUS_ERROR;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Keep;
import com.android.tools.r8.Version;
import com.android.tools.r8.retrace.RetraceCommand.Builder;
import com.android.tools.r8.retrace.RetraceCommand.ProguardMapProducer;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.OptionsParsing;
import com.android.tools.r8.utils.OptionsParsing.ParseContext;
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
import java.util.List;
import java.util.Scanner;

/**
 * A retrace tool for obfuscated stack traces.
 *
 * <p>This is the interface for getting de-obfuscating stack traces, similar to the proguard retrace
 * tool.
 */
@Keep
public class Retrace {

  // This is a slight modification of the default regular expression shown for proguard retrace
  // that allow for retracing classes in the form <class>: lorem ipsum...
  // Seems like Proguard retrace is expecting the form "Caused by: <class>".
  public static final String DEFAULT_REGULAR_EXPRESSION =
      "(?:.*?\\bat\\s+%c\\.%m\\s*\\(%s(?::%l)?\\)\\s*(?:~\\[.*\\])?)"
          + "|(?:(?:(?:%c|.*)?[:\"]\\s+)?%c(?::.*)?)";

  public static final String USAGE_MESSAGE =
      StringUtils.lines(
          "Usage: retrace <proguard-map> [stack-trace-file] [--regex <regexp>, --verbose, --info]",
          "  where <proguard-map> is an r8 generated mapping file.");

  private static Builder parseArguments(String[] args, DiagnosticsHandler diagnosticsHandler) {
    ParseContext context = new ParseContext(args);
    Builder builder = RetraceCommand.builder(diagnosticsHandler);
    boolean hasSetProguardMap = false;
    boolean hasSetStackTrace = false;
    boolean hasSetRegularExpression = false;
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
      String regex = OptionsParsing.tryParseSingle(context, "--regex", "r");
      if (regex != null && !regex.isEmpty()) {
        builder.setRegularExpression(regex);
        hasSetRegularExpression = true;
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
      builder.setStackTrace(getStackTraceFromStandardInput());
    }
    if (!hasSetRegularExpression) {
      builder.setRegularExpression(DEFAULT_REGULAR_EXPRESSION);
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
    return () -> {
      try {
        return new String(Files.readAllBytes(path));
      } catch (IOException e) {
        diagnosticsHandler.error(
            new StringDiagnostic(String.format("Could not open mapping file '%s'.", mappingPath)));
        throw new RuntimeException(e);
      }
    };
  }

  private static List<String> getStackTraceFromFile(
      String stackTracePath, DiagnosticsHandler diagnostics) {
    try {
      return Files.readAllLines(Paths.get(stackTracePath), Charsets.UTF_8);
    } catch (IOException e) {
      diagnostics.error(new StringDiagnostic("Could not find stack trace file: " + stackTracePath));
      throw new RetraceAbortException();
    }
  }

  /**
   * The main entry point for running retrace.
   *
   * @param command The command that describes the desired behavior of this retrace invocation.
   */
  public static void run(RetraceCommand command) {
    try {
      Timing timing = Timing.create("R8 retrace", command.printMemory());
      timing.begin("Read proguard map");
      RetraceApi retracer =
          Retracer.create(command.proguardMapProducer, command.diagnosticsHandler);
      timing.end();
      RetraceCommandLineResult result;
      timing.begin("Parse and Retrace");
      if (command.regularExpression != null) {
        result =
            new RetraceRegularExpression(
                    retracer,
                    command.stackTrace,
                    command.diagnosticsHandler,
                    command.regularExpression,
                    command.isVerbose)
                .retrace();
      } else {
        result =
            new RetraceStackTrace(
                    retracer, command.stackTrace, command.diagnosticsHandler, command.isVerbose)
                .retrace();
      }
      timing.end();
      timing.begin("Report result");
      command.retracedStackTraceConsumer.accept(result.getNodes());
      timing.end();
      if (command.printTimes()) {
        timing.report();
      }
    } catch (InvalidMappingFileException e) {
      command.diagnosticsHandler.error(new ExceptionDiagnostic(e));
      throw e;
    }
  }

  public static void run(String[] args) {
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
    Builder builder = parseArguments(mappedArgs, retraceDiagnosticsHandler);
    if (builder == null) {
      // --help or --version was an argument to list
      if (Arrays.asList(mappedArgs).contains("--version")) {
        System.out.println("Retrace " + Version.getVersionString());
        return;
      }
      assert Arrays.asList(mappedArgs).contains("--help");
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
            retraceDiagnosticsHandler.error(new StringDiagnostic(e.getMessage()));
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

  private static List<String> getStackTraceFromStandardInput() {
    System.out.println("Waiting for stack-trace input...");
    Scanner sc = new Scanner(new InputStreamReader(System.in, Charsets.UTF_8));
    List<String> readLines = new ArrayList<>();
    while (sc.hasNext()) {
      readLines.add(sc.nextLine());
    }
    return readLines;
  }

  static class RetraceAbortException extends RuntimeException {}

  private interface MainAction {
    void run() throws RetraceAbortException;
  }

  private static void withMainProgramHandler(MainAction action) {
    try {
      action.run();
    } catch (RetraceAbortException e) {
      // Detail of the errors were already reported
      System.err.println(StringUtils.LINE_SEPARATOR + USAGE_MESSAGE + StringUtils.LINE_SEPARATOR);
      System.exit(STATUS_ERROR);
    } catch (RuntimeException e) {
      System.err.println("Retrace failed with an internal error.");
      System.err.println(StringUtils.LINE_SEPARATOR + USAGE_MESSAGE + StringUtils.LINE_SEPARATOR);
      Throwable cause = e.getCause() == null ? e : e.getCause();
      cause.printStackTrace();
      System.exit(STATUS_ERROR);
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
