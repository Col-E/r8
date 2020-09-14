// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.IndexedDexItem;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.utils.ComparatorUtils;
import java.util.function.BiPredicate;

public abstract class Format22c<T extends DexReference> extends Base2Format {

  public final byte A;
  public final byte B;
  public T CCCC;

  // vB | vA | op | [type|field]@CCCC
  /*package*/ Format22c(int high, BytecodeStream stream, T[] map) {
    super(stream);
    A = (byte) (high & 0xf);
    B = (byte) ((high >> 4) & 0xf);
    CCCC = map[read16BitValue(stream)];
  }

  /*package*/ Format22c(int A, int B, T CCCC) {
    assert 0 <= A && A <= Constants.U4BIT_MAX;
    assert 0 <= B && B <= Constants.U4BIT_MAX;
    this.A = (byte) A;
    this.B = (byte) B;
    this.CCCC = CCCC;
  }

  @Override
  public final int hashCode() {
    return ((CCCC.hashCode() << 8) | (A << 4) | B) ^ getClass().hashCode();
  }

  @Override
  final int internalCompareTo(Instruction other) {
    Format22c<? extends DexReference> o = (Format22c<? extends DexReference>) other;
    int diff = ComparatorUtils.compareInts(A, o.A, B, o.B);
    return diff != 0 ? diff : CCCC.referenceCompareTo(o.CCCC);
  }

  @Override
  public String toString(ClassNameMapper naming) {
    return formatString(
        "v" + A + ", v" + B + ", " + (naming == null ? CCCC : naming.originalNameOf(CCCC)));
  }

  @Override
  public String toSmaliString(ClassNameMapper naming) {
    // TODO(sgjesse): Add support for smali name mapping.
    return formatSmaliString("v" + A + ", v" + B + ", " + CCCC.toSmaliString());
  }

  @Override
  public boolean equals(Instruction other, BiPredicate<IndexedDexItem, IndexedDexItem> equality) {
    if (other == null || this.getClass() != other.getClass()) {
      return false;
    }
    Format22c<?> o = (Format22c<?>) other;
    return o.A == A && o.B == B && equality.test(CCCC, o.CCCC);
  }
}
