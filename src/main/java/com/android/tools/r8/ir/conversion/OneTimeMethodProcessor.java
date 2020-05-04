// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * A {@link MethodProcessor} that doesn't persist; rather just processes the given methods one-time,
 * along with a default abstraction of concurrent processing.
 */
public class OneTimeMethodProcessor implements MethodProcessor {

  private ProgramMethodSet wave;

  private OneTimeMethodProcessor(ProgramMethodSet methodsToProcess) {
    this.wave = methodsToProcess;
  }

  public static OneTimeMethodProcessor getInstance() {
    return new OneTimeMethodProcessor(null);
  }

  public static OneTimeMethodProcessor getInstance(ProgramMethod methodToProcess) {
    return new OneTimeMethodProcessor(ProgramMethodSet.create(methodToProcess));
  }

  public static OneTimeMethodProcessor getInstance(ProgramMethodSet methodsToProcess) {
    return new OneTimeMethodProcessor(methodsToProcess);
  }

  @Override
  public boolean shouldApplyCodeRewritings(ProgramMethod method) {
    return true;
  }

  @Override
  public Phase getPhase() {
    return Phase.ONE_TIME;
  }

  @Override
  public boolean isProcessedConcurrently(ProgramMethod method) {
    return wave != null && wave.contains(method);
  }

  public <E extends Exception> void forEachWave(
      ThrowingConsumer<ProgramMethod, E> consumer, ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processItems(wave, consumer, executorService);
  }
}
