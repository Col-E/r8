// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.structural.CompareToVisitorWithNamingLens;
import com.android.tools.r8.utils.structural.StructuralItem;

public interface NamingLensComparable<T extends NamingLensComparable<T>> extends StructuralItem<T> {

  default int compareToWithNamingLens(T other, NamingLens lens) {
    return CompareToVisitorWithNamingLens.run(self(), other, lens, StructuralItem::acceptCompareTo);
  }
}
