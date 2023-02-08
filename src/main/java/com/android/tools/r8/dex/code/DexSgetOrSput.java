// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.nio.ShortBuffer;

abstract class DexSgetOrSput extends DexFormat21c<DexField> {

  DexSgetOrSput(int high, BytecodeStream stream, DexField[] map) {
    super(high, stream, map);
  }

  protected DexSgetOrSput(int AA, DexField BBBB) {
    super(AA, BBBB);
  }

  @Override
  public final void collectIndexedItems(
      AppView<?> appView,
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      LensCodeRewriterUtils rewriter) {
    DexField rewritten = appView.graphLens().lookupField(getField());
    rewritten.collectIndexedItems(appView, indexedItems);
  }

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      GraphLens codeLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    DexField rewritten = graphLens.lookupField(getField(), codeLens);
    writeFirst(AA, dest);
    write16BitReference(rewritten, dest, mapping);
  }

  @Override
  public final DexField getField() {
    return BBBB;
  }

  @Override
  void internalSubSpecify(StructuralSpecification<DexFormat21c<DexField>, ?> spec) {
    spec.withItem(i -> i.BBBB);
  }
}
