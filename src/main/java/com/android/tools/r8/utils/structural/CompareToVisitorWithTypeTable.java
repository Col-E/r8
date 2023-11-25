// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.NamingLens;
import java.util.function.ToIntFunction;

public class CompareToVisitorWithTypeTable extends CompareToVisitorWithStringTable {

  private final ToIntFunction<DexType> typeTable;

  public CompareToVisitorWithTypeTable(
      NamingLens namingLens,
      ToIntFunction<DexString> stringTable,
      ToIntFunction<DexType> typeTable) {
    super(namingLens, stringTable);
    this.typeTable = typeTable;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public int visitDexType(DexType type1, DexType type2) {
    if (type1 == type2) {
      return 0;
    }
    return visitInt(typeTable.applyAsInt(type1), typeTable.applyAsInt(type2));
  }
}
