// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.D8Command.USAGE_MESSAGE;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.AbortException;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.CfgPrinter;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.VersionProperties;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * The D8 dex compiler.
 *
 * <p>D8 performs modular compilation to DEX bytecode. It supports compilation of Java bytecode and
 * Android DEX bytecode to DEX bytecode including merging a mix of these input formats.
 *
 * <p>The D8 dexer API is intentionally limited and should "do the right thing" given a command. If
 * this API does not suffice please contact the D8/R8 team.
 *
 * <p>The compiler is invoked by calling {@link #run(D8Command) D8.run} with an appropriate {@link
 * D8Command}. For example:
 *
 * <pre>
 *   D8.run(D8Command.builder()
 *       .addProgramFiles(inputPathA, inputPathB)
 *       .setOutputPath(outputPath)
 *       .build());
 * </pre>
 *
 * The above reads the input files denoted by {@code inputPathA} and {@code inputPathB}, compiles
 * them to DEX bytecode (compiling from Java bytecode for such inputs and merging for DEX inputs),
 * and then writes the result to the directory or zip archive specified by {@code outputPath}.
 */
public final class D8 {

  private static final int STATUS_ERROR = 1;

  private D8() {}

  /**
   * Main API entry for the D8 dexer.
   *
   * @param command D8 command.
   */
  public static D8Output run(D8Command command) throws CompilationFailedException {
    AndroidApp app = command.getInputApp();
    InternalOptions options = command.getInternalOptions();
    ExecutorService executor = ThreadUtils.getExecutorService(options);
    AndroidAppConsumers compatSink =
        command.usingDeprecatedAPI() ? new AndroidAppConsumers(options) : null;
    ExceptionUtils.withD8CompilationHandler(
        command.getReporter(),
        () -> {
          try {
            run(app, options, executor);
          } finally {
            executor.shutdown();
          }
        });
    return compatSink == null ? null : new D8Output(compatSink.build(), command.getOutputMode());
  }

  /**
   * Main API entry for the D8 dexer.
   *
   * <p>The D8 dexer API is intentionally limited and should "do the right thing" given a set of
   * inputs. If the API does not suffice please contact the R8 team.
   *
   * @param command D8 command.
   * @param executor executor service from which to get threads for multi-threaded processing.
   */
  public static D8Output run(D8Command command, ExecutorService executor)
      throws CompilationFailedException {
    AndroidApp app = command.getInputApp();
    InternalOptions options = command.getInternalOptions();
    AndroidAppConsumers compatSink =
        command.usingDeprecatedAPI() ? new AndroidAppConsumers(options) : null;
    ExceptionUtils.withD8CompilationHandler(
        command.getReporter(),
        () -> {
          run(app, options, executor);
        });
    return compatSink == null ? null : new D8Output(compatSink.build(), command.getOutputMode());
  }

  private static void run(String[] args)
      throws IOException, CompilationException, CompilationFailedException {
    D8Command command = D8Command.parse(args, CommandLineOrigin.INSTANCE).build();
    if (command.isPrintHelp()) {
      System.out.println(USAGE_MESSAGE);
      return;
    }
    if (command.isPrintVersion()) {
      Version.printToolVersion("D8");
      return;
    }
    runForTesting(command.getInputApp(), command.getInternalOptions());
  }

  /** Command-line entry to D8. */
  public static void main(String[] args) {
    if (args.length == 0) {
      System.err.println(USAGE_MESSAGE);
      System.exit(STATUS_ERROR);
    }
    try {
      run(args);
    } catch (NoSuchFileException e) {
      System.err.println("File not found: " + e.getFile());
      System.exit(STATUS_ERROR);
    } catch (FileAlreadyExistsException e) {
      System.err.println("File already exists: " + e.getFile());
      System.exit(STATUS_ERROR);
    } catch (IOException e) {
      System.err.println("Failed to read or write application files: " + e.getMessage());
      System.exit(STATUS_ERROR);
    } catch (CompilationFailedException | AbortException e) {
      // Detail of the errors were already reported
      System.err.println("Compilation failed");
      System.exit(STATUS_ERROR);
    } catch (RuntimeException e) {
      System.err.println("Compilation failed with an internal error.");
      Throwable cause = e.getCause() == null ? e : e.getCause();
      cause.printStackTrace();
      System.exit(STATUS_ERROR);
    } catch (CompilationException e) {
      System.err.println("Compilation failed: " + e.getMessageForD8());
      System.exit(STATUS_ERROR);
    }
  }

  static void runForTesting(AndroidApp inputApp, InternalOptions options)
      throws IOException, CompilationException {
    ExecutorService executor = ThreadUtils.getExecutorService(ThreadUtils.NOT_SPECIFIED);
    try {
      run(inputApp, options, executor);
    } finally {
      executor.shutdown();
    }
  }

  // Compute the marker to be placed in the main dex file.
  private static Marker getMarker(InternalOptions options) {
    if (options.hasMarker()) {
      return options.getMarker();
    }
    Marker marker = new Marker(Tool.D8)
        .setVersion(Version.LABEL)
        .setMinApi(options.minApiLevel);
    if (Version.isDev()) {
      marker.setSha1(VersionProperties.INSTANCE.getSha());
    }
    return marker;
  }

  private static void run(AndroidApp inputApp, InternalOptions options, ExecutorService executor)
      throws IOException, CompilationException {
    try {
      // Disable global optimizations.
      options.skipMinification = true;
      options.inlineAccessors = false;
      options.outline.enabled = false;

      Timing timing = new Timing("DX timer");
      DexApplication app = new ApplicationReader(inputApp, options, timing).read(executor);
      AppInfo appInfo = new AppInfo(app);
      app = optimize(app, appInfo, options, timing, executor);

      // If a method filter is present don't produce output since the application is likely partial.
      if (options.hasMethodsFilter()) {
        System.out.println("Finished compilation with method filter: ");
        options.methodsFilter.forEach((m) -> System.out.println("  - " + m));
      }
      Marker marker = getMarker(options);
      new ApplicationWriter(app, options, marker, null, NamingLens.getIdentityLens(), null, null)
          .write(executor);
      options.printWarnings();
    } catch (ExecutionException e) {
      R8.unwrapExecutionException(e);
      throw new AssertionError(e); // unwrapping method should have thrown
    } finally {
      options.signalFinishedToProgramConsumer();
    }
  }

  private static DexApplication optimize(
      DexApplication application, AppInfo appInfo, InternalOptions options,
      Timing timing, ExecutorService executor)
      throws IOException, ExecutionException, ApiLevelException {
    final CfgPrinter printer = options.printCfg ? new CfgPrinter() : null;

    IRConverter converter = new IRConverter(appInfo, options, timing, printer);
    application = converter.convertToDex(application, executor);

    if (options.printCfg) {
      if (options.printCfgFile == null || options.printCfgFile.isEmpty()) {
        System.out.print(printer.toString());
      } else {
        try (OutputStreamWriter writer = new OutputStreamWriter(
            new FileOutputStream(options.printCfgFile),
            StandardCharsets.UTF_8)) {
          writer.write(printer.toString());
        }
      }
    }
    return application;
  }
}
