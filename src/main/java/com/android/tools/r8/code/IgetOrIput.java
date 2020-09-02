// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.DexField;

public abstract class IgetOrIput extends Format22c<DexField> {

  IgetOrIput(int high, BytecodeStream stream, DexField[] map) {
    super(high, stream, map);
  }

  IgetOrIput(int A, int B, DexField CCCC) {
    super(A, B, CCCC);
  }

  @Override
  public final void collectIndexedItems(IndexedItemCollection indexedItems) {
    getField().collectIndexedItems(indexedItems);
  }

  @Override
  public final DexField getField() {
    return CCCC;
  }
}
