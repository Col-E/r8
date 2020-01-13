// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.GraphLense.NestedGraphLense;
import com.android.tools.r8.ir.code.Invoke;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

class LambdaRewriterGraphLense extends NestedGraphLense {

  // We don't map the invocation type in the lens code rewriter for lambda accessibility bridges to
  // ensure that we always compute the same unique id for invoke-custom instructions.
  //
  // TODO(b/129458850): It might be possible to avoid this by performing lambda desugaring prior to
  //  lens code rewriting in the IR converter.
  private boolean shouldMapInvocationType = false;

  LambdaRewriterGraphLense(AppView<?> appView) {
    super(
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableBiMap.of(),
        HashBiMap.create(),
        appView.graphLense(),
        appView.dexItemFactory());
  }

  @Override
  protected boolean isLegitimateToHaveEmptyMappings() {
    return true;
  }

  void addOriginalMethodSignatures(Map<DexMethod, DexMethod> originalMethodSignatures) {
    this.originalMethodSignatures.putAll(originalMethodSignatures);
  }

  void markShouldMapInvocationType() {
    shouldMapInvocationType = true;
  }

  @Override
  protected Invoke.Type mapInvocationType(
      DexMethod newMethod, DexMethod originalMethod, Invoke.Type type) {
    if (shouldMapInvocationType && methodMap.get(originalMethod) == newMethod) {
      assert type == Invoke.Type.VIRTUAL || type == Invoke.Type.DIRECT;
      return Invoke.Type.STATIC;
    }
    return super.mapInvocationType(newMethod, originalMethod, type);
  }
}
