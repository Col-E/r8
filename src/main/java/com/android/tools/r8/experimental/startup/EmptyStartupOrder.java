// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.PrunedItems;

public class EmptyStartupOrder extends StartupOrder {

  EmptyStartupOrder() {}

  @Override
  public EmptyStartupOrder rewrittenWithLens(GraphLens graphLens) {
    return this;
  }

  @Override
  public EmptyStartupOrder withoutPrunedItems(PrunedItems prunedItems) {
    return this;
  }
}
