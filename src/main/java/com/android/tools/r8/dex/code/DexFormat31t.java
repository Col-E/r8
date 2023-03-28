// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.nio.ShortBuffer;

public abstract class DexFormat31t extends DexBase3Format {

  public final short AA;
  protected /* offset */ int BBBBBBBB;

  private static void specify(StructuralSpecification<DexFormat31t, ?> spec) {
    spec.withInt(i -> i.AA).withInt(i -> i.BBBBBBBB);
  }

  // vAA | op | +BBBBlo | +BBBBhi
  DexFormat31t(int high, BytecodeStream stream) {
    super(stream);
    AA = (short) high;
    BBBBBBBB = readSigned32BitValue(stream);
  }

  DexFormat31t(int register, int payloadOffset) {
    assert 0 <= register && register <= Constants.U8BIT_MAX;
    AA = (short) register;
    BBBBBBBB = payloadOffset;
  }

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      GraphLens codeLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    writeFirst(AA, dest);
    assert (getOffset() + BBBBBBBB) % 2 == 0;
    write32BitValue(BBBBBBBB, dest);
  }

  @Override
  public boolean hasPayload() {
    return true;
  }

  @Override
  public int getPayloadOffset() {
    return BBBBBBBB;
  }

  public void setPayloadOffset(int offset) {
    BBBBBBBB = offset;
  }

  @Override
  public final int hashCode() {
    return ((BBBBBBBB << 8) | AA) ^ getClass().hashCode();
  }

  @Override
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return visitor.visit(this, (DexFormat31t) other, DexFormat31t::specify);
  }

  @Override
  void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, DexFormat31t::specify);
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return formatString("v" + AA + ", " + formatRelativeOffset(BBBBBBBB));
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
