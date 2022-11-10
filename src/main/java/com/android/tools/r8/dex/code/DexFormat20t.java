// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import java.nio.ShortBuffer;

abstract class DexFormat20t extends DexBase2Format {

  public /* offset */ short AAAA;

  // øø | op | +AAAA
  DexFormat20t(int high, BytecodeStream stream) {
    super(stream);
    AAAA = readSigned16BitValue(stream);
  }

  protected DexFormat20t(int AAAA) {
    assert Short.MIN_VALUE <= AAAA && AAAA <= Short.MAX_VALUE;
    this.AAAA = (short) AAAA;
  }

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    writeFirst(0, dest);
    write16BitValue(AAAA, dest);
  }

  @Override
  public final int hashCode() {
    return AAAA ^ getClass().hashCode();
  }

  @Override
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return visitor.visitInt(AAAA, ((DexFormat20t) other).AAAA);
  }

  @Override
  final void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visitInt(AAAA);
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return formatString("" + AAAA + " " + formatRelativeOffset(AAAA));
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    return formatSmaliString(":label_" + (getOffset() + AAAA));
  }

  @Override
  public void collectIndexedItems(
      AppView<?> appView,
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      LensCodeRewriterUtils rewriter) {
    // No references.
  }
}
