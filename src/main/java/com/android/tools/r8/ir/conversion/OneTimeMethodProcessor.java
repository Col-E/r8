// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.MethodProcessingId.Factory.ReservedMethodProcessingIds;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.ThrowingBiConsumer;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * A {@link MethodProcessor} that doesn't persist; rather just processes the given methods one-time,
 * along with a default abstraction of concurrent processing.
 */
public class OneTimeMethodProcessor extends MethodProcessor {

  private final MethodProcessingId.Factory methodProcessingIdFactory;

  private OneTimeMethodProcessor(
      MethodProcessingId.Factory methodProcessingIdFactory, SortedProgramMethodSet wave) {
    this.methodProcessingIdFactory = methodProcessingIdFactory;
    this.wave = wave;
  }

  public static OneTimeMethodProcessor create(ProgramMethod methodToProcess, AppView<?> appView) {
    return create(methodToProcess, appView.methodProcessingIdFactory());
  }

  public static OneTimeMethodProcessor create(
      ProgramMethod methodToProcess, MethodProcessingId.Factory methodProcessingIdFactory) {
    return new OneTimeMethodProcessor(
        methodProcessingIdFactory, SortedProgramMethodSet.create(methodToProcess));
  }

  public static OneTimeMethodProcessor create(
      SortedProgramMethodSet methodsToProcess, AppView<?> appView) {
    return create(methodsToProcess, appView.methodProcessingIdFactory());
  }

  public static OneTimeMethodProcessor create(
      SortedProgramMethodSet methodsToProcess,
      MethodProcessingId.Factory methodProcessingIdFactory) {
    return new OneTimeMethodProcessor(methodProcessingIdFactory, methodsToProcess);
  }

  @Override
  public boolean shouldApplyCodeRewritings(ProgramMethod method) {
    return true;
  }

  @Override
  public Phase getPhase() {
    return Phase.ONE_TIME;
  }

  public <E extends Exception> void forEachWaveWithExtension(
      ThrowingBiConsumer<ProgramMethod, MethodProcessingId, E> consumer) throws E {
    while (!wave.isEmpty()) {
      ReservedMethodProcessingIds methodProcessingIds = methodProcessingIdFactory.reserveIds(wave);
      int i = 0;
      for (ProgramMethod method : wave) {
        consumer.accept(method, methodProcessingIds.get(method, i++));
      }
      prepareForWaveExtensionProcessing();
    }
  }

  public <E extends Exception> void forEachWaveWithExtension(
      ThrowingBiConsumer<ProgramMethod, MethodProcessingId, E> consumer,
      ExecutorService executorService)
      throws ExecutionException {
    while (!wave.isEmpty()) {
      ReservedMethodProcessingIds methodProcessingIds = methodProcessingIdFactory.reserveIds(wave);
      ThreadUtils.processItems(
          wave,
          (method, index) -> consumer.accept(method, methodProcessingIds.get(method, index)),
          executorService);
      prepareForWaveExtensionProcessing();
    }
  }
}
