// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.threading.ThreadingModule;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.ThreadUtils.WorkLoad;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * A {@link MethodProcessor} that doesn't persist; rather just processes the given methods one-time,
 * along with a default abstraction of concurrent processing.
 */
public class OneTimeMethodProcessor extends MethodProcessorWithWave {

  private final MethodProcessorEventConsumer eventConsumer;
  private final ProcessorContext processorContext;

  private OneTimeMethodProcessor(
      MethodProcessorEventConsumer eventConsumer,
      ProcessorContext processorContext,
      ProgramMethodSet wave) {
    this.eventConsumer = eventConsumer;
    this.processorContext = processorContext;
    this.wave = wave;
  }

  public static Builder builder(
      MethodProcessorEventConsumer eventConsumer, ProcessorContext processorContext) {
    return new Builder(eventConsumer, processorContext);
  }

  public static OneTimeMethodProcessor create(
      ProgramMethod methodToProcess,
      MethodProcessorEventConsumer eventConsumer,
      AppView<?> appView) {
    return create(ProgramMethodSet.create(methodToProcess), eventConsumer, appView);
  }

  public static OneTimeMethodProcessor create(
      ProgramMethod methodToProcess,
      MethodProcessorEventConsumer eventConsumer,
      ProcessorContext processorContext) {
    return create(ProgramMethodSet.create(methodToProcess), eventConsumer, processorContext);
  }

  public static OneTimeMethodProcessor create(
      ProgramMethodSet methodsToProcess,
      MethodProcessorEventConsumer eventConsumer,
      AppView<?> appView) {
    return create(methodsToProcess, eventConsumer, appView.createProcessorContext());
  }

  public static OneTimeMethodProcessor create(
      ProgramMethodSet methodsToProcess,
      MethodProcessorEventConsumer eventConsumer,
      ProcessorContext processorContext) {
    return new OneTimeMethodProcessor(eventConsumer, processorContext, methodsToProcess);
  }

  @Override
  public MethodProcessorEventConsumer getEventConsumer() {
    return eventConsumer;
  }

  @Override
  public boolean shouldApplyCodeRewritings(ProgramMethod method) {
    return true;
  }

  @FunctionalInterface
  public interface MethodAction {
    void accept(ProgramMethod method, MethodProcessingContext methodProcessingContext);
  }

  public void forEachWaveWithExtension(MethodAction consumer) {
    while (!wave.isEmpty()) {
      for (ProgramMethod method : wave) {
        consumer.accept(method, processorContext.createMethodProcessingContext(method));
      }
      prepareForWaveExtensionProcessing();
    }
  }

  public void forEachWaveWithExtension(
      MethodAction consumer, ThreadingModule threadingModule, ExecutorService executorService)
      throws ExecutionException {
    while (!wave.isEmpty()) {
      ThreadUtils.processItems(
          wave,
          (method, ignored) ->
              consumer.accept(method, processorContext.createMethodProcessingContext(method)),
          threadingModule,
          executorService,
          WorkLoad.HEAVY);
      prepareForWaveExtensionProcessing();
    }
  }

  public static class Builder {

    private final ProgramMethodSet methodsToProcess = ProgramMethodSet.create();

    private final MethodProcessorEventConsumer eventConsumer;
    private final ProcessorContext processorContext;

    Builder(MethodProcessorEventConsumer eventConsumer, ProcessorContext processorContext) {
      this.eventConsumer = eventConsumer;
      this.processorContext = processorContext;
    }

    public Builder add(ProgramMethod methodToProcess) {
      methodsToProcess.add(methodToProcess);
      return this;
    }

    public OneTimeMethodProcessor build() {
      return create(methodsToProcess, eventConsumer, processorContext);
    }
  }
}
