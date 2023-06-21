// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.ir.optimize.info.OptimizationFeedback.getSimpleFeedback;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;
import com.android.tools.r8.ir.optimize.Inliner.InlineResult;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.ir.optimize.inliner.FixedInliningReasonStrategy;
import com.android.tools.r8.ir.optimize.inliner.InliningIRProvider;
import com.android.tools.r8.ir.optimize.inliner.NopWhyAreYouNotInliningReporter;
import com.android.tools.r8.ir.optimize.inliner.multicallerinliner.MultiCallerInlinerCallGraph;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.LazyBox;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.LongLivedProgramMethodSetBuilder;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import com.android.tools.r8.utils.collections.ProgramMethodMultiset;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

// TODO(b/142300882): If a method is selected for multi caller inlining, then if it is reprocessed
//  and we inline into it, we should potentially disable multi caller inlining for that method (or
//  we should disallow inlining into it).
public class MultiCallerInliner {

  private final AppView<AppInfoWithLiveness> appView;

  // Maps each method to the set of inlineable call sites targeting the method, or Optional.empty()
  // if we have stopped tracking the inlineable call sites.
  private final ProgramMethodMap<Optional<ProgramMethodMultiset>> multiInlineCallEdges =
      ProgramMethodMap.createConcurrent();

  private final int[] multiCallerInliningInstructionLimits;

  MultiCallerInliner(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.multiCallerInliningInstructionLimits =
        appView.options().inlinerOptions().multiCallerInliningInstructionLimits;
  }

  void recordCallEdgesForMultiCallerInlining(
      ProgramMethod method, IRCode code, MethodProcessor methodProcessor, Timing timing) {
    if (!methodProcessor.isPrimaryMethodProcessor()) {
      return;
    }

    timing.time(
        "Multi caller inliner: Record call edges",
        () -> recordCallEdgesForMultiCallerInlining(method, code, methodProcessor));
  }

  private void recordCallEdgesForMultiCallerInlining(
      ProgramMethod method, IRCode code, MethodProcessor methodProcessor) {
    LazyBox<DefaultInliningOracle> lazyOracle =
        new LazyBox<>(
            () -> {
              int inliningInstructionAllowance = Integer.MAX_VALUE;
              return new DefaultInliningOracle(
                  appView,
                  new FixedInliningReasonStrategy(Reason.MULTI_CALLER_CANDIDATE),
                  method,
                  methodProcessor,
                  inliningInstructionAllowance);
            });
    for (InvokeMethod invoke : code.<InvokeMethod>instructions(Instruction::isInvokeMethod)) {
      // Don't attempt to multi caller inline constructors. To determine if a constructor is
      // eligible for inlining, the inliner builds IR for the constructor, which we want to avoid
      // here for build speed.
      if (invoke.isInvokeConstructor(appView.dexItemFactory())) {
        continue;
      }

      SingleResolutionResult<?> resolutionResult =
          appView
              .appInfo()
              .resolveMethodLegacy(invoke.getInvokedMethod(), invoke.getInterfaceBit())
              .asSingleResolution();
      if (resolutionResult == null
          || resolutionResult.isAccessibleFrom(method, appView).isPossiblyFalse()) {
        continue;
      }

      ProgramMethod singleTarget = invoke.lookupSingleProgramTarget(appView, method);
      if (singleTarget == null
          || !methodProcessor.getCallSiteInformation().isMultiCallerInlineCandidate(singleTarget)) {
        continue;
      }

      InlineResult inlineResult =
          lazyOracle
              .computeIfAbsent()
              .computeInlining(
                  code,
                  invoke,
                  resolutionResult,
                  singleTarget,
                  method,
                  ClassInitializationAnalysis.trivial(),
                  InliningIRProvider.getThrowingInstance(),
                  NopWhyAreYouNotInliningReporter.getInstance());
      if (inlineResult == null || inlineResult.isRetryAction()) {
        stopTrackingCallSitesForMethod(singleTarget);
        continue;
      }

      InlineAction action = inlineResult.asInlineAction();
      assert action.reason == Reason.MULTI_CALLER_CANDIDATE;
      recordCallEdgeForMultiCallerInlining(method, singleTarget, methodProcessor);
    }
  }

  void recordCallEdgeForMultiCallerInlining(
      ProgramMethod method, ProgramMethod singleTarget, MethodProcessor methodProcessor) {
    Optional<ProgramMethodMultiset> value =
        multiInlineCallEdges.computeIfAbsent(
            singleTarget, ignoreKey(() -> Optional.of(ProgramMethodMultiset.createConcurrent())));

    // If we are not tracking the callers for the single target, then just return. In this case, we
    // have previously found that the single target is ineligible for multi caller inlining.
    if (!value.isPresent()) {
      return;
    }

    // Record that we have seen a call site that dispatched to the single target which is eligible
    // for inlining.
    ProgramMethodMultiset callers = value.get();
    callers.add(method);

    // We track up to n call sites, where n is the size of multiCallerInliningInstructionLimits.
    if (callers.size() > multiCallerInliningInstructionLimits.length) {
      stopTrackingCallSitesForMethodIfDefinitelyIneligibleForMultiCallerInlining(
          singleTarget, methodProcessor, callers);
    }
  }

  private void stopTrackingCallSitesForMethodIfDefinitelyIneligibleForMultiCallerInlining(
      ProgramMethod singleTarget,
      MethodProcessor methodProcessor,
      ProgramMethodMultiset callers) {
    // First remove the call sites that no longer exist due to single caller inlining.
    callers.removeIf(caller -> caller.getOptimizationInfo().hasBeenInlinedIntoSingleCallSite());

    // Then compute the minimum number of call sites that are guaranteed to be present at the end
    // of the primary optimization pass.
    IntBox minimumCallers = new IntBox();
    callers.forEachEntry(
        (caller, calls) -> {
          // If these call sites are inside a method that has a single caller, then the call sites
          // could potentially disappear as a result of single caller inlining, so don't include
          // them.
          if (!methodProcessor.getCallSiteInformation().hasSingleCallSite(caller)) {
            minimumCallers.increment(calls);
          }
        });

    // If the threshold is definitely exceeded, then mark as ineligible for multi caller inlining.
    if (minimumCallers.get() > multiCallerInliningInstructionLimits.length) {
      stopTrackingCallSitesForMethod(singleTarget);
    }
  }

  private void stopTrackingCallSitesForMethod(ProgramMethod method) {
    multiInlineCallEdges.put(method, Optional.empty());
  }

  void onMethodPruned(ProgramMethod method) {
    assert !multiInlineCallEdges.containsKey(method);
  }

  public void onLastWaveDone(
      PostMethodProcessor.Builder postMethodProcessorBuilder,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    timing.begin("Multi caller inliner");
    MultiCallerInlinerCallGraph callGraph =
        timing.time(
            "Call graph construction",
            () -> MultiCallerInlinerCallGraph.builder(appView).build(executorService));
    LongLivedProgramMethodSetBuilder<ProgramMethodSet> multiInlineCallers =
        timing.time("Needs inlining analysis", () -> computeMultiInlineCallerMethods(callGraph));
    postMethodProcessorBuilder
        .rewrittenWithLens(appView)
        .merge(multiInlineCallers);
    timing.end();
  }

  private LongLivedProgramMethodSetBuilder<ProgramMethodSet> computeMultiInlineCallerMethods(
      MultiCallerInlinerCallGraph callGraph) {
    // The multi inline callers are always rewritten up until the graph lens of the primary
    // optimization pass, so we can safely merge them into the methods to reprocess (which may be
    // rewritten with a newer graph lens).
    GraphLens currentGraphLens = appView.graphLens();
    LongLivedProgramMethodSetBuilder<ProgramMethodSet> multiInlineCallers =
        LongLivedProgramMethodSetBuilder.createForIdentitySet(currentGraphLens);
    multiInlineCallEdges.forEach(
        (singleTarget, value) -> {
          if (singleTarget.getDefinition().isLibraryMethodOverride().isPossiblyTrue()) {
            return;
          }

          if (!value.isPresent()) {
            return;
          }

          if (singleTarget.getDefinition().isInstance()
              && !appView.appInfo().isInstantiatedDirectlyOrIndirectly(singleTarget.getHolder())) {
            return;
          }

          ProgramMethodMultiset callers = value.get();
          callers.removeIf(
              method -> method.getOptimizationInfo().hasBeenInlinedIntoSingleCallSite());
          if (callers.size() == 0 || callers.size() > multiCallerInliningInstructionLimits.length) {
            return;
          }

          int numberOfCallSites = callGraph.getNode(singleTarget).getNumberOfCallSites();
          // TODO(b/142300882): The number of call sites according to the call graph should
          //  generally be >= the number of calls that the multi caller inliner has seen. This may
          //  not hold when calls are removed due to identical block prefix/suffix sharing, however.
          //  When this happens, the inliner may think that it can inline all call sites found in
          //  the call graph, although this may not actually be true.
          if (numberOfCallSites < callers.size()) {
            return;
          }
          if (callers.size() < numberOfCallSites) {
            // Can't inline all call sites.
            return;
          }

          int multiCallerInliningInstructionLimit =
              multiCallerInliningInstructionLimits[callers.size() - 1];
          if (!singleTarget
              .getDefinition()
              .getCode()
              .estimatedSizeForInliningAtMost(multiCallerInliningInstructionLimit)) {
            // Multi caller inlining could lead to a size increase according to the heuristic.
            return;
          }
          callers.forEachEntry(
              (caller, count) -> {
                if (!caller.getOptimizationInfo().hasBeenInlinedIntoSingleCallSite()) {
                  multiInlineCallers.add(caller, currentGraphLens);
                }
              });
          getSimpleFeedback().setMultiCallerMethod(singleTarget);
        });
    multiInlineCallEdges.clear();
    return multiInlineCallers;
  }
}
