// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.naming.NamingLens;
import java.util.function.ToIntFunction;

public class CompareToVisitorWithStringTable extends CompareToVisitorWithNamingLens {

  private final ToIntFunction<DexString> stringTable;

  public CompareToVisitorWithStringTable(
      NamingLens namingLens, ToIntFunction<DexString> stringTable) {
    super(namingLens);
    this.stringTable = stringTable;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public int visitDexString(DexString string1, DexString string2) {
    if (string1 == string2) {
      return 0;
    }
    return visitInt(stringTable.applyAsInt(string1), stringTable.applyAsInt(string2));
  }
}
