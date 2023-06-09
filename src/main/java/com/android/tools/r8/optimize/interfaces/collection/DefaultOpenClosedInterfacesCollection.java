// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.collection;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.utils.Timing;

/** Default oracle for that answers "maybe open" for each interface. */
public class DefaultOpenClosedInterfacesCollection extends OpenClosedInterfacesCollection {

  private static final DefaultOpenClosedInterfacesCollection INSTANCE =
      new DefaultOpenClosedInterfacesCollection();

  private DefaultOpenClosedInterfacesCollection() {}

  static DefaultOpenClosedInterfacesCollection getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isDefinitelyClosed(DexClass clazz) {
    return false;
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public OpenClosedInterfacesCollection rewrittenWithLens(GraphLens graphLens, Timing timing) {
    return this;
  }

  @Override
  public OpenClosedInterfacesCollection withoutPrunedItems(PrunedItems prunedItems) {
    return this;
  }
}
