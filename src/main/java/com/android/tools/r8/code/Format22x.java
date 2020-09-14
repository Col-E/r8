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

abstract class Format22x extends Base2Format {

  public final short AA;
  public final char BBBB;

  // AA | op | vBBBB
  Format22x(int high, BytecodeStream stream) {
    super(stream);
    AA = (short) high;
    BBBB = read16BitValue(stream);
  }

  Format22x(int dest, int src) {
    assert 0 <= dest && dest <= Constants.U8BIT_MAX;
    assert 0 <= src && src <= Constants.U16BIT_MAX;
    AA = (short) dest;
    BBBB = (char) src;
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
  final int internalCompareTo(Instruction other) {
    Format22x o = (Format22x) other;
    return ComparatorUtils.compareInts(AA, o.AA, BBBB, o.BBBB);
  }


  @Override
  public String toString(ClassNameMapper naming) {
    return formatString("v" + AA + ", v" + (int)BBBB);
  }

  @Override
  public String toSmaliString(ClassNameMapper naming) {
    return formatSmaliString("v" + AA + ", v" + (int)BBBB);
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
