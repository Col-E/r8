// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.IndexedDexItem;
import com.android.tools.r8.naming.ClassNameMapper;
import java.util.function.BiPredicate;

abstract class Format21c<T extends IndexedDexItem> extends Base2Format {

  public final short AA;
  public T BBBB;

  // AA | op | [type|field|string]@BBBB
  Format21c(int high, BytecodeStream stream, T[] map) {
    super(stream);
    AA = (short) high;
    BBBB = map[read16BitValue(stream)];
  }

  protected Format21c(int AA, T BBBB) {
    assert 0 <= AA && AA <= Constants.U8BIT_MAX;
    this.AA = (short) AA;
    this.BBBB = BBBB;
  }

  @Override
  public final int hashCode() {
    return ((BBBB.hashCode() << 8) | AA) ^ getClass().hashCode();
  }

  @Override
  final int internalCompareTo(Instruction other) {
    Format21c<?> o = (Format21c<?>) other;
    int aaDiff = Short.compare(AA, o.AA);
    return aaDiff != 0 ? aaDiff : internalCompareBBBB(o);
  }

  abstract int internalCompareBBBB(Format21c<?> other);

  @Override
  public String toString(ClassNameMapper naming) {
    return formatString(
        "v" + AA + ", " + (naming == null ? BBBB.toString() : naming.originalNameOf(BBBB)));
  }

  @Override
  public String toSmaliString(ClassNameMapper naming) {
    // TODO(sgjesse): Add support for smali name mapping.
    return formatSmaliString("v" + AA + ", " + BBBB.toSmaliString());
  }

  @Override
  public boolean equals(Instruction other, BiPredicate<IndexedDexItem, IndexedDexItem> equality) {
    if (other == null || this.getClass() != other.getClass()) {
      return false;
    }
    Format21c<?> o = (Format21c<?>) other;
    return o.AA == AA && equality.test(BBBB, o.BBBB);
  }
}
