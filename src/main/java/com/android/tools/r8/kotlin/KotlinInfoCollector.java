// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class KotlinInfoCollector {
  public static void computeKotlinInfoForProgramClasses(
      DexApplication application, AppView<?> appView, ExecutorService executorService)
      throws ExecutionException {
    if (appView.options().kotlinOptimizationOptions().disableKotlinSpecificOptimizations) {
      return;
    }
    Kotlin kotlin = appView.dexItemFactory().kotlin;
    ThreadUtils.processItems(
        application.classes(),
        programClass -> {
          programClass.setKotlinInfo(kotlin.getKotlinInfo(programClass, appView));
        },
        executorService);
  }
}
