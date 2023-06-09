// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Sets;
import java.util.Set;

public class ProguardCompatibilityActions {

  private final Set<DexType> compatInstantiatedTypes;

  private ProguardCompatibilityActions(Set<DexType> compatInstantiatedTypes) {
    this.compatInstantiatedTypes = compatInstantiatedTypes;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isCompatInstantiated(DexProgramClass clazz) {
    return compatInstantiatedTypes.contains(clazz.getType());
  }

  public boolean isEmpty() {
    return compatInstantiatedTypes.isEmpty();
  }

  public ProguardCompatibilityActions withoutPrunedItems(PrunedItems prunedItems) {
    Builder builder = builder();
    for (DexType compatInstantiatedType : compatInstantiatedTypes) {
      if (!prunedItems.isRemoved(compatInstantiatedType)) {
        builder.addCompatInstantiatedType(compatInstantiatedType);
      }
    }
    return builder.build();
  }

  public ProguardCompatibilityActions rewrittenWithLens(GraphLens lens, Timing timing) {
    return timing.time("Rewrite ProguardCompatibilityActions", () -> rewrittenWithLens(lens));
  }

  private ProguardCompatibilityActions rewrittenWithLens(GraphLens lens) {
    Builder builder = builder();
    for (DexType compatInstantiatedType : compatInstantiatedTypes) {
      builder.addCompatInstantiatedType(lens.lookupType(compatInstantiatedType));
    }
    return builder.build();
  }

  public static class Builder {

    private final Set<DexType> compatInstantiatedTypes = Sets.newIdentityHashSet();

    public void addCompatInstantiatedType(DexProgramClass clazz) {
      addCompatInstantiatedType(clazz.getType());
    }

    private void addCompatInstantiatedType(DexType type) {
      compatInstantiatedTypes.add(type);
    }

    public ProguardCompatibilityActions build() {
      return new ProguardCompatibilityActions(compatInstantiatedTypes);
    }
  }
}
