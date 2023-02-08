// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import static com.android.tools.r8.dex.Constants.U16BIT_MAX;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.nio.ShortBuffer;

abstract class DexFormat32x extends DexBase3Format {

  public final int AAAA;
  public final int BBBB;

  private static void specify(StructuralSpecification<DexFormat32x, ?> spec) {
    spec.withInt(i -> i.AAAA).withInt(i -> i.BBBB);
  }

  // øø | op | AAAA | BBBB
  DexFormat32x(int high, BytecodeStream stream) {
    super(stream);
    AAAA = read16BitValue(stream);
    BBBB = read16BitValue(stream);
  }

  DexFormat32x(int dest, int src) {
    assert 0 <= dest && dest <= U16BIT_MAX;
    assert 0 <= src && src <= U16BIT_MAX;
    AAAA = dest;
    BBBB = src;
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
    write16BitValue(AAAA, dest);
    write16BitValue(BBBB, dest);
  }

  @Override
  public final int hashCode() {
    return ((AAAA << 16) | BBBB) ^ getClass().hashCode();
  }

  @Override
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return visitor.visit(this, (DexFormat32x) other, DexFormat32x::specify);
  }

  @Override
  final void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, DexFormat32x::specify);
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return formatString("v" + AAAA + ", v" + BBBB);
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    return formatSmaliString("v" + AAAA + ", v" + BBBB);
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
