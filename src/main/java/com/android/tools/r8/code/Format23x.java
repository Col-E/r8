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
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.utils.ComparatorUtils;
import java.nio.ShortBuffer;

abstract class Format23x extends Base2Format {

  public final short AA;
  public final short BB;
  public final short CC;

  // vAA | op | vCC | vBB
  Format23x(int high, BytecodeStream stream) {
    super(stream);
    AA = (short) high;
    CC = read8BitValue(stream);
    BB = read8BitValue(stream);
  }

  Format23x(int AA, int BB, int CC) {
    assert 0 <= AA && AA <= Constants.U8BIT_MAX;
    assert 0 <= BB && BB <= Constants.U8BIT_MAX;
    assert 0 <= CC && CC <= Constants.U8BIT_MAX;
    this.AA = (short) AA;
    this.BB = (short) BB;
    this.CC = (short) CC;
  }

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    writeFirst(AA, dest);
    write16BitValue(combineBytes(CC, BB), dest);
  }

  @Override
  public final int hashCode() {
    return ((AA << 16) | (BB << 8) | CC) ^ getClass().hashCode();
  }

  @Override
  final int internalCompareTo(Instruction other) {
    Format23x o = (Format23x) other;
    return ComparatorUtils.compareInts(AA, o.AA, BB, o.BB, CC, o.CC);
  }

  @Override
  public String toString(ClassNameMapper naming) {
    return formatString("v" + AA + ", v" + BB + ", v" + CC);
  }

  @Override
  public String toSmaliString(ClassNameMapper naming) {
    return formatSmaliString("v" + AA + ", v" + BB + ", v" + CC);
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
