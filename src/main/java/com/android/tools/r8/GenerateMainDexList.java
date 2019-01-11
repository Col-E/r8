// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.experimental.graphinfo.GraphConsumer;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.shaking.DiscardedChecker;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.shaking.MainDexClasses;
import com.android.tools.r8.shaking.MainDexListBuilder;
import com.android.tools.r8.shaking.RootSetBuilder;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.shaking.WhyAreYouKeepingConsumer;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Keep
public class GenerateMainDexList {
  private final Timing timing = new Timing("maindex");
  private final InternalOptions options;

  private GenerateMainDexList(InternalOptions options) {
    this.options = options;
  }

  private List<String> run(AndroidApp app, ExecutorService executor)
      throws IOException {
    try {
      DexApplication application =
          new ApplicationReader(app, options, timing).read(executor).toDirect();
      AppView<? extends AppInfoWithSubtyping> appView =
          new AppView<>(
              new AppInfoWithSubtyping(application), GraphLense.getIdentityLense(), options);
      RootSet mainDexRootSet =
          new RootSetBuilder(appView, application, options.mainDexKeepRules, options).run(executor);

      GraphConsumer graphConsumer = options.mainDexKeptGraphConsumer;
      WhyAreYouKeepingConsumer whyAreYouKeepingConsumer = null;
      if (!mainDexRootSet.reasonAsked.isEmpty()) {
        whyAreYouKeepingConsumer = new WhyAreYouKeepingConsumer(graphConsumer);
        graphConsumer = whyAreYouKeepingConsumer;
      }

      Enqueuer enqueuer = new Enqueuer(appView, options, graphConsumer, true);
      AppInfoWithLiveness mainDexAppInfo = enqueuer.traceMainDex(mainDexRootSet, executor, timing);
      // LiveTypes is the result.
      MainDexClasses mainDexClasses =
          new MainDexListBuilder(new HashSet<>(mainDexAppInfo.liveTypes), application).run();

      List<String> result =
          mainDexClasses.getClasses().stream()
              .map(c -> c.toSourceString().replace('.', '/') + ".class")
              .sorted()
              .collect(Collectors.toList());

      if (options.mainDexListConsumer != null) {
        options.mainDexListConsumer.accept(String.join("\n", result), options.reporter);
      }

      if (!mainDexRootSet.checkDiscarded.isEmpty()) {
        new DiscardedChecker(
            mainDexRootSet, mainDexClasses.getClasses(), appView.appInfo(), options)
            .run();
      }
      // Print -whyareyoukeeping results if any.
      if (whyAreYouKeepingConsumer != null) {
        for (DexDefinition definition : mainDexRootSet.reasonAsked) {
          whyAreYouKeepingConsumer.printWhyAreYouKeeping(
              enqueuer.getGraphNode(definition), System.out);
        }
      }

      return result;
    } catch (ExecutionException e) {
      throw R8.unwrapExecutionException(e);
    }
  }

  /**
   * Main API entry for computing the main-dex list.
   *
   * The main-dex list is represented as a list of strings, each string specifies one class to
   * keep in the primary dex file (<code>classes.dex</code>).
   *
   * A class is specified using the following format: "com/example/MyClass.class". That is
   * "/" as separator between package components, and a trailing ".class".
   *
   * @param command main dex-list generator command.
   * @return classes to keep in the primary dex file.
   */
  public static List<String> run(GenerateMainDexListCommand command)
      throws CompilationFailedException {
    ExecutorService executorService = ThreadUtils.getExecutorService(command.getInternalOptions());
    try {
      return run(command, executorService);
    } finally {
      executorService.shutdown();
    }
  }

  /**
   * Main API entry for computing the main-dex list.
   *
   * The main-dex list is represented as a list of strings, each string specifies one class to
   * keep in the primary dex file (<code>classes.dex</code>).
   *
   * A class is specified using the following format: "com/example/MyClass.class". That is
   * "/" as separator between package components, and a trailing ".class".
   *
   * @param command main dex-list generator command.
   * @param executor executor service from which to get threads for multi-threaded processing.
   * @return classes to keep in the primary dex file.
   */
  public static List<String> run(GenerateMainDexListCommand command, ExecutorService executor)
      throws CompilationFailedException {
    AndroidApp app = command.getInputApp();
    InternalOptions options = command.getInternalOptions();
    ResultBox result = new ResultBox();

    ExceptionUtils.withMainDexListHandler(
        command.getReporter(),
        () -> {
          try {
            result.content = new GenerateMainDexList(options).run(app, executor);
          } finally {
            executor.shutdown();
          }
        });
    return result.content;
  }

  private static class ResultBox {
    List<String> content;
  }

  public static void main(String[] args) throws CompilationFailedException {
    GenerateMainDexListCommand.Builder builder = GenerateMainDexListCommand.parse(args);
    GenerateMainDexListCommand command = builder.build();
    if (command.isPrintHelp()) {
      System.out.println(GenerateMainDexListCommand.USAGE_MESSAGE);
      return;
    }
    if (command.isPrintVersion()) {
      System.out.println("MainDexListGenerator " + Version.LABEL);
      return;
    }
    List<String> result = run(command);
    if (command.getMainDexListConsumer() == null) {
      result.forEach(System.out::println);
    }
  }
}
