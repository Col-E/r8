// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import java.util.BitSet;

public class LibraryOptimizationInfoInitializer {

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;

  private final OptimizationFeedbackSimple feedback = OptimizationFeedbackSimple.getInstance();

  public LibraryOptimizationInfoInitializer(AppView<?> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
  }

  public void run() {
    modelRequireNonNullMethods();
  }

  private void modelRequireNonNullMethods() {
    for (DexMethod requireNonNullMethod : dexItemFactory.objectsMethods.requireNonNullMethods()) {
      DexEncodedMethod definition = appView.definitionFor(requireNonNullMethod);
      if (definition != null) {
        feedback.methodReturnsArgument(definition, 0);

        BitSet nonNullParamOrThrow = new BitSet();
        nonNullParamOrThrow.set(0);
        feedback.setNonNullParamOrThrow(definition, nonNullParamOrThrow);

        BitSet nonNullParamOnNormalExits = new BitSet();
        nonNullParamOnNormalExits.set(0);
        feedback.setNonNullParamOnNormalExits(definition, nonNullParamOnNormalExits);
      }
    }
  }
}
