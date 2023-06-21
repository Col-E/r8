// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.fields;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Sets the final flag of fields that are only assigned inside the instance initializers of its
 * holder class.
 */
public class FieldFinalizer {

  private final AppView<AppInfoWithLiveness> appView;

  private FieldFinalizer(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public static void run(
      AppView<AppInfoWithLiveness> appView, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    timing.time("Finalize fields pass", () -> run(appView, executorService));
    appView.notifyOptimizationFinishedForTesting();
  }

  private static void run(AppView<AppInfoWithLiveness> appView, ExecutorService executorService)
      throws ExecutionException {
    if (appView.options().isAccessModificationEnabled()
        && appView.options().isOptimizing()
        && appView.options().isShrinking()) {
      new FieldFinalizer(appView).processClasses(executorService);
    }
  }

  private void processClasses(ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(appView.appInfo().classes(), this::processClass, executorService);
  }

  private void processClass(DexProgramClass clazz) {
    clazz.forEachProgramField(this::processField);
  }

  private void processField(ProgramField field) {
    FieldAccessFlags accessFlags = field.getAccessFlags();
    if (!accessFlags.isFinal() && !accessFlags.isVolatile() && field.isEffectivelyFinal(appView)) {
      accessFlags.promoteToFinal();
    }
  }
}
