// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.code;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.horizontalclassmerging.VirtualMethodEntryPoint;
import com.android.tools.r8.ir.synthetic.SynthesizedCode;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import java.util.function.Consumer;

public class VirtualMethodEntryPointSynthesizedCode extends SynthesizedCode {
  private final Int2ReferenceSortedMap<DexMethod> mappedMethods;

  public VirtualMethodEntryPointSynthesizedCode(
      Int2ReferenceSortedMap<DexMethod> mappedMethods,
      DexField classIdField,
      DexMethod superMethod,
      DexMethod method,
      DexMethod originalMethod,
      DexItemFactory factory) {
    super(
        (context, position) ->
            new VirtualMethodEntryPoint(
                mappedMethods,
                classIdField,
                computeSuperMethodTarget(superMethod, context, factory),
                method,
                position,
                originalMethod));
    this.mappedMethods = mappedMethods;
  }

  private static DexMethod computeSuperMethodTarget(
      DexMethod superMethod, ProgramMethod method, DexItemFactory factory) {
    // We are only using superMethod as a bit but if this is changed to generate CfCode directly,
    // the superMethod needs to be computed by mapping through the lens.
    if (superMethod == null) {
      return null;
    }
    return method.getReference().withHolder(method.getHolder().superType, factory);
  }

  @Override
  public Consumer<UseRegistry> getRegistryCallback() {
    return this::registerReachableDefinitions;
  }

  private void registerReachableDefinitions(UseRegistry registry) {
    assert registry.getTraversalContinuation().shouldContinue();
    for (DexMethod mappedMethod : mappedMethods.values()) {
      registry.registerInvokeDirect(mappedMethod);
      if (registry.getTraversalContinuation().shouldBreak()) {
        return;
      }
    }
  }

  @Override
  public boolean isHorizontalClassMergingCode() {
    return true;
  }
}
