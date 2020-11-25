// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.nio.ShortBuffer;

abstract class Format21h extends Base2Format {

  public final short AA;
  public final char BBBB;

  private static void specify(StructuralSpecification<Format21h, ?> spec) {
    spec.withInt(i -> i.AA).withInt(i -> i.BBBB);
  }

  // AA | op | BBBB0000[00000000]
  /*package*/ Format21h(int high, BytecodeStream stream) {
    super(stream);
    AA = (short) high;
    BBBB = read16BitValue(stream);
  }

  /*package*/ Format21h(int AA, int BBBB) {
    assert 0 <= AA && AA <= Constants.U8BIT_MAX;
    assert 0 <= BBBB && BBBB <= Constants.U16BIT_MAX;
    this.AA = (short) AA;
    this.BBBB = (char) BBBB;
  }

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    writeFirst(AA, dest);
    write16BitValue(BBBB, dest);
  }

  @Override
  public final int hashCode() {
    return ((BBBB << 8) | AA) ^ getClass().hashCode();
  }

  @Override
  final int internalAcceptCompareTo(Instruction other, CompareToVisitor visitor) {
    return visitor.visit(this, (Format21h) other, Format21h::specify);
  }

  @Override
  public void collectIndexedItems(
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      GraphLens graphLens,
      LensCodeRewriterUtils rewriter) {
    // No references.
  }
}
