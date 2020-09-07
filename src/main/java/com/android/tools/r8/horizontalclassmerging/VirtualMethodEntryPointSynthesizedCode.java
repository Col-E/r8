// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.synthetic.SynthesizedCode;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import java.util.function.Consumer;

public class VirtualMethodEntryPointSynthesizedCode extends SynthesizedCode {
  private final Int2ReferenceSortedMap<DexMethod> mappedMethods;
  private final DexField classIdField;

  public VirtualMethodEntryPointSynthesizedCode(
      Int2ReferenceSortedMap<DexMethod> mappedMethods,
      DexField classIdField,
      DexMethod superMethod,
      DexMethod method,
      DexMethod originalMethod) {
    super(
        position ->
            new VirtualMethodEntryPoint(
                mappedMethods, classIdField, superMethod, method, position, originalMethod));

    this.mappedMethods = mappedMethods;
    this.classIdField = classIdField;
  }

  @Override
  public Consumer<UseRegistry> getRegistryCallback() {
    return this::registerReachableDefinitions;
  }

  private void registerReachableDefinitions(UseRegistry registry) {
    for (DexMethod mappedMethod : mappedMethods.values()) {
      registry.registerInvokeDirect(mappedMethod);
    }
  }
}
