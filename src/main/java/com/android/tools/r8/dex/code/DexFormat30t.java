// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import java.nio.ShortBuffer;

abstract class DexFormat30t extends DexBase3Format {

  public /* offset */ int AAAAAAAA;

  // øø | op | AAAAlo | AAAAhi
  DexFormat30t(int high, BytecodeStream stream) {
    super(stream);
    AAAAAAAA = readSigned32BitValue(stream);
  }

  protected DexFormat30t(int AAAAAAAA) {
    this.AAAAAAAA = AAAAAAAA;
  }

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      GraphLens codeLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    writeFirst(0, dest);
    write32BitValue(AAAAAAAA, dest);
  }

  @Override
  public final int hashCode() {
    return AAAAAAAA ^ getClass().hashCode();
  }

  @Override
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return visitor.visitInt(AAAAAAAA, ((DexFormat30t) other).AAAAAAAA);
  }

  @Override
  final void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visitInt(AAAAAAAA);
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return formatString(formatOffset(AAAAAAAA));
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    return formatSmaliString(":label_" + (getOffset() + AAAAAAAA));
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
