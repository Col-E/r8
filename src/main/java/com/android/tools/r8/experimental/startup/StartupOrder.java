// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.InternalOptions;
import java.util.Collection;
import java.util.LinkedHashSet;

public abstract class StartupOrder {

  StartupOrder() {}

  public static StartupOrder createInitialStartupOrder(InternalOptions options) {
    if (!options.getStartupOptions().hasStartupConfiguration()) {
      return empty();
    }
    StartupConfiguration startupConfiguration =
        options.getStartupOptions().getStartupConfiguration();
    if (!startupConfiguration.hasStartupClasses()) {
      return empty();
    }
    return new NonEmptyStartupOrder(new LinkedHashSet<>(startupConfiguration.getStartupClasses()));
  }

  public static StartupOrder empty() {
    return new EmptyStartupOrder();
  }

  public abstract boolean contains(DexType type, SyntheticItems syntheticItems);

  public abstract Collection<StartupClass<DexType, DexMethod>> getClasses();

  public abstract boolean isEmpty();

  public abstract StartupOrder rewrittenWithLens(GraphLens graphLens);

  public abstract StartupOrder toStartupOrderForWriting(AppView<?> appView);

  public abstract StartupOrder withoutPrunedItems(
      PrunedItems prunedItems, SyntheticItems syntheticItems);
}
