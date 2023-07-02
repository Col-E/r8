// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.fieldaccess.TrivialFieldAccessReprocessor;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.optimize.argumentpropagation.ArgumentPropagator;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class PrimaryR8IRConverter extends IRConverter {

  private final Timing timing;

  public PrimaryR8IRConverter(AppView<? extends AppInfoWithClassHierarchy> appView, Timing timing) {
    super(appView);
    this.timing = timing;
  }

  public void optimize(AppView<AppInfoWithLiveness> appView, ExecutorService executorService)
      throws ExecutionException, IOException {
    timing.begin("Create IR");
    try {
      DexApplication application =
          internalOptimize(appView.withLiveness(), executorService).asDirect();
      AppInfoWithLiveness oldInfo = appView.appInfo();
      AppInfoWithClassHierarchy newAppInfo =
          oldInfo.rebuildWithClassHierarchy(previous -> application);
      appView.withClassHierarchy().setAppInfo(newAppInfo);
      appView.appInfo().setFilter(oldInfo.getFilter());
    } finally {
      timing.end();
    }
  }

  private DexApplication internalOptimize(
      AppView<AppInfoWithLiveness> appView, ExecutorService executorService)
      throws ExecutionException {
    // Desugaring happens in the enqueuer.
    assert instructionDesugaring.isEmpty();

    workaroundAbstractMethodOnNonAbstractClassVerificationBug(executorService);

    // The process is in two phases in general.
    // 1) Subject all DexEncodedMethods to optimization, except some optimizations that require
    //    reprocessing IR code of methods, e.g., outlining, double-inlining, class staticizer, etc.
    //    - a side effect is candidates for those optimizations are identified.
    // 2) Revisit DexEncodedMethods for the collected candidates.

    printPhase("Primary optimization pass");

    GraphLens graphLensForPrimaryOptimizationPass = appView.graphLens();

    // Setup optimizations for the primary optimization pass.
    appView.withArgumentPropagator(
        argumentPropagator -> argumentPropagator.initializeCodeScanner(executorService, timing));
    enumUnboxer.prepareForPrimaryOptimizationPass(graphLensForPrimaryOptimizationPass);
    outliner.prepareForPrimaryOptimizationPass(graphLensForPrimaryOptimizationPass);

    if (fieldAccessAnalysis != null) {
      fieldAccessAnalysis.fieldAssignmentTracker().initialize();
    }

    // Process the application identifying outlining candidates.
    OptimizationFeedbackDelayed feedback = delayedOptimizationFeedback;
    PostMethodProcessor.Builder postMethodProcessorBuilder =
        new PostMethodProcessor.Builder(graphLensForPrimaryOptimizationPass);
    {
      timing.begin("Build primary method processor");
      MethodProcessorEventConsumer eventConsumer =
          MethodProcessorEventConsumer.createForR8(appView);
      PrimaryMethodProcessor primaryMethodProcessor =
          PrimaryMethodProcessor.create(
              appView.withLiveness(), eventConsumer, executorService, timing);
      timing.end();
      timing.begin("IR conversion phase 1");
      assert appView.graphLens() == graphLensForPrimaryOptimizationPass;
      primaryMethodProcessor.forEachMethod(
          (method, methodProcessingContext) ->
              processDesugaredMethod(
                  method,
                  feedback,
                  primaryMethodProcessor,
                  methodProcessingContext,
                  MethodConversionOptions.forLirPhase(appView)),
          this::waveStart,
          this::waveDone,
          timing,
          executorService);
      lastWaveDone(postMethodProcessorBuilder, executorService);
      eventConsumer.finished(appView);
      assert appView.graphLens() == graphLensForPrimaryOptimizationPass;
      timing.end();
    }

    // The field access info collection is not maintained during IR processing.
    appView.appInfo().withLiveness().getFieldAccessInfoCollection().destroyAccessContexts();

    // Assure that no more optimization feedback left after primary processing.
    assert feedback.noUpdatesLeft();
    appView.setAllCodeProcessed();

    // All the code has been processed so the rewriting required by the lenses is done everywhere,
    // we clear lens code rewriting so that the lens rewriter can be re-executed in phase 2 if new
    // lenses with code rewriting are added.
    appView.clearCodeRewritings();

    // Commit synthetics from the primary optimization pass.
    commitPendingSyntheticItems(appView);

    // Post processing:
    //   1) Second pass for methods whose collected call site information become more precise.
    //   2) Second inlining pass for dealing with double inline callers.
    printPhase("Post optimization pass");

    // Analyze the data collected by the argument propagator, use the analysis result to update
    // the parameter optimization infos, and rewrite the application.
    // TODO(b/199237357): Automatically rewrite state when lens changes.
    enumUnboxer.rewriteWithLens();
    outliner.rewriteWithLens();
    appView.withArgumentPropagator(
        argumentPropagator ->
            argumentPropagator.tearDownCodeScanner(
                this, postMethodProcessorBuilder, executorService, timing));

    if (libraryMethodOverrideAnalysis != null) {
      libraryMethodOverrideAnalysis.finish();
    }

    if (!options.debug) {
      new TrivialFieldAccessReprocessor(appView.withLiveness(), postMethodProcessorBuilder)
          .run(executorService, feedback, timing);
    }

    outliner.rewriteWithLens();
    enumUnboxer.unboxEnums(
        appView, this, postMethodProcessorBuilder, executorService, feedback, timing);
    appView.unboxedEnums().checkEnumsUnboxed(appView);

    GraphLens graphLensForSecondaryOptimizationPass = appView.graphLens();

    outliner.rewriteWithLens();

    {
      timing.begin("IR conversion phase 2");
      MethodProcessorEventConsumer eventConsumer =
          MethodProcessorEventConsumer.createForR8(appView);
      PostMethodProcessor postMethodProcessor =
          timing.time(
              "Build post method processor",
              () ->
                  postMethodProcessorBuilder.build(
                      appView, eventConsumer, executorService, timing));
      if (postMethodProcessor != null) {
        assert appView.graphLens() == graphLensForSecondaryOptimizationPass;
        timing.begin("Process code");
        postMethodProcessor.forEachMethod(
            (method, methodProcessingContext) ->
                processDesugaredMethod(
                    method,
                    feedback,
                    postMethodProcessor,
                    methodProcessingContext,
                    MethodConversionOptions.forLirPhase(appView)),
            feedback,
            executorService,
            timing);
        timing.end();
        timing.time("Update visible optimization info", feedback::updateVisibleOptimizationInfo);
        eventConsumer.finished(appView);
        assert appView.graphLens() == graphLensForSecondaryOptimizationPass;
      }
      timing.end();
    }

    enumUnboxer.unsetRewriter();

    // All the code that should be impacted by the lenses inserted between phase 1 and phase 2
    // have now been processed and rewritten, we clear code lens rewriting so that the class
    // staticizer and phase 3 does not perform again the rewriting.
    appView.clearCodeRewritings();

    // Commit synthetics before creating a builder (otherwise the builder will not include the
    // synthetics.)
    commitPendingSyntheticItems(appView);

    // Update optimization info for all synthesized methods at once.
    feedback.updateVisibleOptimizationInfo();

    // TODO(b/127694949): Adapt to PostOptimization.
    outliner.performOutlining(this, feedback, executorService, timing);
    clearDexMethodCompilationState();

    if (identifierNameStringMarker != null) {
      identifierNameStringMarker.decoupleIdentifierNameStringsInFields(executorService);
    }

    // Assure that no more optimization feedback left after post processing.
    assert feedback.noUpdatesLeft();
    return appView.appInfo().app();
  }

  public static void finalizeLirToOutputFormat(
      AppView<?> appView, Timing timing, ExecutorService executorService)
      throws ExecutionException {
    appView.testing().exitLirSupportedPhase();
    if (!appView.testing().canUseLir(appView)) {
      return;
    }
    DeadCodeRemover deadCodeRemover = new DeadCodeRemover(appView);
    String output = appView.options().isGeneratingClassFiles() ? "CF" : "DEX";
    timing.begin("LIR->IR->" + output);
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz ->
            clazz.forEachProgramMethod(
                m -> finalizeLirMethodToOutputFormat(m, deadCodeRemover, appView)),
        executorService);
    appView
        .getSyntheticItems()
        .getPendingSyntheticClasses()
        .forEach(
            clazz ->
                clazz.forEachProgramMethod(
                    m -> finalizeLirMethodToOutputFormat(m, deadCodeRemover, appView)));
    timing.end();
  }

  private static void finalizeLirMethodToOutputFormat(
      ProgramMethod method, DeadCodeRemover deadCodeRemover, AppView<?> appView) {
    Code code = method.getDefinition().getCode();
    if (!(code instanceof LirCode)) {
      return;
    }
    Timing onThreadTiming = Timing.empty();
    IRCode irCode = method.buildIR(appView, MethodConversionOptions.forPostLirPhase(appView));
    // Processing is done and no further uses of the meta-data should arise.
    BytecodeMetadataProvider noMetadata = BytecodeMetadataProvider.empty();
    // During processing optimization info may cause previously live code to become dead.
    // E.g., we may now have knowledge that an invoke does not have side effects.
    // Thus, we re-run the dead-code remover now as it is assumed complete by CF/DEX finalization.
    deadCodeRemover.run(irCode, onThreadTiming);
    MethodConversionOptions conversionOptions = irCode.getConversionOptions();
    assert !conversionOptions.isGeneratingLir();
    IRFinalizer<?> finalizer = conversionOptions.getFinalizer(deadCodeRemover, appView);
    method.setCode(finalizer.finalizeCode(irCode, noMetadata, onThreadTiming), appView);
  }

  private void clearDexMethodCompilationState() {
    appView.appInfo().classes().forEach(this::clearDexMethodCompilationState);
  }

  private void clearDexMethodCompilationState(DexProgramClass clazz) {
    clazz.forEachMethod(DexEncodedMethod::markNotProcessed);
  }

  private static void commitPendingSyntheticItems(AppView<AppInfoWithLiveness> appView) {
    if (appView.getSyntheticItems().hasPendingSyntheticClasses()) {
      appView.setAppInfo(
          appView
              .appInfo()
              .rebuildWithLiveness(appView.getSyntheticItems().commit(appView.appInfo().app())));
    }
  }

  private void waveStart(ProgramMethodSet wave) {
    onWaveDoneActions = Collections.synchronizedList(new ArrayList<>());
  }

  public void waveDone(ProgramMethodSet wave, ExecutorService executorService)
      throws ExecutionException {
    delayedOptimizationFeedback.refineAppInfoWithLiveness(appView.appInfo().withLiveness());
    delayedOptimizationFeedback.updateVisibleOptimizationInfo();
    fieldAccessAnalysis.fieldAssignmentTracker().waveDone(wave, delayedOptimizationFeedback);
    appView.withArgumentPropagator(ArgumentPropagator::publishDelayedReprocessingCriteria);
    if (appView.options().protoShrinking().enableRemoveProtoEnumSwitchMap()) {
      appView.protoShrinker().protoEnumSwitchMapRemover.updateVisibleStaticFieldValues();
    }
    enumUnboxer.updateEnumUnboxingCandidatesInfo();
    assert delayedOptimizationFeedback.noUpdatesLeft();
    if (onWaveDoneActions != null) {
      onWaveDoneActions.forEach(com.android.tools.r8.utils.Action::execute);
      onWaveDoneActions = null;
    }
    if (!prunedMethodsInWave.isEmpty()) {
      appView.pruneItems(
          PrunedItems.builder()
              .setRemovedMethods(prunedMethodsInWave)
              .setPrunedApp(appView.appInfo().app())
              .build(),
          executorService,
          timing);
      prunedMethodsInWave.clear();
    }
  }

  private void lastWaveDone(
      PostMethodProcessor.Builder postMethodProcessorBuilder, ExecutorService executorService)
      throws ExecutionException {
    if (assertionErrorTwoArgsConstructorRewriter != null) {
      assertionErrorTwoArgsConstructorRewriter.onLastWaveDone(postMethodProcessorBuilder);
      assertionErrorTwoArgsConstructorRewriter = null;
    }
    if (inliner != null) {
      inliner.onLastWaveDone(postMethodProcessorBuilder, executorService, timing);
    }
    if (instanceInitializerOutliner != null) {
      instanceInitializerOutliner.onLastWaveDone(postMethodProcessorBuilder);
      instanceInitializerOutliner = null;
    }
    if (serviceLoaderRewriter != null) {
      serviceLoaderRewriter.onLastWaveDone(postMethodProcessorBuilder);
      serviceLoaderRewriter = null;
    }

    // Ensure determinism of method-to-reprocess set.
    appView.testing().checkDeterminism(postMethodProcessorBuilder::dump);
  }
}
