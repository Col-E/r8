// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
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
  private final ProcessorContext processorContext;
  private final Set<DexType> scheduled = Sets.newIdentityHashSet();

  public D8MethodProcessor(IRConverter converter, ExecutorService executorService) {
    this.converter = converter;
    this.executorService = executorService;
    this.processorContext = converter.appView.createProcessorContext();
  }

  public void addScheduled(DexProgramClass clazz) {
    boolean added = scheduled.add(clazz.getType());
    assert added;
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
  public void scheduleMethodForProcessingAfterCurrentWave(ProgramMethod method) {
    if (!scheduled.contains(method.getHolderType())
        && !converter.appView.getSyntheticItems().isNonLegacySynthetic(method.getHolder())) {
      // The non-synthetic holder is not scheduled. It will be processed once holder is scheduled.
      return;
    }
    futures.add(
        ThreadUtils.processAsynchronously(
            () ->
                converter.rewriteCode(
                    method,
                    OptimizationFeedbackIgnore.getInstance(),
                    this,
                    processorContext.createMethodProcessingContext(method)),
            executorService));
  }

  @Override
  public CallSiteInformation getCallSiteInformation() {
    throw new Unreachable("Invalid attempt to obtain call-site information in D8");
  }

  public void awaitMethodProcessing() throws ExecutionException {
    ThreadUtils.awaitFutures(futures);
    futures.clear();
  }

  public void processMethod(ProgramMethod method) {
    converter.convertMethod(method, this, processorContext.createMethodProcessingContext(method));
  }
}
