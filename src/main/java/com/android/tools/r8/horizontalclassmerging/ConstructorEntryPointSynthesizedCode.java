// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.synthetic.AbstractSynthesizedCode;
import java.util.SortedMap;
import java.util.function.Consumer;

public class ConstructorEntryPointSynthesizedCode extends AbstractSynthesizedCode {
  private DexMethod newConstructor;
  private DexMethod originalMethod;
  private final SortedMap<Integer, DexMethod> typeConstructors;

  public ConstructorEntryPointSynthesizedCode(
      SortedMap<Integer, DexMethod> typeConstructors,
      DexMethod newConstructor,
      DexMethod originalMethod) {
    this.typeConstructors = typeConstructors;
    this.newConstructor = newConstructor;
    this.originalMethod = originalMethod;
  }

  @Override
  public SourceCodeProvider getSourceCodeProvider() {
    return callerPosition ->
        new ConstructorEntryPoint(typeConstructors, newConstructor, callerPosition, originalMethod);
  }

  @Override
  public Consumer<UseRegistry> getRegistryCallback() {
    return this::registerReachableDefinitions;
  }

  private void registerReachableDefinitions(UseRegistry registry) {
    registry.registerInvokeDirect(newConstructor);
    for (DexMethod typeConstructor : typeConstructors.values()) {
      registry.registerInvokeDirect(typeConstructor);
    }
  }
}
