// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.compose;

import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.constant.SparseConditionalConstantPropagation;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.AbstractValueSupplier;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.MethodProcessorEventConsumer;
import com.android.tools.r8.ir.conversion.PrimaryR8IRConverter;
import com.android.tools.r8.ir.conversion.callgraph.CallSiteInformation;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.optimize.argumentpropagation.ArgumentPropagatorCodeScanner;
import com.android.tools.r8.optimize.argumentpropagation.ArgumentPropagatorOptimizationInfoPopulator;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMonomorphicMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ParameterState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.LazyBox;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;

public class ComposeMethodProcessor extends MethodProcessor {

  private final AppView<AppInfoWithLiveness> appView;
  private final ArgumentPropagatorCodeScanner codeScanner;
  private final PrimaryR8IRConverter converter;

  private final Set<ComposableCallGraphNode> processed = Sets.newIdentityHashSet();

  public ComposeMethodProcessor(
      AppView<AppInfoWithLiveness> appView,
      ComposableCallGraph callGraph,
      PrimaryR8IRConverter converter) {
    this.appView = appView;
    this.codeScanner = new ArgumentPropagatorCodeScannerForComposableFunctions(appView, callGraph);
    this.converter = converter;
  }

  // TODO(b/302483644): Process wave concurrently.
  public Set<ComposableCallGraphNode> processWave(Set<ComposableCallGraphNode> wave) {
    ProcessorContext processorContext = appView.createProcessorContext();
    wave.forEach(
        node -> {
          assert !processed.contains(node);
          converter.processDesugaredMethod(
              node.getMethod(),
              OptimizationFeedback.getIgnoreFeedback(),
              this,
              processorContext.createMethodProcessingContext(node.getMethod()),
              MethodConversionOptions.forLirPhase(appView));
        });
    processed.addAll(wave);
    return optimizeComposableFunctionsCalledFromWave(wave);
  }

  private Set<ComposableCallGraphNode> optimizeComposableFunctionsCalledFromWave(
      Set<ComposableCallGraphNode> wave) {
    ArgumentPropagatorOptimizationInfoPopulator optimizationInfoPopulator =
        new ArgumentPropagatorOptimizationInfoPopulator(appView, null, null, null);
    Set<ComposableCallGraphNode> optimizedComposableFunctions = Sets.newIdentityHashSet();
    wave.forEach(
        node ->
            node.forEachComposableCallee(
                callee -> {
                  if (Iterables.all(callee.getCallers(), this::isProcessed)) {
                    optimizationInfoPopulator.setOptimizationInfo(
                        callee.getMethod(), ProgramMethodSet.empty(), getMethodState(callee));
                    // TODO(b/302483644): Only enqueue this callee if its optimization info changed.
                    optimizedComposableFunctions.add(callee);
                  }
                }));
    return optimizedComposableFunctions;
  }

  private MethodState getMethodState(ComposableCallGraphNode node) {
    assert processed.containsAll(node.getCallers());
    MethodState methodState = codeScanner.getMethodStates().get(node.getMethod());
    return widenMethodState(methodState);
  }

  /**
   * If a parameter state of the current method state encodes that it is greater than (lattice wise)
   * than another parameter in the program, then widen the parameter state to unknown. This is
   * needed since we are not guaranteed to have seen all possible call sites of the callers of this
   * method.
   */
  private MethodState widenMethodState(MethodState methodState) {
    assert !methodState.isBottom();
    assert !methodState.isPolymorphic();
    if (methodState.isMonomorphic()) {
      ConcreteMonomorphicMethodState monomorphicMethodState = methodState.asMonomorphic();
      for (int i = 0; i < monomorphicMethodState.size(); i++) {
        if (monomorphicMethodState.getParameterState(i).isConcrete()) {
          ConcreteParameterState concreteParameterState =
              monomorphicMethodState.getParameterState(i).asConcrete();
          if (concreteParameterState.hasInParameters()) {
            monomorphicMethodState.setParameterState(i, ParameterState.unknown());
          }
        }
      }
    } else {
      assert methodState.isUnknown();
    }
    return methodState;
  }

  public void scan(ProgramMethod method, IRCode code, Timing timing) {
    LazyBox<Map<Value, AbstractValue>> abstractValues =
        new LazyBox<>(() -> new SparseConditionalConstantPropagation(appView).analyze(code));
    AbstractValueSupplier abstractValueSupplier =
        value -> {
          AbstractValue abstractValue = abstractValues.computeIfAbsent().get(value);
          assert abstractValue != null;
          return abstractValue;
        };
    codeScanner.scan(method, code, abstractValueSupplier, timing);
  }

  public boolean isProcessed(ComposableCallGraphNode node) {
    return processed.contains(node);
  }

  @Override
  public CallSiteInformation getCallSiteInformation() {
    return CallSiteInformation.empty();
  }

  @Override
  public MethodProcessorEventConsumer getEventConsumer() {
    throw new Unreachable();
  }

  @Override
  public boolean isComposeMethodProcessor() {
    return true;
  }

  @Override
  public ComposeMethodProcessor asComposeMethodProcessor() {
    return this;
  }

  @Override
  public boolean isProcessedConcurrently(ProgramMethod method) {
    return false;
  }

  @Override
  public void scheduleDesugaredMethodForProcessing(ProgramMethod method) {
    throw new Unreachable();
  }

  @Override
  public boolean shouldApplyCodeRewritings(ProgramMethod method) {
    return false;
  }
}
