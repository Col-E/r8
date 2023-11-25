// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.callgraph.CallSiteInformation;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackIgnore;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.threading.SynchronizedTaskCollection;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class D8MethodProcessor extends MethodProcessor {

  private final ProfileCollectionAdditions profileCollectionAdditions;
  private final PrimaryD8L8IRConverter converter;
  private final MethodProcessorEventConsumer eventConsumer;
  private final Set<DexType> scheduled = Sets.newIdentityHashSet();

  // Asynchronous method processing actions. These are "terminal" method processing actions in the
  // sense that the method processing is known not to fork any other futures.
  private final SynchronizedTaskCollection<?> terminalTasks;

  // Asynchronous method processing actions. This list includes both "terminal" and "non-terminal"
  // method processing actions. Thus, before the asynchronous method processing finishes, it may
  // fork the processing of another method.
  private final SynchronizedTaskCollection<?> nonTerminalTasks;

  private ProcessorContext processorContext;

  public D8MethodProcessor(
      ProfileCollectionAdditions profileCollectionAdditions,
      PrimaryD8L8IRConverter converter,
      ExecutorService executorService) {
    this.profileCollectionAdditions = profileCollectionAdditions;
    this.converter = converter;
    this.eventConsumer = MethodProcessorEventConsumer.createForD8(profileCollectionAdditions);
    this.processorContext = converter.appView.createProcessorContext();
    this.terminalTasks = new SynchronizedTaskCollection<>(converter.options, executorService);
    this.nonTerminalTasks = new SynchronizedTaskCollection<>(converter.options, executorService);
  }

  public void addScheduled(DexProgramClass clazz) {
    boolean added = scheduled.add(clazz.getType());
    assert added;
  }

  public void newWave() {
    this.processorContext = converter.appView.createProcessorContext();
  }

  public ProfileCollectionAdditions getProfileCollectionAdditions() {
    return profileCollectionAdditions;
  }

  @Override
  public MethodProcessorEventConsumer getEventConsumer() {
    return eventConsumer;
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

  public void scheduleMethodForProcessing(
      ProgramMethod method, CfInstructionDesugaringEventConsumer eventConsumer) {
    // TODO(b/179755192): By building up waves of methods in the class converter, we can avoid the
    //  following check and always process the method asynchronously.
    if (!scheduled.contains(method.getHolderType())
        && !converter.appView.getSyntheticItems().isSynthetic(method.getHolder())) {
      // The non-synthetic holder is not scheduled. It will be processed once holder is scheduled.
      return;
    }
    nonTerminalTasks.submitUnchecked(
        () ->
            converter.rewriteNonDesugaredCode(
                method,
                eventConsumer,
                OptimizationFeedbackIgnore.getInstance(),
                this,
                processorContext.createMethodProcessingContext(method)));
  }

  @Override
  public void scheduleDesugaredMethodForProcessing(ProgramMethod method) {
    // TODO(b/179755192): By building up waves of methods in the class converter, we can avoid the
    //  following check and always process the method asynchronously.
    if (!scheduled.contains(method.getHolderType())
        && !converter.appView.getSyntheticItems().isSynthetic(method.getHolder())) {
      // The non-synthetic holder is not scheduled. It will be processed once holder is scheduled.
      return;
    }
    if (method.getDefinition().isAbstract()) {
      return;
    }
    terminalTasks.submitUnchecked(
        () ->
            converter.rewriteDesugaredCode(
                method,
                OptimizationFeedbackIgnore.getInstance(),
                this,
                processorContext.createMethodProcessingContext(method),
                MethodConversionOptions.forD8(converter.appView)));
  }

  public void scheduleDesugaredMethodsForProcessing(Iterable<ProgramMethod> methods) {
    methods.forEach(this::scheduleDesugaredMethodForProcessing);
  }

  @Override
  public CallSiteInformation getCallSiteInformation() {
    throw new Unreachable("Invalid attempt to obtain call-site information in D8");
  }

  public void awaitMethodProcessing() throws ExecutionException {
    nonTerminalTasks.await();
    terminalTasks.await();
  }

  public void processMethod(
      ProgramMethod method, CfInstructionDesugaringEventConsumer desugaringEventConsumer) {
    converter.convertMethod(
        method,
        desugaringEventConsumer,
        this,
        processorContext.createMethodProcessingContext(method));
  }

  public boolean verifyNoPendingMethodProcessing() {
    assert terminalTasks.isEmpty();
    assert nonTerminalTasks.isEmpty();
    return true;
  }
}
