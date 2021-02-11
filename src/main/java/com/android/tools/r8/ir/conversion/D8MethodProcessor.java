// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackIgnore;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class D8MethodProcessor extends MethodProcessor {

  private final IRConverter converter;
  private final ExecutorService executorService;
  private final List<Future<?>> futures = Collections.synchronizedList(new ArrayList<>());
  private final Set<DexType> scheduled = Sets.newIdentityHashSet();

  private ProcessorContext processorContext;

  public D8MethodProcessor(IRConverter converter, ExecutorService executorService) {
    this.converter = converter;
    this.executorService = executorService;
    this.processorContext = converter.appView.createProcessorContext();
  }

  public void addScheduled(DexProgramClass clazz) {
    boolean added = scheduled.add(clazz.getType());
    assert added;
  }

  public void newWave() {
    this.processorContext = converter.appView.createProcessorContext();
  }

  @Override
  public boolean isProcessedConcurrently(ProgramMethod method) {
    // In D8 all methods are considered independently compiled.
    return true;
  }

  @Override
  public boolean shouldApplyCodeRewritings(ProgramMethod method) {
    return true;
  }

  @Override
  public void scheduleDesugaredMethodForProcessing(ProgramMethod method) {
    // TODO(b/179755192): By building up waves of methods in the class converter, we can avoid the
    //  following check and always process the method asynchronously.
    if (!scheduled.contains(method.getHolderType())
        && !converter.appView.getSyntheticItems().isNonLegacySynthetic(method.getHolder())) {
      // The non-synthetic holder is not scheduled. It will be processed once holder is scheduled.
      return;
    }
    futures.add(
        ThreadUtils.processAsynchronously(
            () ->
                converter.rewriteDesugaredCode(
                    method,
                    OptimizationFeedbackIgnore.getInstance(),
                    this,
                    processorContext.createMethodProcessingContext(method)),
            executorService));
  }

  public void scheduleDesugaredMethodsForProcessing(Iterable<ProgramMethod> methods) {
    methods.forEach(this::scheduleDesugaredMethodForProcessing);
  }

  @Override
  public CallSiteInformation getCallSiteInformation() {
    throw new Unreachable("Invalid attempt to obtain call-site information in D8");
  }

  public void awaitMethodProcessing() throws ExecutionException {
    ThreadUtils.awaitFutures(futures);
    futures.clear();
  }

  public void processMethod(
      ProgramMethod method, CfInstructionDesugaringEventConsumer desugaringEventConsumer) {
    converter.convertMethod(
        method,
        desugaringEventConsumer,
        this,
        processorContext.createMethodProcessingContext(method));
  }

  public void processDesugaredMethod(ProgramMethod method) {
    processMethod(method, CfInstructionDesugaringEventConsumer.createForDesugaredCode());
  }

  public boolean verifyNoPendingMethodProcessing() {
    assert futures.isEmpty();
    return true;
  }
}
