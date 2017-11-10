// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.errors.Unreachable;
import java.util.Arrays;

public class DexItemBasedString extends DexString {
  public final IndexedDexItem basedOn;

  DexItemBasedString(DexType basedOn) {
    super(basedOn.toString());
    this.basedOn = basedOn;
  }

  DexItemBasedString(DexField basedOn) {
    super(basedOn.name.toString());
    this.basedOn = basedOn;
  }

  DexItemBasedString(DexMethod basedOn) {
    super(basedOn.name.toString());
    this.basedOn = basedOn;
  }

  @Override
  public boolean computeEquals(Object other) {
    if (other instanceof DexItemBasedString) {
      DexItemBasedString o = (DexItemBasedString) other;
      return basedOn == o.basedOn && size == o.size && Arrays.equals(content, o.content);
    }
    return false;
  }

  @Override
  public int computeHashCode() {
    return super.computeHashCode() + 7 * basedOn.hashCode();
  }

  @Override
  public void collectIndexedItems(IndexedItemCollection indexedItems) {
    // This instance should not exist when collecting indexed items.
    // {@link IdentifierMinifier} will replace this with an appropriate {@link DexString}.
    throw new Unreachable("Remaining DexItemBasedString: " + this.toString());
  }
}
