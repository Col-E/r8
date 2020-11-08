// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.R8Command.USAGE_MESSAGE;
import static com.android.tools.r8.utils.ExceptionUtils.unwrapExecutionException;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.errors.CheckDiscardDiagnostic;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.experimental.graphinfo.GraphConsumer;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppServices;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.AppliedGraphLens;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugEvent;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.DirectMappedDexApplication.Builder;
import com.android.tools.r8.graph.EnumValueInfoMapCollection;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.NestedGraphLens;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.graph.analysis.ClassInitializerAssertionEnablingAnalysis;
import com.android.tools.r8.graph.analysis.InitializedClassesInInstanceMethodsAnalysis;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMergerGraphLens;
import com.android.tools.r8.inspector.internal.InspectorImpl;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter;
import com.android.tools.r8.ir.desugar.DesugaredLibraryRetargeter;
import com.android.tools.r8.ir.desugar.InterfaceMethodRewriter;
import com.android.tools.r8.ir.desugar.NestedPrivateMethodLens;
import com.android.tools.r8.ir.desugar.R8NestBasedAccessDesugaring;
import com.android.tools.r8.ir.optimize.AssertionsRewriter;
import com.android.tools.r8.ir.optimize.MethodPoolCollection;
import com.android.tools.r8.ir.optimize.NestReducer;
import com.android.tools.r8.ir.optimize.SwitchMapCollector;
import com.android.tools.r8.ir.optimize.UninstantiatedTypeOptimization;
import com.android.tools.r8.ir.optimize.UninstantiatedTypeOptimization.UninstantiatedTypeOptimizationGraphLens;
import com.android.tools.r8.ir.optimize.UnusedArgumentsCollector;
import com.android.tools.r8.ir.optimize.UnusedArgumentsCollector.UnusedArgumentsGraphLens;
import com.android.tools.r8.ir.optimize.enums.EnumUnboxingCfMethods;
import com.android.tools.r8.ir.optimize.enums.EnumValueInfoMapCollector;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.ir.optimize.templates.CfUtilityMethodsForCodeOptimizations;
import com.android.tools.r8.jar.CfApplicationWriter;
import com.android.tools.r8.kotlin.KotlinMetadataRewriter;
import com.android.tools.r8.kotlin.KotlinMetadataUtils;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.Minifier;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.naming.PrefixRewritingNamingLens;
import com.android.tools.r8.naming.ProguardMapMinifier;
import com.android.tools.r8.naming.ProguardMapSupplier;
import com.android.tools.r8.naming.SeedMapper;
import com.android.tools.r8.naming.SourceFileRewriter;
import com.android.tools.r8.naming.signature.GenericSignatureRewriter;
import com.android.tools.r8.optimize.ClassAndMemberPublicizer;
import com.android.tools.r8.optimize.MemberRebindingAnalysis;
import com.android.tools.r8.optimize.MemberRebindingIdentityLens;
import com.android.tools.r8.optimize.MemberRebindingIdentityLensFactory;
import com.android.tools.r8.optimize.VisibilityBridgeRemover;
import com.android.tools.r8.optimize.bridgehoisting.BridgeHoisting;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.repackaging.Repackaging;
import com.android.tools.r8.repackaging.RepackagingLens;
import com.android.tools.r8.shaking.AbstractMethodRemover;
import com.android.tools.r8.shaking.AnnotationRemover;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ClassInitFieldSynthesizer;
import com.android.tools.r8.shaking.DefaultTreePrunerConfiguration;
import com.android.tools.r8.shaking.DiscardedChecker;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.Enqueuer.Mode;
import com.android.tools.r8.shaking.EnqueuerFactory;
import com.android.tools.r8.shaking.MainDexClasses;
import com.android.tools.r8.shaking.MainDexListBuilder;
import com.android.tools.r8.shaking.MainDexTracingResult;
import com.android.tools.r8.shaking.ProguardClassFilter;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardConfigurationUtils;
import com.android.tools.r8.shaking.RootSetBuilder;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.shaking.RuntimeTypeCheckInfo;
import com.android.tools.r8.shaking.StaticClassMerger;
import com.android.tools.r8.shaking.TreePruner;
import com.android.tools.r8.shaking.TreePrunerConfiguration;
import com.android.tools.r8.shaking.VerticalClassMerger;
import com.android.tools.r8.shaking.VerticalClassMergerGraphLens;
import com.android.tools.r8.shaking.WhyAreYouKeepingConsumer;
import com.android.tools.r8.synthesis.SyntheticFinalization;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.CfgPrinter;
import com.android.tools.r8.utils.CollectionUtils;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.LineNumberOptimizer;
import com.android.tools.r8.utils.SelfRetraceTest;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

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

  private final Timing timing;
  private final InternalOptions options;

  private R8(InternalOptions options) {
    this.options = options;
    if (options.printMemory) {
      System.gc();
    }
    timing = Timing.create("R8", options);
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

  static void writeApplication(
      ExecutorService executorService,
      AppView<?> appView,
      GraphLens graphLens,
      InitClassLens initClassLens,
      NamingLens namingLens,
      InternalOptions options,
      ProguardMapSupplier proguardMapSupplier)
      throws ExecutionException {
    InspectorImpl.runInspections(options.outputInspections, appView.appInfo().classes());
    try {
      Marker marker = options.getMarker(Tool.R8);
      assert marker != null;
      // Get the markers from the input which are different from the one created for this
      // compilation
      Set<Marker> markers = new HashSet<>(options.itemFactory.extractMarkers());
      markers.remove(marker);
      if (options.isGeneratingClassFiles()) {
        new CfApplicationWriter(appView, marker, graphLens, namingLens, proguardMapSupplier)
            .write(options.getClassFileConsumer());
      } else {
        new ApplicationWriter(
                appView,
                // Ensure that the marker for this compilation is the first in the list.
                ImmutableList.<Marker>builder().add(marker).addAll(markers).build(),
                graphLens,
                initClassLens,
                namingLens,
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

  private static DirectMappedDexApplication getDirectApp(AppView<?> appView) {
    return appView.appInfo().app().asDirect();
  }

  private void run(AndroidApp inputApp, ExecutorService executorService) throws IOException {
    assert options.programConsumer != null;
    if (options.quiet) {
      System.setOut(new PrintStream(ByteStreams.nullOutputStream()));
    }
    if (this.getClass().desiredAssertionStatus()) {
      options.reporter.info(
          new StringDiagnostic(
              "Running R8 version " + Version.LABEL + " with assertions enabled."));
    }
    try {
      AppView<AppInfoWithClassHierarchy> appView;
      {
        ApplicationReader applicationReader = new ApplicationReader(inputApp, options, timing);
        DirectMappedDexApplication application = applicationReader.read(executorService).toDirect();
        MainDexClasses mainDexClasses = applicationReader.readMainDexClasses(application);

        // Now that the dex-application is fully loaded, close any internal archive providers.
        inputApp.closeInternalArchiveProviders();

        appView = AppView.createForR8(application, mainDexClasses);
        appView.setAppServices(AppServices.builder(appView).build());
      }

      // Check for potentially having pass-through of Cf-code for kotlin libraries.
      options.enableCfByteCodePassThrough =
          options.isGeneratingClassFiles() && KotlinMetadataUtils.mayProcessKotlinMetadata(appView);

      // Up-front check for valid library setup.
      if (!options.mainDexKeepRules.isEmpty()) {
        MainDexListBuilder.checkForAssumedLibraryTypes(appView.appInfo());
      }
      if (!options.desugaredLibraryConfiguration.getRetargetCoreLibMember().isEmpty()) {
        DesugaredLibraryRetargeter.checkForAssumedLibraryTypes(appView);
        DesugaredLibraryRetargeter.amendLibraryWithRetargetedMembers(appView);
      }
      InterfaceMethodRewriter.checkForAssumedLibraryTypes(appView.appInfo(), options);
      BackportedMethodRewriter.registerAssumedLibraryTypes(options);
      if (options.enableEnumUnboxing) {
        EnumUnboxingCfMethods.registerSynthesizedCodeReferences(appView.dexItemFactory());
      }
      CfUtilityMethodsForCodeOptimizations.registerSynthesizedCodeReferences(
          appView.dexItemFactory());

      List<ProguardConfigurationRule> synthesizedProguardRules = new ArrayList<>();
      timing.begin("Strip unused code");
      Set<DexType> classesToRetainInnerClassAttributeFor = null;
      Set<DexType> missingClasses = null;
      RuntimeTypeCheckInfo.Builder classMergingEnqueuerExtensionBuilder =
          new RuntimeTypeCheckInfo.Builder(appView.dexItemFactory());
      try {
        // TODO(b/154849103): Find a better way to determine missing classes.
        missingClasses = new SubtypingInfo(appView).getMissingClasses();
        missingClasses = filterMissingClasses(
            missingClasses, options.getProguardConfiguration().getDontWarnPatterns());
        if (!missingClasses.isEmpty()) {
          missingClasses.forEach(
              clazz -> {
                options.reporter.warning(
                    new StringDiagnostic("Missing class: " + clazz.toSourceString()));
              });
          if (!options.ignoreMissingClasses) {
            DexType missingClass = missingClasses.iterator().next();
            if (missingClasses.size() == 1) {
              throw new CompilationError(
                  "Compilation can't be completed because the class `"
                      + missingClass.toSourceString()
                      + "` is missing.");
            } else {
              throw new CompilationError(
                  "Compilation can't be completed because `" + missingClass.toSourceString()
                      + "` and " + (missingClasses.size() - 1) + " other classes are missing.");
            }
          }
        }
        options.reporter.failIfPendingErrors();

        // Add synthesized -assumenosideeffects from min api if relevant.
        if (options.isGeneratingDex()) {
          if (!ProguardConfigurationUtils.hasExplicitAssumeValuesOrAssumeNoSideEffectsRuleForMinSdk(
              options.itemFactory, options.getProguardConfiguration().getRules())) {
            synthesizedProguardRules.add(
                ProguardConfigurationUtils.buildAssumeNoSideEffectsRuleForApiLevel(
                    options.itemFactory, AndroidApiLevel.getAndroidApiLevel(options.minApiLevel)));
          }
        }
        SubtypingInfo subtypingInfo = new SubtypingInfo(appView);
        appView.setRootSet(
            new RootSetBuilder(
                    appView,
                    subtypingInfo,
                    Iterables.concat(
                        options.getProguardConfiguration().getRules(), synthesizedProguardRules))
                .run(executorService));

        AnnotationRemover.Builder annotationRemoverBuilder =
            options.isShrinking() ? AnnotationRemover.builder() : null;
        AppView<AppInfoWithLiveness> appViewWithLiveness =
            runEnqueuer(
                annotationRemoverBuilder,
                executorService,
                appView,
                subtypingInfo,
                classMergingEnqueuerExtensionBuilder);

        assert appView.rootSet().verifyKeptFieldsAreAccessedAndLive(appViewWithLiveness.appInfo());
        assert appView.rootSet().verifyKeptMethodsAreTargetedAndLive(appViewWithLiveness.appInfo());
        assert appView.rootSet().verifyKeptTypesAreLive(appViewWithLiveness.appInfo());
        assert appView.rootSet().verifyKeptItemsAreKept(appView);

        missingClasses =
            Sets.union(missingClasses, appViewWithLiveness.appInfo().getMissingTypes());

        appView.rootSet().checkAllRulesAreUsed(options);

        if (options.proguardSeedsConsumer != null) {
          ByteArrayOutputStream bytes = new ByteArrayOutputStream();
          PrintStream out = new PrintStream(bytes);
          RootSetBuilder.writeSeeds(appView.appInfo().withLiveness(), out, type -> true);
          out.flush();
          ExceptionUtils.withConsumeResourceHandler(
              options.reporter, options.proguardSeedsConsumer, bytes.toString());
          ExceptionUtils.withFinishedResourceHandler(
              options.reporter, options.proguardSeedsConsumer);
        }
        if (options.isShrinking()) {
          // Mark dead proto extensions fields as neither being read nor written. This step must
          // run prior to the tree pruner.
          appView.withGeneratedExtensionRegistryShrinker(
              shrinker -> shrinker.run(Mode.INITIAL_TREE_SHAKING));

          TreePruner pruner = new TreePruner(appViewWithLiveness);
          DirectMappedDexApplication prunedApp = pruner.run();

          // Recompute the subtyping information.
          Set<DexType> removedClasses = pruner.getRemovedClasses();
          appView.removePrunedClasses(
              prunedApp, removedClasses, pruner.getMethodsToKeepForConfigurationDebugging());
          new AbstractMethodRemover(
                  appViewWithLiveness, appViewWithLiveness.appInfo().computeSubtypingInfo())
              .run();

          if (appView.options().protoShrinking().isProtoEnumShrinkingEnabled()) {
            appView.protoShrinker().enumProtoShrinker.clearDeadEnumLiteMaps();
          }

          AnnotationRemover annotationRemover =
              annotationRemoverBuilder
                  .computeClassesToRetainInnerClassAttributeFor(appViewWithLiveness)
                  .build(appViewWithLiveness, removedClasses);
          annotationRemover.ensureValid().run();
          classesToRetainInnerClassAttributeFor =
              annotationRemover.getClassesToRetainInnerClassAttributeFor();
          new GenericSignatureRewriter(appView, NamingLens.getIdentityLens())
              .run(appView.appInfo().classes(), executorService);
        }
      } finally {
        timing.end();
      }

      assert appView.appInfo().hasLiveness();
      assert verifyNoJarApplicationReaders(appView.appInfo().classes());
      // Build conservative main dex content after first round of tree shaking. This is used
      // by certain optimizations to avoid introducing additional class references into main dex
      // classes, as that can cause the final number of main dex methods to grow.
      RootSet mainDexRootSet = null;
      MainDexTracingResult mainDexTracingResult = MainDexTracingResult.NONE;
      if (!options.mainDexKeepRules.isEmpty()) {
        assert appView.graphLens().isIdentityLens();
        // Find classes which may have code executed before secondary dex files installation.
        SubtypingInfo subtypingInfo = new SubtypingInfo(appView);
        mainDexRootSet =
            new RootSetBuilder(appView, subtypingInfo, options.mainDexKeepRules)
                .run(executorService);
        // Live types is the tracing result.
        Set<DexProgramClass> mainDexBaseClasses =
            EnqueuerFactory.createForMainDexTracing(appView, subtypingInfo)
                .traceMainDex(mainDexRootSet, executorService, timing);
        // Calculate the automatic main dex list according to legacy multidex constraints.
        mainDexTracingResult = new MainDexListBuilder(mainDexBaseClasses, appView).run();
        appView.appInfo().unsetObsolete();
      }

      // The class type lattice elements include information about the interfaces that a class
      // implements. This information can change as a result of vertical class merging, so we need
      // to clear the cache, so that we will recompute the type lattice elements.
      appView.dexItemFactory().clearTypeElementsCache();

      if (options.getProguardConfiguration().isAccessModificationAllowed()) {
        AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
        SubtypingInfo subtypingInfo = appViewWithLiveness.appInfo().computeSubtypingInfo();
        GraphLens publicizedLens =
            ClassAndMemberPublicizer.run(
                executorService,
                timing,
                appViewWithLiveness.appInfo().app(),
                appViewWithLiveness,
                subtypingInfo);
        boolean changed = appView.setGraphLens(publicizedLens);
        if (changed) {
          // We can now remove visibility bridges. Note that we do not need to update the
          // invoke-targets here, as the existing invokes will simply dispatch to the now
          // visible super-method. MemberRebinding, if run, will then dispatch it correctly.
          new VisibilityBridgeRemover(appView.withLiveness()).run();
        }
      }

      AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
      appView.setGraphLens(new MemberRebindingAnalysis(appViewWithLiveness).run(executorService));
      appView.appInfo().withLiveness().getFieldAccessInfoCollection().restrictToProgram(appView);

      if (options.shouldDesugarNests()) {
        timing.begin("NestBasedAccessDesugaring");
        R8NestBasedAccessDesugaring analyzer = new R8NestBasedAccessDesugaring(appViewWithLiveness);
        Builder appBuilder = getDirectApp(appView).builder();
        NestedPrivateMethodLens lens = analyzer.run(executorService, appBuilder);
        if (lens != null) {
          appView.rewriteWithLensAndApplication(lens, appBuilder.build());
        }
        timing.end();
      } else {
        timing.begin("NestReduction");
        // This pass attempts to reduce the number of nests and nest size
        // to allow further passes, specifically the class mergers, to do
        // a better job. This pass is better run before the class merger
        // but after the publicizer (cannot be run as part of the Enqueuer).
        new NestReducer(appViewWithLiveness).run(executorService);
        timing.end();
      }

      boolean isKotlinLibraryCompilationWithInlinePassThrough =
          options.enableCfByteCodePassThrough && appView.hasCfByteCodePassThroughMethods();

      RuntimeTypeCheckInfo runtimeTypeCheckInfo = classMergingEnqueuerExtensionBuilder.build();
      if (!isKotlinLibraryCompilationWithInlinePassThrough
          && options.getProguardConfiguration().isOptimizing()) {
        if (options.enableStaticClassMerging) {
          timing.begin("HorizontalStaticClassMerger");
          StaticClassMerger staticClassMerger =
              new StaticClassMerger(appViewWithLiveness, options, mainDexTracingResult);
          NestedGraphLens lens = staticClassMerger.run();
          appView.rewriteWithLens(lens);
          timing.end();
        }
        if (options.enableVerticalClassMerging) {
          timing.begin("VerticalClassMerger");
          VerticalClassMerger verticalClassMerger =
              new VerticalClassMerger(
                  getDirectApp(appViewWithLiveness),
                  appViewWithLiveness,
                  executorService,
                  timing,
                  mainDexTracingResult);
          VerticalClassMergerGraphLens lens = verticalClassMerger.run();
          if (lens != null) {
            appView.setVerticallyMergedClasses(lens.getMergedClasses());
            appView.rewriteWithLens(lens);
            runtimeTypeCheckInfo = runtimeTypeCheckInfo.rewriteWithLens(lens);
          }
          timing.end();
        }

        if (options.enableArgumentRemoval) {
          SubtypingInfo subtypingInfo = appViewWithLiveness.appInfo().computeSubtypingInfo();
          {
            timing.begin("UnusedArgumentRemoval");
            UnusedArgumentsGraphLens lens =
                new UnusedArgumentsCollector(
                        appViewWithLiveness,
                        new MethodPoolCollection(appViewWithLiveness, subtypingInfo))
                    .run(executorService, timing);
            assert lens == null || getDirectApp(appView).verifyNothingToRewrite(appView, lens);
            appView.rewriteWithLens(lens);
            timing.end();
          }
          if (options.enableUninstantiatedTypeOptimization) {
            timing.begin("UninstantiatedTypeOptimization");
            UninstantiatedTypeOptimizationGraphLens lens =
                new UninstantiatedTypeOptimization(appViewWithLiveness)
                    .strenghtenOptimizationInfo()
                    .run(
                        new MethodPoolCollection(appViewWithLiveness, subtypingInfo),
                        executorService,
                        timing);
            assert lens == null || getDirectApp(appView).verifyNothingToRewrite(appView, lens);
            appView.rewriteWithLens(lens);
            timing.end();
          }
        }
        if (options.enableHorizontalClassMerging && options.enableInlining) {
          timing.begin("HorizontalClassMerger");
          HorizontalClassMerger merger = new HorizontalClassMerger(appViewWithLiveness);
          DirectMappedDexApplication.Builder appBuilder =
              appView.appInfo().app().asDirect().builder();
          HorizontalClassMergerGraphLens lens =
              merger.run(appBuilder, mainDexTracingResult, runtimeTypeCheckInfo);
          if (lens != null) {
            DirectMappedDexApplication app = appBuilder.build();
            appView.removePrunedClasses(app, appView.horizontallyMergedClasses().getSources());
            appView.rewriteWithLens(lens);

            // Only required for class merging, clear instance to save memory.
            runtimeTypeCheckInfo = null;
          }
          timing.end();
        }

      }

      // None of the optimizations above should lead to the creation of type lattice elements.
      assert appView.dexItemFactory().verifyNoCachedTypeElements();

      // Collect switch maps and ordinals maps.
      if (options.enableEnumSwitchMapRemoval) {
        appViewWithLiveness.setAppInfo(new SwitchMapCollector(appViewWithLiveness).run());
      }
      if (options.enableEnumValueOptimization || options.enableEnumUnboxing) {
        appViewWithLiveness.setAppInfo(new EnumValueInfoMapCollector(appViewWithLiveness).run());
      }

      // Collect the already pruned types before creating a new app info without liveness.
      // TODO: we should avoid removing liveness.
      Set<DexType> prunedTypes = appView.withLiveness().appInfo().getPrunedTypes();

      // TODO: move to appview.
      EnumValueInfoMapCollection enumValueInfoMapCollection =
          appViewWithLiveness.appInfo().getEnumValueInfoMapCollection();

      timing.begin("Create IR");
      CfgPrinter printer = options.printCfg ? new CfgPrinter() : null;
      try {
        IRConverter converter = new IRConverter(appView, timing, printer, mainDexTracingResult);
        DexApplication application =
            converter.optimize(appViewWithLiveness, executorService).asDirect();
        appView.setAppInfo(appView.appInfo().rebuildWithClassHierarchy(previous -> application));
      } finally {
        timing.end();
      }

      // Clear the reference type lattice element cache to reduce memory pressure.
      appView.dexItemFactory().clearTypeElementsCache();

      // At this point all code has been mapped according to the graph lens. We cannot remove the
      // graph lens entirely, though, since it is needed for mapping all field and method signatures
      // back to the original program.
      timing.begin("AppliedGraphLens construction");
      appView.setGraphLens(new AppliedGraphLens(appView));
      timing.end();

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

      if (!options.mainDexKeepRules.isEmpty()) {
        // No need to build a new main dex root set
        assert mainDexRootSet != null;
        GraphConsumer mainDexKeptGraphConsumer = options.mainDexKeptGraphConsumer;
        WhyAreYouKeepingConsumer whyAreYouKeepingConsumer = null;
        if (!mainDexRootSet.reasonAsked.isEmpty()) {
          whyAreYouKeepingConsumer = new WhyAreYouKeepingConsumer(mainDexKeptGraphConsumer);
          mainDexKeptGraphConsumer = whyAreYouKeepingConsumer;
        }

        Enqueuer enqueuer =
            EnqueuerFactory.createForMainDexTracing(
                appView, new SubtypingInfo(appView), mainDexKeptGraphConsumer);
        // Find classes which may have code executed before secondary dex files installation.
        // Live types is the tracing result.
        Set<DexProgramClass> mainDexBaseClasses =
            enqueuer.traceMainDex(mainDexRootSet, executorService, timing);
        // Calculate the automatic main dex list according to legacy multidex constraints.
        mainDexTracingResult = new MainDexListBuilder(mainDexBaseClasses, appView).run();
        final MainDexTracingResult finalMainDexClasses = mainDexTracingResult;

        processWhyAreYouKeepingAndCheckDiscarded(
            mainDexRootSet,
            () -> {
              ArrayList<DexProgramClass> classes = new ArrayList<>();
              // TODO(b/131668850): This is not a deterministic order!
              finalMainDexClasses
                  .getClasses()
                  .forEach(
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
            executorService);
      }

      if (options.shouldRerunEnqueuer()) {
        timing.begin("Post optimization code stripping");
        try {
          GraphConsumer keptGraphConsumer = null;
          WhyAreYouKeepingConsumer whyAreYouKeepingConsumer = null;
          if (options.isShrinking()) {
            keptGraphConsumer = options.keptGraphConsumer;
            if (!appView.rootSet().reasonAsked.isEmpty()) {
              whyAreYouKeepingConsumer = new WhyAreYouKeepingConsumer(keptGraphConsumer);
              keptGraphConsumer = whyAreYouKeepingConsumer;
            }
          }

          Enqueuer enqueuer =
              EnqueuerFactory.createForFinalTreeShaking(
                  appView,
                  new SubtypingInfo(appView),
                  keptGraphConsumer,
                  missingClasses,
                  prunedTypes);
          appView.setAppInfo(
              enqueuer
                  .traceApplication(
                      appView.rootSet(),
                      options.getProguardConfiguration().getDontWarnPatterns(),
                      executorService,
                      timing)
                  .withEnumValueInfoMaps(enumValueInfoMapCollection));
          // Rerunning the enqueuer should not give rise to any method rewritings.
          assert enqueuer.buildGraphLens() == null;
          appView.withGeneratedMessageLiteBuilderShrinker(
              shrinker ->
                  shrinker.rewriteDeadBuilderReferencesFromDynamicMethods(
                      appViewWithLiveness, executorService, timing));

          if (options.isShrinking()) {
            // Mark dead proto extensions fields as neither being read nor written. This step must
            // run prior to the tree pruner.
            TreePrunerConfiguration treePrunerConfiguration =
                appView.withGeneratedExtensionRegistryShrinker(
                    shrinker -> shrinker.run(enqueuer.getMode()),
                    DefaultTreePrunerConfiguration.getInstance());

            TreePruner pruner = new TreePruner(appViewWithLiveness, treePrunerConfiguration);
            DirectMappedDexApplication application = pruner.run();
            Set<DexType> removedClasses = pruner.getRemovedClasses();

            if (options.usageInformationConsumer != null) {
              ExceptionUtils.withFinishedResourceHandler(
                  options.reporter, options.usageInformationConsumer);
            }

            appView.removePrunedClasses(
                application,
                CollectionUtils.mergeSets(prunedTypes, removedClasses),
                pruner.getMethodsToKeepForConfigurationDebugging());

            new BridgeHoisting(appViewWithLiveness).run();

            // TODO(b/130721661): Enable this assert.
            // assert Inliner.verifyNoMethodsInlinedDueToSingleCallSite(appView);

            assert appView.allMergedClasses().verifyAllSourcesPruned(appViewWithLiveness);
            assert appView.validateUnboxedEnumsHaveBeenPruned();

            processWhyAreYouKeepingAndCheckDiscarded(
                appView.rootSet(),
                () -> appView.appInfo().app().classesWithDeterministicOrder(),
                whyAreYouKeepingConsumer,
                appView,
                enqueuer,
                false,
                options,
                timing,
                executorService);

            // Remove annotations that refer to types that no longer exist.
            assert classesToRetainInnerClassAttributeFor != null;
            AnnotationRemover.builder()
                .setClassesToRetainInnerClassAttributeFor(classesToRetainInnerClassAttributeFor)
                .build(appView.withLiveness(), removedClasses)
                .run();
            if (!mainDexTracingResult.isEmpty()) {
              // Remove types that no longer exists from the computed main dex list.
              mainDexTracingResult =
                  mainDexTracingResult.prunedCopy(appView.appInfo().withLiveness());
            }

            // Synthesize fields for triggering class initializers.
            new ClassInitFieldSynthesizer(appViewWithLiveness).run(executorService);
          }
        } finally {
          timing.end();
        }

        if (appView.options().protoShrinking().isProtoShrinkingEnabled()) {
          if (appView.options().protoShrinking().isProtoEnumShrinkingEnabled()) {
            appView.protoShrinker().enumProtoShrinker.verifyDeadEnumLiteMapsAreDead();
          }

          IRConverter converter = new IRConverter(appView, timing, null, mainDexTracingResult);

          // If proto shrinking is enabled, we need to reprocess every dynamicMethod(). This ensures
          // that proto fields that have been removed by the second round of tree shaking are also
          // removed from the proto schemas in the bytecode.
          appView.withGeneratedMessageLiteShrinker(
              shrinker -> shrinker.postOptimizeDynamicMethods(converter, executorService, timing));

          // If proto shrinking is enabled, we need to post-process every
          // findLiteExtensionByNumber() method. This ensures that there are no references to dead
          // extensions that have been removed by the second round of tree shaking.
          appView.withGeneratedExtensionRegistryShrinker(
              shrinker ->
                  shrinker.postOptimizeGeneratedExtensionRegistry(
                      converter, executorService, timing));
        }
      }

      // Remove unneeded visibility bridges that have been inserted for member rebinding.
      // This can only be done if we have AppInfoWithLiveness.
      if (appView.appInfo().hasLiveness()) {
        new VisibilityBridgeRemover(appView.withLiveness()).run();
      } else {
        // If we don't have AppInfoWithLiveness here, it must be because we are not shrinking. When
        // we are not shrinking, we can't move visibility bridges. In principle, though, it would be
        // possible to remove visibility bridges that have been synthesized by R8, but we currently
        // do not have this information.
        assert !options.isShrinking();
      }

      MemberRebindingIdentityLens memberRebindingLens =
          MemberRebindingIdentityLensFactory.create(appView, executorService);
      appView.setGraphLens(memberRebindingLens);

      // Perform repackaging.
      if (options.isRepackagingEnabled()) {
        DirectMappedDexApplication.Builder appBuilder =
            appView.appInfo().app().asDirect().builder();
        RepackagingLens lens =
            new Repackaging(appView.withLiveness()).run(appBuilder, executorService, timing);
        if (lens != null) {
          // Specify to use the member rebinding lens as the parent lens during the rewriting. This
          // is needed to ensure that the rebound references are available during lens lookups.
          // TODO(b/168282032): This call-site should not have to think about the parent lens that
          //  is used for the rewriting. Once the new member rebinding lens replaces the old member
          //  rebinding analysis it should be possible to clean this up.
          appView.rewriteWithLensAndApplication(
              lens, appBuilder.build(), memberRebindingLens.getPrevious());
        }
      }

      // Add automatic main dex classes to an eventual manual list of classes.
      if (!options.mainDexKeepRules.isEmpty()) {
        appView.appInfo().getMainDexClasses().addAll(mainDexTracingResult);
      }

      SyntheticFinalization.Result result =
          appView.getSyntheticItems().computeFinalSynthetics(appView);
      if (result != null) {
        if (appView.appInfo().hasLiveness()) {
          appViewWithLiveness.setAppInfo(
              appViewWithLiveness
                  .appInfo()
                  .rebuildWithLiveness(result.commit, result.removedSyntheticClasses));
        } else {
          appView.setAppInfo(appView.appInfo().rebuildWithClassHierarchy(result.commit));
        }
      }

      // Perform minification.
      NamingLens namingLens;
      if (options.getProguardConfiguration().hasApplyMappingFile()) {
        SeedMapper seedMapper =
            SeedMapper.seedMapperFromFile(
                options.reporter, options.getProguardConfiguration().getApplyMappingFile());
        timing.begin("apply-mapping");
        namingLens =
            new ProguardMapMinifier(appView.withLiveness(), seedMapper)
                .run(executorService, timing);
        timing.end();
      } else if (options.isMinifying()) {
        timing.begin("Minification");
        namingLens = new Minifier(appView.withLiveness()).run(executorService, timing);
        timing.end();
      } else {
        namingLens = NamingLens.getIdentityLens();
      }

      assert verifyMovedMethodsHaveOriginalMethodPosition(appView, getDirectApp(appView));

      timing.begin("Line number remapping");
      // When line number optimization is turned off the identity mapping for line numbers is
      // used. We still run the line number optimizer to collect line numbers and inline frame
      // information for the mapping file.
      ClassNameMapper classNameMapper =
          LineNumberOptimizer.run(appView, getDirectApp(appView), inputApp, namingLens);
      timing.end();

      // Overwrite SourceFile if specified. This step should be done after IR conversion.
      timing.begin("Rename SourceFile");
      new SourceFileRewriter(appView, appView.appInfo().app()).run();
      timing.end();

      // If a method filter is present don't produce output since the application is likely partial.
      if (options.hasMethodsFilter()) {
        System.out.println("Finished compilation with method filter: ");
        options.methodsFilter.forEach(m -> System.out.println("  - " + m));
        return;
      }

      // Validity checks.
      assert getDirectApp(appView).verifyCodeObjectsOwners();
      assert appView.appInfo().classes().stream().allMatch(clazz -> clazz.isValid(options));
      if (options.isShrinking()
          || options.isMinifying()
          || options.getProguardConfiguration().hasApplyMappingFile()) {
        assert appView.rootSet().verifyKeptItemsAreKept(appView);
      }

      assert options.testing.disableMappingToOriginalProgramVerification
          || appView
              .graphLens()
              .verifyMappingToOriginalProgram(
                  appView,
                  new ApplicationReader(inputApp.withoutMainDexList(), options, timing)
                      .readWithoutDumping(executorService));

      // Report synthetic rules (only for testing).
      // TODO(b/120959039): Move this to being reported through the graph consumer.
      if (options.syntheticProguardRulesConsumer != null) {
        options.syntheticProguardRulesConsumer.accept(synthesizedProguardRules);
      }

      NamingLens prefixRewritingNamingLens =
          PrefixRewritingNamingLens.createPrefixRewritingNamingLens(appView, namingLens);

      timing.begin("MinifyKotlinMetadata");
      new KotlinMetadataRewriter(appView, prefixRewritingNamingLens).runForR8(executorService);
      timing.end();

      new GenericSignatureRewriter(appView, prefixRewritingNamingLens)
          .run(appView.appInfo().classes(), executorService);

      // Generate the resulting application resources.
      // TODO(b/165783399): Apply the graph lens to all instructions in the CF and DEX backends.
      writeApplication(
          executorService,
          appView,
          appView.graphLens(),
          appView.initClassLens(),
          prefixRewritingNamingLens,
          options,
          ProguardMapSupplier.create(classNameMapper, options));

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

  private static boolean verifyMovedMethodsHaveOriginalMethodPosition(
      AppView<?> appView, DirectMappedDexApplication application) {
    application
        .classes()
        .forEach(
            clazz -> {
              clazz.forEachProgramMethod(
                  method -> {
                    DexMethod originalMethod =
                        appView.graphLens().getOriginalMethodSignature(method.getReference());
                    if (originalMethod != method.getReference()) {
                      DexMethod originalMethod2 =
                          appView.graphLens().getOriginalMethodSignature(method.getReference());
                      appView.graphLens().getOriginalMethodSignature(method.getReference());
                      DexEncodedMethod definition = method.getDefinition();
                      Code code = definition.getCode();
                      if (code == null) {
                        return;
                      }
                      if (code.isCfCode()) {
                        assert verifyOriginalMethodInPosition(code.asCfCode(), originalMethod);
                      } else {
                        assert code.isDexCode();
                        assert verifyOriginalMethodInDebugInfo(code.asDexCode(), originalMethod);
                      }
                    }
                  });
            });
    return true;
  }

  private static boolean verifyOriginalMethodInPosition(CfCode code, DexMethod originalMethod) {
    for (CfInstruction instruction : code.getInstructions()) {
      if (!instruction.isPosition()) {
        continue;
      }
      CfPosition position = instruction.asPosition();
      assert position.getPosition().getOutermostCaller().method == originalMethod;
    }
    return true;
  }

  private static boolean verifyOriginalMethodInDebugInfo(DexCode code, DexMethod originalMethod) {
    if (code.getDebugInfo() == null) {
      return true;
    }
    for (DexDebugEvent event : code.getDebugInfo().events) {
      assert !event.isSetInlineFrame() || event.asSetInlineFrame().hasOuterPosition(originalMethod);
    }
    return true;
  }

  private AppView<AppInfoWithLiveness> runEnqueuer(
      AnnotationRemover.Builder annotationRemoverBuilder,
      ExecutorService executorService,
      AppView<AppInfoWithClassHierarchy> appView,
      SubtypingInfo subtypingInfo,
      RuntimeTypeCheckInfo.Builder classMergingEnqueuerExtensionBuilder)
      throws ExecutionException {
    Enqueuer enqueuer = EnqueuerFactory.createForInitialTreeShaking(appView, subtypingInfo);
    enqueuer.setAnnotationRemoverBuilder(annotationRemoverBuilder);
    if (appView.options().enableInitializedClassesInInstanceMethodsAnalysis) {
      enqueuer.registerAnalysis(new InitializedClassesInInstanceMethodsAnalysis(appView));
    }
    if (AssertionsRewriter.isEnabled(appView.options())) {
      enqueuer.registerAnalysis(
          new ClassInitializerAssertionEnablingAnalysis(
              appView.dexItemFactory(), OptimizationFeedbackSimple.getInstance()));
    }

    if (options.isClassMergingExtensionRequired()) {
      classMergingEnqueuerExtensionBuilder.attach(enqueuer);
    }

    AppView<AppInfoWithLiveness> appViewWithLiveness =
        appView.setAppInfo(
            enqueuer.traceApplication(
                appView.rootSet(),
                options.getProguardConfiguration().getDontWarnPatterns(),
                executorService,
                timing));
    NestedGraphLens lens = enqueuer.buildGraphLens();
    appView.rewriteWithLens(lens);
    if (InternalOptions.assertionsEnabled()) {
      // Register the dead proto types. These are needed to verify that no new missing types are
      // reported and that no dead proto types are referenced in the generated application.
      appViewWithLiveness.withProtoShrinker(
          shrinker ->
              shrinker.setDeadProtoTypes(appViewWithLiveness.appInfo().getDeadProtoTypes()));
    }
    appView.withGeneratedMessageLiteBuilderShrinker(
        shrinker ->
            shrinker.rewriteDeadBuilderReferencesFromDynamicMethods(
                appViewWithLiveness, executorService, timing));
    return appViewWithLiveness;
  }

  static void processWhyAreYouKeepingAndCheckDiscarded(
      RootSet rootSet,
      Supplier<Iterable<DexProgramClass>> classes,
      WhyAreYouKeepingConsumer whyAreYouKeepingConsumer,
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Enqueuer enqueuer,
      boolean forMainDex,
      InternalOptions options,
      Timing timing,
      ExecutorService executorService)
      throws ExecutionException {
    if (whyAreYouKeepingConsumer != null) {
      for (DexReference reference : rootSet.reasonAsked) {
        whyAreYouKeepingConsumer.printWhyAreYouKeeping(
            enqueuer.getGraphReporter().getGraphNode(reference), System.out);
      }
    }
    if (rootSet.checkDiscarded.isEmpty()
        || appView.options().testing.dontReportFailingCheckDiscarded) {
      return;
    }
    List<DexDefinition> failed = new DiscardedChecker(rootSet, classes.get()).run();
    if (failed.isEmpty()) {
      return;
    }
    // If there is no kept-graph info, re-run the enqueueing to compute it.
    if (whyAreYouKeepingConsumer == null) {
      whyAreYouKeepingConsumer = new WhyAreYouKeepingConsumer(null);
      SubtypingInfo subtypingInfo = new SubtypingInfo(appView);
      if (forMainDex) {
        enqueuer =
            EnqueuerFactory.createForMainDexTracing(
                appView, subtypingInfo, whyAreYouKeepingConsumer);
        enqueuer.traceMainDex(rootSet, executorService, timing);
      } else {
        enqueuer =
            EnqueuerFactory.createForWhyAreYouKeeping(
                appView, subtypingInfo, whyAreYouKeepingConsumer);
        enqueuer.traceApplication(
            rootSet,
            options.getProguardConfiguration().getDontWarnPatterns(),
            executorService,
            timing);
      }
    }
    options.reporter.error(
        new CheckDiscardDiagnostic.Builder()
            .addFailedItems(failed, enqueuer.getGraphReporter(), whyAreYouKeepingConsumer)
            .build());
    options.reporter.failIfPendingErrors();
  }

  private static boolean verifyNoJarApplicationReaders(Collection<DexProgramClass> classes) {
    for (DexProgramClass clazz : classes) {
      for (DexEncodedMethod method : clazz.methods()) {
        if (method.getCode() != null) {
          assert method.getCode().verifyNoInputReaders();
        }
      }
    }
    return true;
  }

  private static void run(String[] args) throws CompilationFailedException {
    R8Command command = R8Command.parse(args, CommandLineOrigin.INSTANCE).build();
    if (command.isPrintHelp()) {
      SelfRetraceTest.test();
      System.out.println(USAGE_MESSAGE);
      return;
    }
    if (command.isPrintVersion()) {
      System.out.println("R8 " + Version.getVersionString());
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
