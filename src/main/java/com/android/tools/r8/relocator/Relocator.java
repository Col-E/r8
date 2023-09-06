// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.relocator;

import static com.android.tools.r8.utils.ExceptionUtils.unwrapExecutionException;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.Keep;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppServices;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.jar.CfApplicationWriter;
import com.android.tools.r8.naming.signature.GenericSignatureRewriter;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

@Keep
public class Relocator {

  private Relocator() {}

  /**
   * Main API entry for Relocator.
   *
   * @param command Relocator command.
   */
  public static void run(RelocatorCommand command) throws CompilationFailedException {
    AndroidApp app = command.getApp();
    InternalOptions options = command.getInternalOptions();
    ExecutorService executor = ThreadUtils.getExecutorService(options);
    ExceptionUtils.withCompilationHandler(
        command.getReporter(),
        () -> {
          try {
            run(command, executor, app, options);
          } finally {
            executor.shutdown();
          }
        });
  }

  /**
   * Main API entry for Relocator with a externally supplied executor service.
   *
   * @param command Relocator command.
   * @param executor executor service from which to get threads for multi-threaded processing.
   */
  public static void run(RelocatorCommand command, ExecutorService executor)
      throws CompilationFailedException {
    AndroidApp app = command.getApp();
    InternalOptions options = command.getInternalOptions();
    ExceptionUtils.withCompilationHandler(
        command.getReporter(), () -> run(command, executor, app, options));
  }

  private static void run(
      RelocatorCommand command,
      ExecutorService executor,
      AndroidApp inputApp,
      InternalOptions options)
      throws IOException {
    Timing timing = Timing.create("Relocator", options);
    try {
      DexApplication app = new ApplicationReader(inputApp, options, timing).read(executor);
      AppInfo appInfo =
          AppInfo.createInitialAppInfo(app, GlobalSyntheticsStrategy.forNonSynthesizing());
      AppView<?> appView = AppView.createForRelocator(appInfo);
      appView.setAppServices(AppServices.builder(appView).build());
      appView.setNamingLens(command.getMapping().compute(appView));
      new GenericSignatureRewriter(appView).run(appInfo.classes(), executor);
      new CfApplicationWriter(appView, new Marker(Tool.Relocator)).write(command.getConsumer());
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
}
