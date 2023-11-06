// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.ExceptionUtils.unwrapExecutionException;

import com.android.tools.r8.StringConsumer.ForwardingConsumer;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.experimental.graphinfo.GraphConsumer;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppServices;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.EnqueuerFactory;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.shaking.MainDexListBuilder;
import com.android.tools.r8.shaking.RootSetUtils.MainDexRootSet;
import com.android.tools.r8.shaking.WhyAreYouKeepingConsumer;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.SortingStringConsumer;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

@KeepForApi
public class GenerateMainDexList {
  private final Timing timing = new Timing("maindex");
  private final InternalOptions options;

  public GenerateMainDexList(InternalOptions options) {
    this.options = options;
  }

  private void run(AndroidApp app, ExecutorService executor, SortingStringConsumer consumer)
      throws IOException {
    try {
      DexApplication application = new ApplicationReader(app, options, timing).read(executor);
      traceMainDexForGenerateMainDexList(executor, application)
          .forEach(type -> consumer.accept(type.toBinaryName() + ".class", options.reporter));
      consumer.finished(options.reporter);
    } catch (ExecutionException e) {
      throw unwrapExecutionException(e);
    }
  }

  public MainDexInfo traceMainDexForD8(AppView<AppInfo> appView, ExecutorService executor)
      throws ExecutionException {
    return traceMainDex(
        AppView.createForSimulatingR8InD8(
            appView.app().toDirect(), appView.appInfo().getMainDexInfo()),
        executor);
  }

  public MainDexInfo traceMainDexForGenerateMainDexList(
      ExecutorService executor, DexApplication application) throws ExecutionException {
    return traceMainDex(AppView.createForR8(application.toDirect()), executor);
  }

  private MainDexInfo traceMainDex(
      AppView<? extends AppInfoWithClassHierarchy> appView, ExecutorService executor)
      throws ExecutionException {
    appView.setAppServices(AppServices.builder(appView).build());

    MainDexListBuilder.checkForAssumedLibraryTypes(appView.appInfo());

    SubtypingInfo subtypingInfo = SubtypingInfo.create(appView);

    ProfileCollectionAdditions profileCollectionAdditions = ProfileCollectionAdditions.nop();
    MainDexRootSet mainDexRootSet =
        MainDexRootSet.builder(
                appView, profileCollectionAdditions, subtypingInfo, options.mainDexKeepRules)
            .build(executor);
    appView.setMainDexRootSet(mainDexRootSet);

    GraphConsumer graphConsumer = options.mainDexKeptGraphConsumer;
    WhyAreYouKeepingConsumer whyAreYouKeepingConsumer = null;
    if (!mainDexRootSet.reasonAsked.isEmpty()) {
      whyAreYouKeepingConsumer = new WhyAreYouKeepingConsumer(graphConsumer);
      graphConsumer = whyAreYouKeepingConsumer;
    }

    Enqueuer enqueuer =
        EnqueuerFactory.createForGenerateMainDexList(
            appView, executor, subtypingInfo, graphConsumer);
    MainDexInfo mainDexInfo = enqueuer.traceMainDex(executor, timing);
    R8.processWhyAreYouKeepingAndCheckDiscarded(
        mainDexRootSet,
        () -> {
          ArrayList<DexProgramClass> classes = new ArrayList<>();
          // TODO(b/131668850): This is not a deterministic order!
          mainDexInfo.forEach(
              type -> {
                DexClass clazz = appView.definitionFor(type);
                assert clazz.isProgramClass();
                classes.add(clazz.asProgramClass());
              });
          return classes;
        },
        whyAreYouKeepingConsumer,
        appView,
        enqueuer,
        true,
        options,
        timing,
        executor);

    return mainDexInfo;
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
    AndroidApp app = command.getInputApp();
    InternalOptions options = command.getInternalOptions();
    return runForTesting(app, options);
  }

  /**
   * Main API entry for computing the main-dex list.
   *
   * <p>The main-dex list is represented as a list of strings, each string specifies one class to
   * keep in the primary dex file (<code>classes.dex</code>).
   *
   * <p>A class is specified using the following format: "com/example/MyClass.class". That is "/" as
   * separator between package components, and a trailing ".class".
   *
   * @param command main dex-list generator command.
   * @param executor executor service from which to get threads for multi-threaded processing.
   * @return classes to keep in the primary dex file.
   */
  public static List<String> run(GenerateMainDexListCommand command, ExecutorService executor)
      throws CompilationFailedException {
    AndroidApp app = command.getInputApp();
    InternalOptions options = command.getInternalOptions();
    List<String> result = new ArrayList<>();
    ExceptionUtils.withMainDexListHandler(
        command.getReporter(), () -> run(app, executor, options, result));
    return result;
  }

  static List<String> runForTesting(AndroidApp app, InternalOptions options)
      throws CompilationFailedException {
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    List<String> result = new ArrayList<>();
    ExceptionUtils.withMainDexListHandler(
        options.reporter,
        () -> {
          try {
            run(app, executorService, options, result);
          } finally {
            executorService.shutdown();
          }
        });
    return result;
  }

  private static void run(
      AndroidApp app, ExecutorService executor, InternalOptions options, List<String> result)
      throws IOException {
    new GenerateMainDexList(options)
        .run(
            app,
            executor,
            new SortingStringConsumer(
                new ForwardingConsumer(options.mainDexListConsumer) {
                  @Override
                  public void accept(String string, DiagnosticsHandler handler) {
                    result.add(string);
                    super.accept(string, handler);
                  }
                }));
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
