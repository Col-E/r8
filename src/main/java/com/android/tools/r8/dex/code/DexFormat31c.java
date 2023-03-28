// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import static com.android.tools.r8.dex.Constants.U8BIT_MAX;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.IndexedDexItem;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.nio.ShortBuffer;
import java.util.function.BiPredicate;

abstract class DexFormat31c extends DexBase3Format {

  public final short AA;
  public DexString BBBBBBBB;

  private static void specify(StructuralSpecification<DexFormat31c, ?> spec) {
    spec.withInt(i -> i.AA).withItem(i -> i.BBBBBBBB);
  }

  // vAA | op | string@BBBBlo | string@#+BBBBhi
  DexFormat31c(int high, BytecodeStream stream, DexString[] map) {
    super(stream);
    AA = (short) high;
    BBBBBBBB = map[(int) read32BitValue(stream)];
  }

  DexFormat31c(int AA, DexString BBBBBBBB) {
    assert 0 <= AA && AA <= U8BIT_MAX;
    this.AA = (short) AA;
    this.BBBBBBBB = BBBBBBBB;
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
    write32BitReference(BBBBBBBB, dest, mapping);
  }

  @Override
  public final int hashCode() {
    return ((BBBBBBBB.hashCode() << 8) | AA) ^ getClass().hashCode();
  }

  @Override
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return visitor.visit(this, (DexFormat31c) other, DexFormat31c::specify);
  }

  @Override
  final void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, DexFormat31c::specify);
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return formatString("v" + AA + ", " + retracer.toDescriptor(BBBBBBBB));
  }

  @Override
  public void collectIndexedItems(
      AppView<?> appView,
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      LensCodeRewriterUtils rewriter) {
    BBBBBBBB.collectIndexedItems(indexedItems);
  }

  @Override
  public boolean equals(
      DexInstruction other, BiPredicate<IndexedDexItem, IndexedDexItem> equality) {
    if (other == null || (this.getClass() != other.getClass())) {
      return false;
    }
    DexFormat31c o = (DexFormat31c) other;
    return o.AA == AA && equality.test(BBBBBBBB, o.BBBBBBBB);
  }
}
