// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.code;

import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.horizontalclassmerging.ConstructorEntryPoint;
import com.android.tools.r8.ir.synthetic.AbstractSynthesizedCode;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import java.util.function.Consumer;

public class ConstructorEntryPointSynthesizedCode extends AbstractSynthesizedCode {
  private final DexMethod newConstructor;
  private final DexMethod originalMethod;
  private final DexField classIdField;
  private final Int2ReferenceSortedMap<DexMethod> typeConstructors;

  public ConstructorEntryPointSynthesizedCode(
      Int2ReferenceSortedMap<DexMethod> typeConstructors,
      DexMethod newConstructor,
      DexField classIdField,
      DexMethod originalMethod) {
    this.typeConstructors = typeConstructors;
    this.newConstructor = newConstructor;
    this.classIdField = classIdField;
    this.originalMethod = originalMethod;
  }

  @Override
  public SourceCodeProvider getSourceCodeProvider() {
    return (ignored, callerPosition) ->
        new ConstructorEntryPoint(
            typeConstructors, newConstructor, classIdField, callerPosition, originalMethod);
  }

  @Override
  public Consumer<UseRegistry> getRegistryCallback(DexClassAndMethod method) {
    return this::registerReachableDefinitions;
  }

  private void registerReachableDefinitions(UseRegistry registry) {
    assert registry.getTraversalContinuation().shouldContinue();
    for (DexMethod typeConstructor : typeConstructors.values()) {
      registry.registerInvokeDirect(typeConstructor);
      if (registry.getTraversalContinuation().shouldBreak()) {
        return;
      }
    }
  }

  @Override
  public boolean isHorizontalClassMergerCode() {
    return true;
  }
}
