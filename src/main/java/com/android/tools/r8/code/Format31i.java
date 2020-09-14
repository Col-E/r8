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

abstract class Format31i extends Base3Format {

  public final short AA;
  public final int BBBBBBBB;

  // vAA | op | #+BBBBlo | #+BBBBhi
  /*package*/ Format31i(int high, BytecodeStream stream) {
    super(stream);
    AA = (short) high;
    BBBBBBBB = readSigned32BitValue(stream);
  }

  /*package*/ Format31i(int AA, int BBBBBBBB) {
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
  final int internalCompareTo(Instruction other) {
    Format31i o = (Format31i) other;
    return ComparatorUtils.compareInts(AA, o.AA, BBBBBBBB, o.BBBBBBBB);
  }

  @Override
  public String toString(ClassNameMapper naming) {
    return formatString("v" + AA + ", #" + BBBBBBBB);
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
