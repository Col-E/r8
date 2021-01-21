// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackIgnore;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class D8MethodProcessor extends MethodProcessor {

  private final IRConverter converter;
  private final ExecutorService executorService;
  private final List<Future<?>> futures = Collections.synchronizedList(new ArrayList<>());

  public D8MethodProcessor(IRConverter converter, ExecutorService executorService) {
    this.converter = converter;
    this.executorService = executorService;
  }

  public void awaitMethodProcessing() throws ExecutionException {
    ThreadUtils.awaitFutures(futures);
    futures.clear();
  }

  @Override
  public boolean shouldApplyCodeRewritings(ProgramMethod method) {
    return true;
  }

  @Override
  public void scheduleMethodForProcessingAfterCurrentWave(ProgramMethod method) {
    futures.add(
        ThreadUtils.processAsynchronously(
            () ->
                converter.rewriteCode(method, OptimizationFeedbackIgnore.getInstance(), this, null),
            executorService));
  }

  public boolean verifyAllMethodsProcessed() {
    assert futures.isEmpty();
    return true;
  }
}
