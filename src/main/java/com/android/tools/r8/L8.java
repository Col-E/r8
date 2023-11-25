// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.ExceptionUtils.unwrapExecutionException;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.LazyLoadedDexApplication;
import com.android.tools.r8.ir.conversion.PrimaryD8L8IRConverter;
import com.android.tools.r8.ir.desugar.TypeRewriter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryAmender;
import com.android.tools.r8.jar.CfApplicationWriter;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.naming.PrefixRewritingNamingLens;
import com.android.tools.r8.naming.VarHandleDesugaringRewritingNamingLens;
import com.android.tools.r8.naming.signature.GenericSignatureRewriter;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.shaking.AnnotationRemover;
import com.android.tools.r8.shaking.L8TreePruner;
import com.android.tools.r8.synthesis.SyntheticFinalization;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.SelfRetraceTest;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/** The L8 compiler. */
@KeepForApi
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
      ExceptionUtils.withD8CompilationHandler(
          options.reporter,
          () -> {
            // Desugar to class file format and turn off switch optimizations, as the final
            // compilation with D8 or R8 will do that.
            assert options.isCfDesugaring();
            assert !options.forceAnnotateSynthetics;
            options.forceAnnotateSynthetics = true;
            assert options.enableSwitchRewriting;
            options.enableSwitchRewriting = false;
            assert options.enableStringSwitchConversion;
            options.enableStringSwitchConversion = false;
            assert !options.enableVarHandleDesugaring;
            options.enableVarHandleDesugaring = true;
            options.tool = Tool.L8;

            desugar(app, options, executorService);

            options.forceAnnotateSynthetics = false;
            options.enableSwitchRewriting = true;
            options.enableStringSwitchConversion = true;
            options.enableVarHandleDesugaring = false;
          });
      if (shrink) {
        R8.run(r8Command, executorService);
      } else if (d8Command != null) {
        D8.run(d8Command, executorService);
      }
    } finally {
      executorService.shutdown();
    }
  }

  private static void desugar(
      AndroidApp inputApp, InternalOptions options, ExecutorService executor) throws IOException {
    Timing timing = Timing.create("L8 desugaring", options);
    assert options.isCfDesugaring();
    try {
      // Since L8 Cf representation is temporary, just disable long running back-end optimizations
      // on it.
      options.enableLoadStoreOptimization = false;

      AppView<AppInfo> appView = readApp(inputApp, options, executor, timing);
      DesugaredLibraryAmender.run(appView);

      if (!options.disableL8AnnotationRemoval) {
        AnnotationRemover.clearAnnotations(appView);
      }

      new PrimaryD8L8IRConverter(appView, timing).convert(appView, executor);

      SyntheticFinalization.finalize(appView, timing, executor);

      appView.setNamingLens(PrefixRewritingNamingLens.createPrefixRewritingNamingLens(appView));
      appView.setNamingLens(
          VarHandleDesugaringRewritingNamingLens.createVarHandleDesugaringRewritingNamingLens(
              appView));
      new GenericSignatureRewriter(appView).run(appView.appInfo().classes(), executor);

      new CfApplicationWriter(appView, options.getMarker()).write(options.getClassFileConsumer());
      options.printWarnings();
    } catch (ExecutionException e) {
      throw unwrapExecutionException(e);
    } finally {
      inputApp.signalFinishedToProviders(options.reporter);
      options.signalFinishedToConsumers();
      // Dump timings.
      if (options.printTimes) {
        timing.report();
      }
    }
  }

  private static AppView<AppInfo> readApp(
      AndroidApp inputApp, InternalOptions options, ExecutorService executor, Timing timing)
      throws IOException {
    LazyLoadedDexApplication lazyApp =
        new ApplicationReader(inputApp, options, timing).read(executor);
    options.loadMachineDesugaredLibrarySpecification(timing, lazyApp);
    TypeRewriter typeRewriter = options.getTypeRewriter();

    DexApplication app = new L8TreePruner(options).prune(lazyApp, typeRewriter);
    return AppView.createForL8(
        AppInfo.createInitialAppInfo(app, GlobalSyntheticsStrategy.forSingleOutputMode()),
        typeRewriter);
  }

  private static void run(String[] args) throws CompilationFailedException {
    L8Command command = L8Command.parse(args, CommandLineOrigin.INSTANCE).build();
    if (command.isPrintHelp()) {
      SelfRetraceTest.test();
      System.out.println(L8CommandParser.getUsageMessage());
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
   * <p>See {@link L8CommandParser#getUsageMessage()} or run {@code l8 --help} for usage
   * information.
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      throw new RuntimeException(
          StringUtils.joinLines("Invalid invocation.", L8CommandParser.getUsageMessage()));
    }
    ExceptionUtils.withMainProgramHandler(() -> run(args));
  }
}
