// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.L8Command.USAGE_MESSAGE;
import static com.android.tools.r8.utils.ExceptionUtils.unwrapExecutionException;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.LazyLoadedDexApplication;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.desugar.PrefixRewritingMapper;
import com.android.tools.r8.jar.CfApplicationWriter;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.naming.PrefixRewritingNamingLens;
import com.android.tools.r8.naming.signature.GenericSignatureRewriter;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.shaking.AnnotationRemover;
import com.android.tools.r8.shaking.L8TreePruner;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.SelfRetraceTest;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * The L8 compiler.
 */
@Keep
public class L8 {

  /**
   * Main API entry for the L8 compiler.
   *
   * @param command L8 command.
   */
  public static void run(L8Command command) throws CompilationFailedException {
    runForTesting(
        command.getInputApp(),
        command.getInternalOptions(),
        command.isShrinking(),
        command.getD8Command(),
        command.getR8Command());
  }

  /**
   * Main API entry for the L8 compiler.
   *
   * @param command L8 command.
   * @param executor executor service from which to get threads for multi-threaded processing.
   */
  public static void run(L8Command command, ExecutorService executor)
      throws CompilationFailedException {
    run(
        command.getInputApp(),
        command.getInternalOptions(),
        command.isShrinking(),
        command.getD8Command(),
        command.getR8Command(),
        executor);
  }

  static void runForTesting(
      AndroidApp app,
      InternalOptions options,
      boolean shrink,
      D8Command d8Command,
      R8Command r8Command)
      throws CompilationFailedException {
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    run(app, options, shrink, d8Command, r8Command, executorService);
  }

  private static void run(
      AndroidApp app,
      InternalOptions options,
      boolean shrink,
      D8Command d8Command,
      R8Command r8Command,
      ExecutorService executorService)
      throws CompilationFailedException {
    try {
      assert !options.cfToCfDesugar;
      ExceptionUtils.withD8CompilationHandler(
          options.reporter,
          () -> {
            options.cfToCfDesugar = true;
            desugar(app, options, executorService);
            options.cfToCfDesugar = false;
          });
      assert !options.cfToCfDesugar;
      if (shrink) {
        R8.run(r8Command);
      } else {
        D8.run(d8Command, executorService);
      }
    } finally {
      executorService.shutdown();
    }
  }

  private static void desugar(
      AndroidApp inputApp, InternalOptions options, ExecutorService executor) throws IOException {
    Timing timing = Timing.create("L8 desugaring", options);
    assert options.cfToCfDesugar;
    try {
      // Disable global optimizations.
      options.disableGlobalOptimizations();
      // Since L8 Cf representation is temporary, just disable long running back-end optimizations
      // on it.
      options.enableLoadStoreOptimization = false;

      LazyLoadedDexApplication lazyApp =
          new ApplicationReader(inputApp, options, timing).read(executor);

      PrefixRewritingMapper rewritePrefix =
          options.desugaredLibraryConfiguration.createPrefixRewritingMapper(options);

      DexApplication app = new L8TreePruner(options).prune(lazyApp, rewritePrefix);
      AppInfo appInfo = new AppInfo(app);

      AppView<?> appView = AppView.createForL8(appInfo, rewritePrefix);
      IRConverter converter = new IRConverter(appView, timing);

      if (!options.testing.disableL8AnnotationRemoval) {
        AnnotationRemover.clearAnnotations(appView);
      }
      app = converter.convert(app, executor);
      assert appView.appInfo() == appInfo;

      NamingLens namingLens = PrefixRewritingNamingLens.createPrefixRewritingNamingLens(appView);
      new GenericSignatureRewriter(appView, namingLens).run(appInfo.classes(), executor);

      new CfApplicationWriter(
              app,
              appView,
              options,
              options.getMarker(Tool.L8),
              GraphLense.getIdentityLense(),
              namingLens,
              null)
          .write(options.getClassFileConsumer());
      options.printWarnings();
    } catch (ExecutionException e) {
      throw unwrapExecutionException(e);
    } finally {
      options.signalFinishedToConsumers();
      // Dump timings.
      if (options.printTimes) {
        timing.report();
      }
    }
  }

  private static void run(String[] args) throws CompilationFailedException {
    L8Command command = L8Command.parse(args, CommandLineOrigin.INSTANCE).build();
    if (command.isPrintHelp()) {
      SelfRetraceTest.test();
      System.out.println(USAGE_MESSAGE);
      return;
    }
    if (command.isPrintVersion()) {
      System.out.println("L8 " + Version.getVersionString());
      return;
    }
    run(command);
  }

  /**
   * Command-line entry to L8.
   *
   * <p>See {@link L8Command#USAGE_MESSAGE} or run {@code l8 --help} for usage information.
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      System.err.println(USAGE_MESSAGE);
      System.exit(ExceptionUtils.STATUS_ERROR);
    }
    ExceptionUtils.withMainProgramHandler(() -> run(args));
  }
}
