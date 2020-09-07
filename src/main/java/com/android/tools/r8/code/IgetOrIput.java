// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import java.nio.ShortBuffer;

public abstract class IgetOrIput extends Format22c<DexField> {

  IgetOrIput(int high, BytecodeStream stream, DexField[] map) {
    super(high, stream, map);
  }

  IgetOrIput(int A, int B, DexField CCCC) {
    super(A, B, CCCC);
  }

  @Override
  public final void collectIndexedItems(
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      GraphLens graphLens,
      LensCodeRewriterUtils rewriter) {
    DexField rewritten = graphLens.lookupField(getField());
    rewritten.collectIndexedItems(indexedItems);
  }

  @Override
  public final DexField getField() {
    return CCCC;
  }

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    DexField lookup = graphLens.lookupField(getField());
    writeFirst(B, A, dest);
    write16BitReference(lookup, dest, mapping);
  }
}
