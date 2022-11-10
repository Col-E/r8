// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.dex.Constants;
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

abstract class DexFormat31i extends DexBase3Format {

  public final short AA;
  public final int BBBBBBBB;

  private static void specify(StructuralSpecification<DexFormat31i, ?> spec) {
    spec.withInt(i -> i.AA).withInt(i -> i.BBBBBBBB);
  }

  // vAA | op | #+BBBBlo | #+BBBBhi
  /*package*/ DexFormat31i(int high, BytecodeStream stream) {
    super(stream);
    AA = (short) high;
    BBBBBBBB = readSigned32BitValue(stream);
  }

  /*package*/ DexFormat31i(int AA, int BBBBBBBB) {
    assert 0 <= AA && AA <= Constants.U8BIT_MAX;
    this.AA = (short) AA;
    this.BBBBBBBB = BBBBBBBB;
  }

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    writeFirst(AA, dest);
    write32BitValue(BBBBBBBB, dest);
  }

  @Override
  public final int hashCode() {
    return ((BBBBBBBB << 8) | AA) ^ getClass().hashCode();
  }

  @Override
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return visitor.visit(this, (DexFormat31i) other, DexFormat31i::specify);
  }

  @Override
  void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, DexFormat31i::specify);
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return formatString("v" + AA + ", #" + BBBBBBBB);
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
