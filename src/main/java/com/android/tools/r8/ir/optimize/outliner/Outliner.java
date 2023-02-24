// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.outliner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public abstract class Outliner {

  public static Outliner create(AppView<AppInfoWithLiveness> appView) {
    return appView.options().outline.enabled ? new OutlinerImpl(appView) : empty();
  }

  public static Outliner empty() {
    return new Outliner() {
      @Override
      public void collectOutlineSites(IRCode code, Timing timing) {
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
      public void prepareForPrimaryOptimizationPass(GraphLens graphLensForPrimaryOptimizationPass) {
        // Intentionally empty.
      }

      @Override
      public void performOutlining(
          IRConverter converter,
          OptimizationFeedbackDelayed feedback,
          ExecutorService executorService,
          Timing timing) {
        // Intentionally empty.
      }

      @Override
      public void rewriteWithLens() {
        // Intentionally empty.
      }
    };
  }

  public abstract void collectOutlineSites(IRCode code, Timing timing);

  public abstract void onMethodPruned(ProgramMethod method);

  public abstract void onMethodCodePruned(ProgramMethod method);

  public abstract void prepareForPrimaryOptimizationPass(
      GraphLens graphLensForPrimaryOptimizationPass);

  public abstract void performOutlining(
      IRConverter converter,
      OptimizationFeedbackDelayed feedback,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException;

  public abstract void rewriteWithLens();
}
