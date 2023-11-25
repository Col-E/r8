// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.numberunboxer;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public abstract class NumberUnboxer {

  public static NumberUnboxer create(AppView<AppInfoWithLiveness> appView) {
    if (appView.testing().enableNumberUnboxer) {
      return new NumberUnboxerImpl(appView);
    }
    return empty();
  }

  public static NumberUnboxer empty() {
    return new Empty();
  }

  public abstract void prepareForPrimaryOptimizationPass(
      Timing timing, ExecutorService executorService) throws ExecutionException;

  public abstract void analyze(IRCode code);

  public abstract void unboxNumbers(
      PostMethodProcessor.Builder postMethodProcessorBuilder,
      Timing timing,
      ExecutorService executorService);

  public abstract void onMethodPruned(ProgramMethod method);

  public abstract void onMethodCodePruned(ProgramMethod method);

  public abstract void rewriteWithLens();

  static class Empty extends NumberUnboxer {

    @Override
    public void prepareForPrimaryOptimizationPass(Timing timing, ExecutorService executorService)
        throws ExecutionException {
      // Intentionally empty.
    }

    @Override
    public void analyze(IRCode code) {
      // Intentionally empty.
    }

    @Override
    public void unboxNumbers(
        PostMethodProcessor.Builder postMethodProcessorBuilder,
        Timing timing,
        ExecutorService executorService) {
      // Intentionally empty.
    }

    @Override
    public void onMethodPruned(ProgramMethod method) {
      // Intentionally empty.
    }

    @Override
    public void onMethodCodePruned(ProgramMethod method) {
      // Intentionally empty.
    }

    @Override
    public void rewriteWithLens() {
      // Intentionally empty.
    }
  }
}
