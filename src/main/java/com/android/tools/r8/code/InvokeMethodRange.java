// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.DexMethod;

public abstract class InvokeMethodRange extends Format3rc<DexMethod> {

  InvokeMethodRange(int high, BytecodeStream stream, DexMethod[] map) {
    super(high, stream, map);
  }

  InvokeMethodRange(int firstArgumentRegister, int argumentCount, DexMethod dexItem) {
    super(firstArgumentRegister, argumentCount, dexItem);
  }

  @Override
  public final void collectIndexedItems(IndexedItemCollection indexedItems) {
    getMethod().collectIndexedItems(indexedItems);
  }

  @Override
  public final DexMethod getMethod() {
    return BBBB;
  }
}
