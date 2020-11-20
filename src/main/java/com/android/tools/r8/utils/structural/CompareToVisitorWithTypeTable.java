// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.NamingLensComparable;
import com.android.tools.r8.naming.NamingLens;
import java.util.function.ToIntFunction;

public class CompareToVisitorWithTypeTable extends CompareToVisitorWithStringTable {

  public static <T extends NamingLensComparable<T>> int run(
      T item1,
      T item2,
      NamingLens namingLens,
      ToIntFunction<DexString> stringTable,
      ToIntFunction<DexType> typeTable) {
    CompareToVisitorWithNamingLens state =
        new CompareToVisitorWithTypeTable(namingLens, stringTable, typeTable);
    item1.acceptCompareTo(item2, state);
    return state.getOrder();
  }

  private final ToIntFunction<DexType> typeTable;

  public CompareToVisitorWithTypeTable(
      NamingLens namingLens,
      ToIntFunction<DexString> stringTable,
      ToIntFunction<DexType> typeTable) {
    super(namingLens, stringTable);
    this.typeTable = typeTable;
  }

  @Override
  public void visitDexType(DexType type1, DexType type2) {
    if (stillEqual()) {
      visitInt(typeTable.applyAsInt(type1), typeTable.applyAsInt(type2));
    }
  }
}
