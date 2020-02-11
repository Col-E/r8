// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.Set;

public class LibraryOptimizationInfoInitializer {

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;

  private final OptimizationFeedbackSimple feedback = OptimizationFeedbackSimple.getInstance();
  private final Set<DexType> modeledLibraryTypes = Sets.newIdentityHashSet();

  LibraryOptimizationInfoInitializer(AppView<?> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
  }

  void run() {
    modelLibraryMethodsReturningNonNull();
    modelLibraryMethodsReturningReceiver();
    modelRequireNonNullMethods();
  }

  Set<DexType> getModeledLibraryTypes() {
    return modeledLibraryTypes;
  }

  private void modelLibraryMethodsReturningNonNull() {
    for (DexMethod method : dexItemFactory.libraryMethodsReturningNonNull) {
      DexEncodedMethod definition = lookupMethod(method);
      if (definition != null) {
        feedback.methodNeverReturnsNull(definition);
      }
    }
  }

  private void modelLibraryMethodsReturningReceiver() {
    for (DexMethod method : dexItemFactory.libraryMethodsReturningReceiver) {
      DexEncodedMethod definition = lookupMethod(method);
      if (definition != null) {
        feedback.methodReturnsArgument(definition, 0);
      }
    }
  }

  private void modelRequireNonNullMethods() {
    for (DexMethod requireNonNullMethod : dexItemFactory.objectsMethods.requireNonNullMethods()) {
      DexEncodedMethod definition = lookupMethod(requireNonNullMethod);
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

  private DexEncodedMethod lookupMethod(DexMethod method) {
    DexEncodedMethod encodedMethod = appView.definitionFor(method);
    if (encodedMethod != null) {
      modeledLibraryTypes.add(method.holder);
      return encodedMethod;
    }
    return null;
  }
}
