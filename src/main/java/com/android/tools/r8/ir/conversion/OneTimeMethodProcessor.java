// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * A {@link MethodProcessor} that doesn't persist; rather just processes the given methods one-time,
 * along with a default abstraction of concurrent processing.
 */
public class OneTimeMethodProcessor implements MethodProcessor {

  private Map<DexEncodedMethod, ProgramMethod> wave;

  private OneTimeMethodProcessor(Map<DexEncodedMethod, ProgramMethod> methodsToProcess) {
    this.wave = methodsToProcess;
  }

  public static OneTimeMethodProcessor getInstance() {
    return new OneTimeMethodProcessor(null);
  }

  public static OneTimeMethodProcessor getInstance(ProgramMethod methodToProcess) {
    Map<DexEncodedMethod, ProgramMethod> methodsToProcess = new IdentityHashMap<>();
    methodsToProcess.put(methodToProcess.getDefinition(), methodToProcess);
    return new OneTimeMethodProcessor(methodsToProcess);
  }

  public static OneTimeMethodProcessor getInstance(
      Map<DexEncodedMethod, ProgramMethod> methodsToProcess) {
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
    return wave != null && wave.containsKey(method.getDefinition());
  }

  public <E extends Exception> void forEachWave(
      ThrowingConsumer<ProgramMethod, E> consumer, ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processItems(wave.values(), consumer, executorService);
  }
}
