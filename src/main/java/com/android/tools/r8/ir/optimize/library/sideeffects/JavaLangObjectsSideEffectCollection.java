// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library.sideeffects;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.List;

public class JavaLangObjectsSideEffectCollection {

  public static boolean toStringMayHaveSideEffects(AppView<?> appView, List<Value> arguments) {
    // Calling toString() on an array does not call toString() on the array elements.
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    TypeElement type = arguments.get(0).getType();
    if (type.isArrayType() || type.isNullType()) {
      return false;
    }

    assert type.isClassType();

    // Check if this is a library class with a toString() method that does not have side effects.
    DexType classType = type.asClassType().getClassType();
    DexMethod toStringMethodReference =
        dexItemFactory.objectMembers.toString.withHolder(classType, dexItemFactory);
    if (appView
        .getLibraryMethodSideEffectModelCollection()
        .isSideEffectFreeFinalMethod(toStringMethodReference, arguments)) {
      return false;
    }

    // Check if there is an -assumenosideeffects rule for the toString() method.
    if (appView.getAssumeInfoCollection().isSideEffectFree(toStringMethodReference)) {
      return false;
    }

    if (appView.appInfo().hasLiveness()) {
      AppInfoWithLiveness appInfo = appView.appInfo().withLiveness();

      // Check if this is a program class with a toString() method that does not have side effects.
      DexClass clazz = appInfo.definitionFor(classType);
      if (clazz != null && clazz.isEffectivelyFinal(appView)) {
        SingleResolutionResult<?> resolutionResult =
            appInfo.resolveMethodOnLegacy(clazz, toStringMethodReference).asSingleResolution();
        if (resolutionResult != null
            && !resolutionResult.getResolvedMethod().getOptimizationInfo().mayHaveSideEffects()) {
          return false;
        }
      }
    }

    return true;
  }
}
