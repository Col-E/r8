// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.PrunedItems;
import java.util.LinkedHashSet;

public class NonEmptyStartupOrder extends StartupOrder {

  private final LinkedHashSet<DexType> startupClasses;

  NonEmptyStartupOrder(LinkedHashSet<DexType> startupClasses) {
    assert !startupClasses.isEmpty();
    this.startupClasses = startupClasses;
  }

  @Override
  public StartupOrder rewrittenWithLens(GraphLens graphLens) {
    LinkedHashSet<DexType> rewrittenStartupClasses = new LinkedHashSet<>(startupClasses.size());
    for (DexType startupClass : startupClasses) {
      DexType rewrittenStartupClass = graphLens.lookupType(startupClass);
      rewrittenStartupClasses.add(rewrittenStartupClass);
    }
    return createNonEmpty(rewrittenStartupClasses);
  }

  @Override
  public StartupOrder withoutPrunedItems(PrunedItems prunedItems) {
    LinkedHashSet<DexType> rewrittenStartupClasses = new LinkedHashSet<>(startupClasses.size());
    for (DexType startupClass : startupClasses) {
      if (!prunedItems.isRemoved(startupClass)) {
        rewrittenStartupClasses.add(startupClass);
      }
    }
    return createNonEmpty(rewrittenStartupClasses);
  }

  private StartupOrder createNonEmpty(LinkedHashSet<DexType> startupClasses) {
    if (startupClasses.isEmpty()) {
      assert false;
      return empty();
    }
    return new NonEmptyStartupOrder(startupClasses);
  }
}
