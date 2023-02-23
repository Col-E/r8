// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.MethodLookupResult;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import java.nio.ShortBuffer;

public abstract class DexInvokeMethod extends DexFormat35c<DexMethod> {

  DexInvokeMethod(int high, BytecodeStream stream, DexMethod[] map) {
    super(high, stream, map);
  }

  DexInvokeMethod(int A, DexMethod BBBB, int C, int D, int E, int F, int G) {
    super(A, BBBB, C, D, E, F, G);
  }

  @Override
  public final void collectIndexedItems(
      AppView<?> appView,
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      LensCodeRewriterUtils rewriter) {
    DexMethod rewritten =
        appView
            .graphLens()
            .lookupMethod(getMethod(), context.getReference(), getInvokeType())
            .getReference();
    rewritten.collectIndexedItems(appView, indexedItems);
  }

  @Override
  public final DexMethod getMethod() {
    return BBBB;
  }

  public abstract InvokeType getInvokeType();

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      GraphLens codeLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    MethodLookupResult lookup =
        graphLens.lookupMethod(getMethod(), context.getReference(), getInvokeType());
    writeFirst(A, G, dest, lookup.getType().getDexOpcode());
    write16BitReference(lookup.getReference(), dest, mapping);
    write16BitValue(combineBytes(makeByte(F, E), makeByte(D, C)), dest);
  }
}
