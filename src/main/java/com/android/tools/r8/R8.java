// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.R8Command.USAGE_MESSAGE;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graphinfo.GraphConsumer;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.optimize.EnumOrdinalMapCollector;
import com.android.tools.r8.ir.optimize.MethodPoolCollection;
import com.android.tools.r8.ir.optimize.SwitchMapCollector;
import com.android.tools.r8.ir.optimize.UninstantiatedTypeOptimization;
import com.android.tools.r8.ir.optimize.UnusedArgumentsCollector;
import com.android.tools.r8.jar.CfApplicationWriter;
import com.android.tools.r8.kotlin.Kotlin;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.Minifier;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.naming.ProguardMapApplier;
import com.android.tools.r8.naming.ProguardMapSupplier;
import com.android.tools.r8.naming.SeedMapper;
import com.android.tools.r8.naming.SourceFileRewriter;
import com.android.tools.r8.optimize.ClassAndMemberPublicizer;
import com.android.tools.r8.optimize.MemberRebindingAnalysis;
import com.android.tools.r8.optimize.VisibilityBridgeRemover;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.shaking.AbstractMethodRemover;
import com.android.tools.r8.shaking.AnnotationRemover;
import com.android.tools.r8.shaking.DiscardedChecker;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.shaking.MainDexClasses;
import com.android.tools.r8.shaking.MainDexListBuilder;
import com.android.tools.r8.shaking.ProguardClassFilter;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.RootSetBuilder;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.shaking.StaticClassMerger;
import com.android.tools.r8.shaking.TreePruner;
import com.android.tools.r8.shaking.VerticalClassMerger;
import com.android.tools.r8.shaking.WhyAreYouKeepingConsumer;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.CfgPrinter;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import com.android.tools.r8.utils.LineNumberOptimizer;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.SelfRetraceTest;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.VersionProperties;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * The R8 compiler.
 *
 * <p>R8 performs whole-program optimizing compilation of Java bytecode. It supports compilation of
 * Java bytecode to Java bytecode or DEX bytecode. R8 supports tree-shaking the program to remove
 * unneeded code and it supports minification of the program names to reduce the size of the
 * resulting program.
 *
 * <p>The R8 API is intentionally limited and should "do the right thing" given a command. If this
 * API does not suffice please contact the D8/R8 team.
 *
 * <p>R8 supports some configuration using configuration files mostly compatible with the format of
 * the <a href="https://www.guardsquare.com/en/proguard">ProGuard</a> optimizer.
 *
 * <p>The compiler is invoked by calling {@link #run(R8Command) R8.run} with an appropriate {link
 * R8Command}. For example:
 *
 * <pre>
 *   R8.run(R8Command.builder()
 *       .addProgramFiles(inputPathA, inputPathB)
 *       .addLibraryFiles(androidJar)
 *       .setOutput(outputPath, OutputMode.DexIndexed)
 *       .build());
 * </pre>
 *
 * The above reads the input files denoted by {@code inputPathA} and {@code inputPathB}, compiles
 * them to DEX bytecode, using {@code androidJar} as the reference of the system runtime library,
 * and then writes the result to the directory or zip archive specified by {@code outputPath}.
 */
@Keep
public class R8 {

  private final Timing timing = new Timing("R8");
  private final InternalOptions options;

  private R8(InternalOptions options) {
    this.options = options;
    options.itemFactory.resetSortedIndices();
  }

  /**
   * Main API entry for the R8 compiler.
   *
   * <p>The R8 API is intentionally limited and should "do the right thing" given a command. If this
   * API does not suffice please contact the R8 team.
   *
   * @param command R8 command.
   */
  public static void run(R8Command command) throws CompilationFailedException {
    AndroidApp app = command.getInputApp();
    InternalOptions options = command.getInternalOptions();
    runForTesting(app, options);
  }

  /**
   * Main API entry for the R8 compiler.
   *
   * <p>The R8 API is intentionally limited and should "do the right thing" given a command. If this
   * API does not suffice please contact the R8 team.
   *
   * @param command R8 command.
   * @param executor executor service from which to get threads for multi-threaded processing.
   */
  public static void run(R8Command command, ExecutorService executor)
      throws CompilationFailedException {
    AndroidApp app = command.getInputApp();
    InternalOptions options = command.getInternalOptions();
    ExceptionUtils.withR8CompilationHandler(
        command.getReporter(),
        () -> {
          run(app, options, executor);
        });
  }

  // Compute the marker to be placed in the main dex file.
  private static Marker getMarker(InternalOptions options) {
    if (options.hasMarker()) {
      return options.getMarker();
    }
    Marker marker = new Marker(Tool.R8)
        .setVersion(Version.LABEL)
        .setCompilationMode(options.debug ? CompilationMode.DEBUG : CompilationMode.RELEASE)
        .setMinApi(options.minApiLevel);
    if (Version.isDev()) {
      marker.setSha1(VersionProperties.INSTANCE.getSha());
    }
    return marker;
  }

  static void writeApplication(
      ExecutorService executorService,
      DexApplication application,
      String deadCode,
      GraphLense graphLense,
      NamingLens namingLens,
      String proguardSeedsData,
      InternalOptions options,
      ProguardMapSupplier proguardMapSupplier)
      throws ExecutionException {
    try {
      Marker marker = getMarker(options);
      if (options.isGeneratingClassFiles()) {
        new CfApplicationWriter(
                application,
                options,
                deadCode,
                graphLense,
                namingLens,
                proguardSeedsData,
                proguardMapSupplier)
            .write(options.getClassFileConsumer(), executorService);
      } else {
        new ApplicationWriter(
                application,
                options,
                marker == null ? null : Collections.singletonList(marker),
                deadCode,
                graphLense,
                namingLens,
                proguardSeedsData,
                proguardMapSupplier)
            .write(executorService);
      }
    } catch (IOException e) {
      throw new RuntimeException("Cannot write application", e);
    }
  }

  private Set<DexType> filterMissingClasses(Set<DexType> missingClasses,
      ProguardClassFilter dontWarnPatterns) {
    Set<DexType> result = new HashSet<>(missingClasses);
    dontWarnPatterns.filterOutMatches(result);
    return result;
  }

  static void runForTesting(AndroidApp app, InternalOptions options)
      throws CompilationFailedException {
    ExecutorService executor = ThreadUtils.getExecutorService(options);
    ExceptionUtils.withR8CompilationHandler(
        options.reporter,
        () -> {
          try {
            run(app, options, executor);
          } finally {
            executor.shutdown();
          }
        });
  }

  private static void run(AndroidApp app, InternalOptions options, ExecutorService executor)
      throws IOException {
    new R8(options).run(app, executor);
  }

  private void run(AndroidApp inputApp, ExecutorService executorService) throws IOException {
    assert options.programConsumer != null;
    if (options.quiet) {
      System.setOut(new PrintStream(ByteStreams.nullOutputStream()));
    }
    try {
      DexApplication application =
          new ApplicationReader(inputApp, options, timing).read(executorService).toDirect();

      // Now that the dex-application is fully loaded, close any internal archive providers.
      inputApp.closeInternalArchiveProviders();

      AppView<AppInfoWithSubtyping> appView =
          new AppView<>(
              new AppInfoWithSubtyping(application), GraphLense.getIdentityLense(), options);
      RootSet rootSet;
      String proguardSeedsData = null;
      timing.begin("Strip unused code");
      try {
        Set<DexType> missingClasses = appView.appInfo().getMissingClasses();
        missingClasses = filterMissingClasses(
            missingClasses, options.getProguardConfiguration().getDontWarnPatterns());
        if (!missingClasses.isEmpty()) {
          missingClasses.forEach(
              clazz -> {
                options.reporter.warning(
                    new StringDiagnostic("Missing class: " + clazz.toSourceString()));
              });
          if (!options.ignoreMissingClasses) {
            throw new CompilationError(
                "Compilation can't be completed because some library classes are missing.");
          }
        }

        // Compute kotlin info before setting the roots and before
        // kotlin metadata annotation is removed.
        computeKotlinInfoForProgramClasses(application, appView.appInfo());

        ProguardConfiguration.Builder compatibility =
            ProguardConfiguration.builder(application.dexItemFactory, options.reporter);

        rootSet =
            new RootSetBuilder(
                    appView, application, options.getProguardConfiguration().getRules(), options)
                .run(executorService);

        Enqueuer enqueuer = new Enqueuer(appView, options, null, compatibility);
        appView.setAppInfo(
            enqueuer.traceApplication(
                rootSet,
                options.getProguardConfiguration().getDontWarnPatterns(),
                executorService,
                timing));
        assert rootSet.verifyKeptFieldsAreAccessedAndLive(appView.appInfo().withLiveness());
        assert rootSet.verifyKeptMethodsAreTargetedAndLive(appView.appInfo().withLiveness());
        assert rootSet.verifyKeptTypesAreLive(appView.appInfo().withLiveness());

        if (options.getProguardConfiguration().isPrintSeeds()) {
          ByteArrayOutputStream bytes = new ByteArrayOutputStream();
          PrintStream out = new PrintStream(bytes);
          RootSetBuilder.writeSeeds(appView.appInfo().withLiveness(), out, type -> true);
          out.flush();
          proguardSeedsData = bytes.toString();
        }
        if (options.enableTreeShaking) {
          TreePruner pruner =
              new TreePruner(application, appView.appInfo().withLiveness(), options);
          application = pruner.run();

          // Recompute the subtyping information.
          appView.setAppInfo(
              appView
                  .appInfo()
                  .withLiveness()
                  .prunedCopyFrom(application, pruner.getRemovedClasses()));
          new AbstractMethodRemover(appView.appInfo().withLiveness()).run();
        }

        new AnnotationRemover(appView.appInfo().withLiveness(), appView.graphLense(), options)
            .ensureValid(compatibility)
            .run();

        // TODO(69445518): This is still work in progress, and this file writing is currently used
        // for testing.
        if (options.forceProguardCompatibility
            && options.proguardCompatibilityRulesOutput != null) {
          try (Closer closer = Closer.create()) {
            OutputStream outputStream =
                FileUtils.openPath(
                    closer,
                    options.proguardCompatibilityRulesOutput,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            PrintStream ps = new PrintStream(outputStream);
            ps.println(compatibility.buildRaw().toString());
          }
        }
      } finally {
        timing.end();
      }

      if (options.getProguardConfiguration().isAccessModificationAllowed()) {
        appView.setGraphLense(
            ClassAndMemberPublicizer.run(executorService, timing, application, appView, rootSet));
        // We can now remove visibility bridges. Note that we do not need to update the
        // invoke-targets here, as the existing invokes will simply dispatch to the now
        // visible super-method. MemberRebinding, if run, will then dispatch it correctly.
        application = new VisibilityBridgeRemover(appView.appInfo(), application).run();
      }

      // Build conservative main dex content before first round of tree shaking. This is used
      // by certain optimizations to avoid introducing additional class references into main dex
      // classes, as that can cause the final number of main dex methods to grow.
      RootSet mainDexRootSet = null;
      MainDexClasses mainDexClasses = MainDexClasses.NONE;
      if (!options.mainDexKeepRules.isEmpty()) {
        // Find classes which may have code executed before secondary dex files installation.
        mainDexRootSet =
            new RootSetBuilder(appView, application, options.mainDexKeepRules, options)
                .run(executorService);
        Enqueuer enqueuer = new Enqueuer(appView, options, null, true);
        AppInfoWithLiveness mainDexAppInfo =
            enqueuer.traceMainDex(mainDexRootSet, executorService, timing);
        // LiveTypes is the tracing result.
        Set<DexType> mainDexBaseClasses = new HashSet<>(mainDexAppInfo.liveTypes);
        // Calculate the automatic main dex list according to legacy multidex constraints.
        mainDexClasses = new MainDexListBuilder(mainDexBaseClasses, application).run();
      }

      if (appView.appInfo().hasLiveness()) {
        AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();

        if (options.getProguardConfiguration().hasApplyMappingFile()) {
          SeedMapper seedMapper =
              SeedMapper.seedMapperFromFile(
                  options.getProguardConfiguration().getApplyMappingFile());
          timing.begin("apply-mapping");
          appView.setGraphLense(
              new ProguardMapApplier(appView.withLiveness(), seedMapper).run(timing));
          application = application.asDirect().rewrittenWithLense(appView.graphLense());
          appView.setAppInfo(
              appView
                  .appInfo()
                  .withLiveness()
                  .rewrittenWithLense(application.asDirect(), appView.graphLense()));
          timing.end();
        }
        appView.setGraphLense(new MemberRebindingAnalysis(appViewWithLiveness, options).run());
        if (options.enableHorizontalClassMerging) {
          StaticClassMerger staticClassMerger =
              new StaticClassMerger(appViewWithLiveness, options, mainDexClasses);
          appView.setGraphLense(staticClassMerger.run());
          appViewWithLiveness.setAppInfo(
              appViewWithLiveness
                  .appInfo()
                  .rewrittenWithLense(application.asDirect(), appView.graphLense()));
        }
        if (options.enableVerticalClassMerging) {
          timing.begin("ClassMerger");
          VerticalClassMerger verticalClassMerger =
              new VerticalClassMerger(
                  application,
                  appViewWithLiveness,
                  executorService,
                  options,
                  timing,
                  mainDexClasses);
          appView.setGraphLense(verticalClassMerger.run());
          appView.setVerticallyMergedClasses(verticalClassMerger.getMergedClasses());
          timing.end();
          application = application.asDirect().rewrittenWithLense(appView.graphLense());
          appViewWithLiveness.setAppInfo(
              appViewWithLiveness
                  .appInfo()
                  .prunedCopyFrom(application, verticalClassMerger.getRemovedClasses())
                  .rewrittenWithLense(application.asDirect(), appView.graphLense()));
        }
        if (options.enableArgumentRemoval) {
          if (options.enableUnusedArgumentRemoval) {
            timing.begin("UnusedArgumentRemoval");
            appView.setGraphLense(new UnusedArgumentsCollector(appViewWithLiveness).run());
            application = application.asDirect().rewrittenWithLense(appView.graphLense());
            timing.end();
            appViewWithLiveness.setAppInfo(
                appViewWithLiveness
                    .appInfo()
                    .rewrittenWithLense(application.asDirect(), appView.graphLense()));
          }
          if (options.enableUninstantiatedTypeOptimization) {
            timing.begin("UninstantiatedTypeOptimization");
            appView.setGraphLense(
                new UninstantiatedTypeOptimization(appViewWithLiveness, options)
                    .run(new MethodPoolCollection(application), executorService, timing));
            application = application.asDirect().rewrittenWithLense(appView.graphLense());
            timing.end();
            appViewWithLiveness.setAppInfo(
                appViewWithLiveness
                    .appInfo()
                    .rewrittenWithLense(application.asDirect(), appView.graphLense()));
          }
        }

        // Collect switch maps and ordinals maps.
        appViewWithLiveness.setAppInfo(new SwitchMapCollector(appViewWithLiveness, options).run());
        appViewWithLiveness.setAppInfo(
            new EnumOrdinalMapCollector(appViewWithLiveness, options).run());

        // TODO(b/79143143): re-enable once fixed.
        // graphLense = new BridgeMethodAnalysis(graphLense, appInfo.withLiveness()).run();
      }

      timing.begin("Create IR");
      Set<DexCallSite> desugaredCallSites;
      CfgPrinter printer = options.printCfg ? new CfgPrinter() : null;
      try {
        IRConverter converter =
            new IRConverter(appView, options, timing, printer, mainDexClasses, rootSet);
        application = converter.optimize(application, executorService);
        desugaredCallSites = converter.getDesugaredCallSites();
      } finally {
        timing.end();
      }

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

      // Overwrite SourceFile if specified. This step should be done after IR conversion.
      timing.begin("Rename SourceFile");
      new SourceFileRewriter(appView.appInfo(), options).run();
      timing.end();

      if (!options.mainDexKeepRules.isEmpty()) {
        appView.setAppInfo(new AppInfoWithSubtyping(application));
        // No need to build a new main dex root set
        assert mainDexRootSet != null;
        GraphConsumer mainDexKeptGraphConsumer = options.mainDexKeptGraphConsumer;
        WhyAreYouKeepingConsumer whyAreYouKeepingConsumer = null;
        if (!mainDexRootSet.reasonAsked.isEmpty()) {
          whyAreYouKeepingConsumer = new WhyAreYouKeepingConsumer(mainDexKeptGraphConsumer);
          mainDexKeptGraphConsumer = whyAreYouKeepingConsumer;
        }

        Enqueuer enqueuer = new Enqueuer(appView, options, mainDexKeptGraphConsumer, true);
        // Find classes which may have code executed before secondary dex files installation.
        AppInfoWithLiveness mainDexAppInfo =
            enqueuer.traceMainDex(mainDexRootSet, executorService, timing);
        // LiveTypes is the tracing result.
        Set<DexType> mainDexBaseClasses = new HashSet<>(mainDexAppInfo.liveTypes);
        // Calculate the automatic main dex list according to legacy multidex constraints.
        mainDexClasses = new MainDexListBuilder(mainDexBaseClasses, application).run();
        if (!mainDexRootSet.checkDiscarded.isEmpty()) {
          new DiscardedChecker(
                  mainDexRootSet, mainDexClasses.getClasses(), appView.appInfo(), options)
              .run();
        }
        if (whyAreYouKeepingConsumer != null) {
          for (DexDefinition dexDefinition : mainDexRootSet.reasonAsked) {
            whyAreYouKeepingConsumer.printWhyAreYouKeeping(
                enqueuer.getGraphNode(dexDefinition), System.out);
          }
        }
      }

      appView.setAppInfo(new AppInfoWithSubtyping(application));

      if (options.enableTreeShaking || options.enableMinification) {
        timing.begin("Post optimization code stripping");
        try {

          GraphConsumer keptGraphConsumer = null;
          WhyAreYouKeepingConsumer whyAreYouKeepingConsumer = null;
          if (options.enableTreeShaking) {
            keptGraphConsumer = options.keptGraphConsumer;
            if (!rootSet.reasonAsked.isEmpty()) {
              whyAreYouKeepingConsumer = new WhyAreYouKeepingConsumer(keptGraphConsumer);
              keptGraphConsumer = whyAreYouKeepingConsumer;
            }
          }

          Enqueuer enqueuer = new Enqueuer(appView, options, keptGraphConsumer);
          appView.setAppInfo(
              enqueuer.traceApplication(
                  rootSet,
                  options.getProguardConfiguration().getDontWarnPatterns(),
                  executorService,
                  timing));

          AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
          if (options.enableTreeShaking) {
            TreePruner pruner = new TreePruner(application, appViewWithLiveness.appInfo(), options);
            application = pruner.run();
            appViewWithLiveness.setAppInfo(
                appViewWithLiveness
                    .appInfo()
                    .prunedCopyFrom(application, pruner.getRemovedClasses()));

            // Print reasons on the application after pruning, so that we reflect the actual result.
            if (whyAreYouKeepingConsumer != null) {
              for (DexDefinition dexDefinition : rootSet.reasonAsked) {
                whyAreYouKeepingConsumer.printWhyAreYouKeeping(
                    enqueuer.getGraphNode(dexDefinition), System.out);
              }
            }
            // Remove annotations that refer to types that no longer exist.
            new AnnotationRemover(appView.appInfo().withLiveness(), appView.graphLense(), options)
                .run();
            if (!mainDexClasses.isEmpty()) {
              // Remove types that no longer exists from the computed main dex list.
              mainDexClasses = mainDexClasses.prunedCopy(appView.appInfo().withLiveness());
            }
          }
        } finally {
          timing.end();
        }
      }

      // Add automatic main dex classes to an eventual manual list of classes.
      if (!options.mainDexKeepRules.isEmpty()) {
        application = application.builder().addToMainDexList(mainDexClasses.getClasses()).build();
      }

      // Only perform discard-checking if tree-shaking is turned on.
      if (options.enableTreeShaking && !rootSet.checkDiscarded.isEmpty()) {
        new DiscardedChecker(rootSet, application, options).run();
      }

      timing.begin("Minification");
      // If we did not have keep rules, everything will be marked as keep, so no minification
      // will happen. Just avoid the overhead.
      NamingLens namingLens =
          options.enableMinification
              ? new Minifier(appView.appInfo().withLiveness(), rootSet, desugaredCallSites, options)
                  .run(timing)
              : NamingLens.getIdentityLens();
      timing.end();

      ProguardMapSupplier proguardMapSupplier;

      timing.begin("Line number remapping");
      // When line number optimization is turned off the identity mapping for line numbers is
      // used. We still run the line number optimizer to collect line numbers and inline frame
      // information for the mapping file.
      ClassNameMapper classNameMapper =
          LineNumberOptimizer.run(
              application,
              appView.graphLense(),
              namingLens,
              options.lineNumberOptimization == LineNumberOptimization.OFF);
      timing.end();
      proguardMapSupplier =
          ProguardMapSupplier.fromClassNameMapper(classNameMapper, options.minApiLevel);

      // If a method filter is present don't produce output since the application is likely partial.
      if (options.hasMethodsFilter()) {
        System.out.println("Finished compilation with method filter: ");
        options.methodsFilter.forEach(m -> System.out.println("  - " + m));
        return;
      }

      // Validity checks.
      assert application.classes().stream().allMatch(DexClass::isValid);
      assert rootSet.verifyKeptItemsAreKept(application, appView.appInfo(), options);

      // Generate the resulting application resources.
      writeApplication(
          executorService,
          application,
          application.deadCode,
          appView.graphLense(),
          namingLens,
          proguardSeedsData,
          options,
          proguardMapSupplier);

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

  private void computeKotlinInfoForProgramClasses(
      DexApplication application, AppInfoWithSubtyping appInfo) {
    Kotlin kotlin = appInfo.dexItemFactory.kotlin;
    Reporter reporter = options.reporter;
    for (DexProgramClass programClass : application.classes()) {
      programClass.setKotlinInfo(kotlin.getKotlinInfo(programClass, reporter));
    }
  }

  static RuntimeException unwrapExecutionException(ExecutionException executionException) {
    Throwable cause = executionException.getCause();
    if (cause instanceof Error) {
      // add original exception as suppressed exception to provide the original stack trace
      cause.addSuppressed(executionException);
      throw (Error) cause;
    } else if (cause instanceof RuntimeException) {
      cause.addSuppressed(executionException);
      throw (RuntimeException) cause;
    } else {
      throw new RuntimeException(executionException.getMessage(), cause);
    }
  }

  private static void run(String[] args) throws CompilationFailedException {
    R8Command command = R8Command.parse(args, CommandLineOrigin.INSTANCE).build();
    if (command.isPrintHelp()) {
      SelfRetraceTest.test();
      System.out.println(USAGE_MESSAGE);
      return;
    }
    if (command.isPrintVersion()) {
      Version.printToolVersion("R8");
      return;
    }
    InternalOptions options = command.getInternalOptions();
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    try {
      ExceptionUtils.withR8CompilationHandler(options.reporter, () ->
          run(command.getInputApp(), options, executorService));
    } finally {
      executorService.shutdown();
    }
  }

  /**
   * Command-line entry to R8.
   *
   * See {@link R8Command#USAGE_MESSAGE} or run {@code r8 --help} for usage information.
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      System.err.println(USAGE_MESSAGE);
      System.exit(ExceptionUtils.STATUS_ERROR);
    }
    ExceptionUtils.withMainProgramHandler(() -> run(args));
  }
}
