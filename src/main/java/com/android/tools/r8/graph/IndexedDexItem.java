// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.MixedSectionCollection;

/** Subset of dex items that are referenced by some table index. */
public abstract class IndexedDexItem extends CachedHashValueDexItem {

  @Override
  void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    // Should never be visited.
    assert false;
  }

  public abstract int getOffset(ObjectToOffsetMapping mapping);

  // Partial implementation of PresortedComparable.

  @Override
  public void flushCachedValues() {
    super.flushCachedValues();
  }
}
