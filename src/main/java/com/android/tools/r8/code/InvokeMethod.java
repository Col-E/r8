// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.DexMethod;

public abstract class InvokeMethod extends Format35c<DexMethod> {

  InvokeMethod(int high, BytecodeStream stream, DexMethod[] map) {
    super(high, stream, map);
  }

  InvokeMethod(int A, DexMethod BBBB, int C, int D, int E, int F, int G) {
    super(A, BBBB, C, D, E, F, G);
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
