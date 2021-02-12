// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.lambda;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.ir.conversion.ClassConverterResult;
import com.android.tools.r8.ir.conversion.D8MethodProcessor;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class D8LambdaDesugaring {

  public static void synthesizeAccessibilityBridgesForLambdaClasses(
      AppView<?> appView,
      ClassConverterResult classConverterResult,
      D8MethodProcessor methodProcessor)
      throws ExecutionException {
    Map<DexMethod, DexMethod> forcefullyMovedLambdaMethods = new IdentityHashMap<>();
    ProgramMethodSet seenAccessibilityBridges = ProgramMethodSet.createConcurrent();
    classConverterResult.forEachSynthesizedLambdaClassWithDeterministicOrdering(
        lambdaClass -> {
          // Collect the accessibility bridges that require processing. Note that we cannot schedule
          // the methods for processing directly here, since that would lead to concurrent IR
          // processing meanwhile we update the program (insert bridges on existing classes).
          lambdaClass.target.ensureAccessibilityIfNeeded(
              forcefullyMovedLambdaMethods::put, seenAccessibilityBridges::add);
        });
    methodProcessor
        .scheduleDesugaredMethodsForProcessing(seenAccessibilityBridges)
        .awaitMethodProcessing();
    rewriteEnclosingMethodAttributes(appView, forcefullyMovedLambdaMethods);
  }

  private static void rewriteEnclosingMethodAttributes(
      AppView<?> appView, Map<DexMethod, DexMethod> forcefullyMovedLambdaMethods) {
    if (forcefullyMovedLambdaMethods.isEmpty()) {
      return;
    }
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (clazz.hasEnclosingMethodAttribute()) {
        DexMethod enclosingMethod = clazz.getEnclosingMethodAttribute().getEnclosingMethod();
        DexMethod rewrittenEnclosingMethod = forcefullyMovedLambdaMethods.get(enclosingMethod);
        if (rewrittenEnclosingMethod != null) {
          clazz.setEnclosingMethodAttribute(new EnclosingMethodAttribute(rewrittenEnclosingMethod));
        }
      }
    }
  }
}
