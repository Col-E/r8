// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.utils.ExceptionUtils.failWithFakeEntry;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Keep;
import com.android.tools.r8.Version;
import com.android.tools.r8.naming.ProguardMapSupplier.ProguardMapChecker;
import com.android.tools.r8.naming.ProguardMapSupplier.ProguardMapChecker.VerifyMappingFileHashResult;
import com.android.tools.r8.retrace.RetraceCommand.Builder;
import com.android.tools.r8.retrace.internal.RetraceAbortException;
import com.android.tools.r8.retrace.internal.RetracerImpl;
import com.android.tools.r8.retrace.internal.StackTraceRegularExpressionParser;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.OptionsParsing;
import com.android.tools.r8.utils.OptionsParsing.ParseContext;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Charsets;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Function;
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
              + "[--regex <regexp>, --verbose, --info, --quiet, --verify-mapping-file-hash]",
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
      Boolean verify = OptionsParsing.tryParseBoolean(context, "--verify-mapping-file-hash");
      if (verify != null) {
        builder.setVerifyMappingFileHash(true);
        hasSetStackTrace = true;
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
  public List<List<List<T>>> retraceStackTrace(List<T> stackTrace) {
    ListUtils.forEachWithIndex(
        stackTrace,
        (line, lineNumber) -> {
          if (line == null) {
            diagnosticsHandler.error(
                RetraceInvalidStackTraceLineDiagnostics.createNull(lineNumber));
            throw new RetraceAbortException();
          }
        });
    RetraceStackTraceElementProxyEquivalence<T, ST> equivalence =
        new RetraceStackTraceElementProxyEquivalence<>(isVerbose);
    List<List<List<T>>> finalResult = new ArrayList<>();
    ListUtils.fold(
        stackTrace,
        RetraceStackTraceContext.empty(),
        (context, stackTraceLine) -> {
          ST parsedLine = stackTraceLineParser.parse(stackTraceLine);
          List<Pair<RetraceStackTraceElementProxy<T, ST>, List<T>>> resultsForLine =
              new ArrayList<>();
          Box<List<T>> currentList = new Box<>();
          Set<Wrapper<RetraceStackTraceElementProxy<T, ST>>> seen = new HashSet<>();
          List<RetraceStackTraceContext> contexts = new ArrayList<>();
          RetraceStackTraceElementProxyResult<T, ST> retraceResult =
              proxyRetracer.retrace(parsedLine, context);
          retraceResult.stream()
              .forEach(
                  retracedElement -> {
                    if (retracedElement.isTopFrame() || !retracedElement.hasRetracedClass()) {
                      if (seen.add(equivalence.wrap(retracedElement))) {
                        currentList.set(new ArrayList<>());
                        resultsForLine.add(Pair.create(retracedElement, currentList.get()));
                        contexts.add(retracedElement.getContext());
                      } else {
                        currentList.empty();
                      }
                    }
                    if (currentList.isSet()) {
                      currentList.get().add(parsedLine.toRetracedItem(retracedElement, isVerbose));
                    }
                  });
          resultsForLine.sort(Comparator.comparing(Pair::getFirst));
          finalResult.add(ListUtils.map(resultsForLine, Pair::getSecond));
          if (contexts.isEmpty()) {
            return retraceResult.getResultContext();
          }
          return contexts.size() == 1 ? contexts.get(0) : RetraceStackTraceContext.empty();
        });
    return finalResult;
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
    proxyRetracer.retrace(parsedLine, RetraceStackTraceContext.empty()).stream()
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
    return proxyRetracer.retrace(parsedLine, RetraceStackTraceContext.empty()).stream()
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
      RetraceOptions options = command.getOptions();
      if (command.getOptions().isVerifyMappingFileHash()) {
        try (Reader reader = options.getProguardMapProducer().get()) {
          VerifyMappingFileHashResult checkResult =
              ProguardMapChecker.validateProguardMapHash(CharStreams.toString(reader));
          if (checkResult.isError()) {
            command
                .getOptions()
                .getDiagnosticsHandler()
                .error(new StringDiagnostic(checkResult.getMessage()));
            throw new RuntimeException(checkResult.getMessage());
          }
          if (!checkResult.isOk()) {
            command
                .getOptions()
                .getDiagnosticsHandler()
                .warning(new StringDiagnostic(checkResult.getMessage()));
          }
        } catch (IOException e) {
          command.getOptions().getDiagnosticsHandler().error(new ExceptionDiagnostic(e));
          throw new RuntimeException(e);
        }
        return;
      }
      timing.begin("Read proguard map");
      DiagnosticsHandler diagnosticsHandler = options.getDiagnosticsHandler();
      // The setup of a retracer should likely also follow a builder pattern instead of having
      // static create methods. That would avoid the need to method overload the construction here
      // and the default create would become the default build of a retracer.
      RetracerImpl retracer =
          RetracerImpl.create(
              options.getProguardMapProducer(),
              options.getDiagnosticsHandler(),
              allowExperimentalMapping);
      retracer
          .getMapVersions()
          .forEach(
              mapVersionInfo -> {
                if (mapVersionInfo.getMapVersion().isUnknown()) {
                  diagnosticsHandler.warning(
                      RetraceUnknownMapVersionDiagnostic.create(mapVersionInfo.getValue()));
                }
              });
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

  private static class RetraceStackTraceElementProxyEquivalence<
          T, ST extends StackTraceElementProxy<T, ST>>
      extends Equivalence<RetraceStackTraceElementProxy<T, ST>> {

    private final boolean isVerbose;

    public RetraceStackTraceElementProxyEquivalence(boolean isVerbose) {
      this.isVerbose = isVerbose;
    }

    @Override
    protected boolean doEquivalent(
        RetraceStackTraceElementProxy<T, ST> one, RetraceStackTraceElementProxy<T, ST> other) {
      if (one == other) {
        return true;
      }
      if (testNotEqualProperty(
              one,
              other,
              RetraceStackTraceElementProxy::hasRetracedClass,
              r -> r.getRetracedClass().getTypeName())
          || testNotEqualProperty(
              one,
              other,
              RetraceStackTraceElementProxy::hasSourceFile,
              RetraceStackTraceElementProxy::getSourceFile)) {
        return false;
      }
      assert one.getOriginalItem() == other.getOriginalItem();
      if (isVerbose
          || (one.getOriginalItem().hasLineNumber() && one.getOriginalItem().getLineNumber() > 0)) {
        if (testNotEqualProperty(
            one,
            other,
            RetraceStackTraceElementProxy::hasLineNumber,
            RetraceStackTraceElementProxy::getLineNumber)) {
          return false;
        }
      }
      if (one.hasRetracedMethod() != other.hasRetracedMethod()) {
        return false;
      }
      if (one.hasRetracedMethod()) {
        RetracedMethodReference oneMethod = one.getRetracedMethod();
        RetracedMethodReference otherMethod = other.getRetracedMethod();
        if (oneMethod.isKnown() != otherMethod.isKnown()) {
          return false;
        }
        // In verbose mode we check the signature, otherwise we only check the name
        if (!oneMethod.getMethodName().equals(otherMethod.getMethodName())) {
          return false;
        }
        if (isVerbose
            && ((oneMethod.isKnown()
                    && !oneMethod
                        .asKnown()
                        .getMethodReference()
                        .toString()
                        .equals(otherMethod.asKnown().getMethodReference().toString()))
                || (!oneMethod.isKnown()
                    && !oneMethod.getMethodName().equals(otherMethod.getMethodName())))) {
          return false;
        }
      }
      if (one.hasRetracedField() != other.hasRetracedField()) {
        return false;
      }
      if (one.hasRetracedField()) {
        RetracedFieldReference oneField = one.getRetracedField();
        RetracedFieldReference otherField = other.getRetracedField();
        if (oneField.isKnown() != otherField.isKnown()) {
          return false;
        }
        if (!oneField.getFieldName().equals(otherField.getFieldName())) {
          return false;
        }
        if (isVerbose
            && ((oneField.isKnown()
                    && !oneField
                        .asKnown()
                        .getFieldReference()
                        .toString()
                        .equals(otherField.asKnown().getFieldReference().toString()))
                || (oneField.isUnknown()
                    && !oneField.getFieldName().equals(otherField.getFieldName())))) {
          return false;
        }
      }
      if (one.hasRetracedFieldOrReturnType() != other.hasRetracedFieldOrReturnType()) {
        return false;
      }
      if (one.hasRetracedFieldOrReturnType()) {
        RetracedTypeReference oneFieldOrReturn = one.getRetracedFieldOrReturnType();
        RetracedTypeReference otherFieldOrReturn = other.getRetracedFieldOrReturnType();
        if (!compareRetracedTypeReference(oneFieldOrReturn, otherFieldOrReturn)) {
          return false;
        }
      }
      if (one.hasRetracedMethodArguments() != other.hasRetracedMethodArguments()) {
        return false;
      }
      if (one.hasRetracedMethodArguments()) {
        List<RetracedTypeReference> oneMethodArguments = one.getRetracedMethodArguments();
        List<RetracedTypeReference> otherMethodArguments = other.getRetracedMethodArguments();
        if (oneMethodArguments.size() != otherMethodArguments.size()) {
          return false;
        }
        for (int i = 0; i < oneMethodArguments.size(); i++) {
          if (compareRetracedTypeReference(
              oneMethodArguments.get(i), otherMethodArguments.get(i))) {
            return false;
          }
        }
      }
      return true;
    }

    private boolean compareRetracedTypeReference(
        RetracedTypeReference one, RetracedTypeReference other) {
      return one.isVoid() == other.isVoid()
          && (one.isVoid() || one.getTypeName().equals(other.getTypeName()));
    }

    @Override
    protected int doHash(RetraceStackTraceElementProxy<T, ST> proxy) {
      return 0;
    }

    private <V extends Comparable<V>> boolean testNotEqualProperty(
        RetraceStackTraceElementProxy<T, ST> one,
        RetraceStackTraceElementProxy<T, ST> other,
        Function<RetraceStackTraceElementProxy<T, ST>, Boolean> predicate,
        Function<RetraceStackTraceElementProxy<T, ST>, V> getter) {
      return Comparator.comparing(predicate)
              .thenComparing(getter, Comparator.nullsFirst(V::compareTo))
              .compare(one, other)
          != 0;
    }

    public static <T, ST extends StackTraceElementProxy<T, ST>>
        RetraceStackTraceElementProxyEquivalence<T, ST> getInstance(boolean isVerbose) {
      return new RetraceStackTraceElementProxyEquivalence<>(isVerbose);
    }
  }
}
