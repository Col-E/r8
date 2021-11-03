// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * A {@link MethodProcessor} that doesn't persist; rather just processes the given methods one-time,
 * along with a default abstraction of concurrent processing.
 */
public class OneTimeMethodProcessor extends MethodProcessorWithWave {

  private final ProcessorContext processorContext;

  private OneTimeMethodProcessor(ProcessorContext processorContext, ProgramMethodSet wave) {
    this.processorContext = processorContext;
    this.wave = wave;
  }

  public static Builder builder(ProcessorContext processorContext) {
    return new Builder(processorContext);
  }

  public static OneTimeMethodProcessor create(ProgramMethod methodToProcess, AppView<?> appView) {
    return create(ProgramMethodSet.create(methodToProcess), appView);
  }

  public static OneTimeMethodProcessor create(
      ProgramMethod methodToProcess, ProcessorContext processorContext) {
    return create(ProgramMethodSet.create(methodToProcess), processorContext);
  }

  public static OneTimeMethodProcessor create(
      ProgramMethodSet methodsToProcess, AppView<?> appView) {
    return create(methodsToProcess, appView.createProcessorContext());
  }

  public static OneTimeMethodProcessor create(
      ProgramMethodSet methodsToProcess, ProcessorContext processorContext) {
    return new OneTimeMethodProcessor(processorContext, methodsToProcess);
  }

  @Override
  public MethodProcessingContext createMethodProcessingContext(ProgramMethod method) {
    return processorContext.createMethodProcessingContext(method);
  }

  @Override
  public boolean shouldApplyCodeRewritings(ProgramMethod method) {
    return true;
  }

  @FunctionalInterface
  public interface MethodAction<E extends Exception> {
    void accept(ProgramMethod method, MethodProcessingContext methodProcessingContext) throws E;
  }

  public <E extends Exception> void forEachWaveWithExtension(MethodAction<E> consumer) throws E {
    while (!wave.isEmpty()) {
      for (ProgramMethod method : wave) {
        consumer.accept(method, processorContext.createMethodProcessingContext(method));
      }
      prepareForWaveExtensionProcessing();
    }
  }

  public <E extends Exception> void forEachWaveWithExtension(
      MethodAction<E> consumer, ExecutorService executorService) throws ExecutionException {
    while (!wave.isEmpty()) {
      ThreadUtils.processItems(
          wave,
          method -> consumer.accept(method, processorContext.createMethodProcessingContext(method)),
          executorService);
      prepareForWaveExtensionProcessing();
    }
  }

  public static class Builder {

    private final ProgramMethodSet methodsToProcess = ProgramMethodSet.create();
    private final ProcessorContext processorContext;

    Builder(ProcessorContext processorContext) {
      this.processorContext = processorContext;
    }

    public Builder add(ProgramMethod methodToProcess) {
      methodsToProcess.add(methodToProcess);
      return this;
    }

    public OneTimeMethodProcessor build() {
      return create(methodsToProcess, processorContext);
    }
  }
}
