// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.ir.conversion.FieldOptimizationFeedback;
import com.android.tools.r8.ir.conversion.MethodOptimizationFeedback;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public abstract class OptimizationFeedback
    implements FieldOptimizationFeedback, MethodOptimizationFeedback {

  public interface OptimizationInfoFixer {

    void fixup(DexEncodedField field);

    void fixup(DexEncodedMethod method);
  }

  public void fixupOptimizationInfos(
      AppView<?> appView, ExecutorService executorService, OptimizationInfoFixer fixer)
      throws ExecutionException {
    List<Future<?>> futures = new ArrayList<>();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      futures.add(
          executorService.submit(
              () -> {
                clazz.fields().forEach(fixer::fixup);
                clazz.methods().forEach(fixer::fixup);
                return null;
              }));
    }
    ThreadUtils.awaitFutures(futures);
  }
}
