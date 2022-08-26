// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.experimental.startup.profile.StartupItem;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.synthesis.SyntheticItems;
import java.util.Collection;
import java.util.Collections;

public class EmptyStartupOrder extends StartupOrder {

  EmptyStartupOrder() {}

  @Override
  public boolean contains(DexMethod method, SyntheticItems syntheticItems) {
    return false;
  }

  @Override
  public boolean contains(DexType type, SyntheticItems syntheticItems) {
    return false;
  }

  @Override
  public Collection<StartupItem> getItems() {
    return Collections.emptyList();
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public EmptyStartupOrder rewrittenWithLens(GraphLens graphLens) {
    return this;
  }

  @Override
  public StartupOrder toStartupOrderForWriting(AppView<?> appView) {
    return this;
  }

  @Override
  public EmptyStartupOrder withoutPrunedItems(
      PrunedItems prunedItems, SyntheticItems syntheticItems) {
    return this;
  }
}
