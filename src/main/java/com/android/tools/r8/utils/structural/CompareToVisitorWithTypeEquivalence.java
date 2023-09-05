// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.structural.StructuralItem.CompareToAccept;

public class CompareToVisitorWithTypeEquivalence extends CompareToVisitorBase {

  public static <T> int run(T item1, T item2, RepresentativeMap map, StructuralMapping<T> visit) {
    return run(item1, item2, map, (i1, i2, visitor) -> visitor.visit(i1, i2, visit));
  }

  public static <T> int run(
      T item1, T item2, RepresentativeMap map, CompareToAccept<T> compareToAccept) {
    if (item1 == item2) {
      return 0;
    }
    CompareToVisitorWithTypeEquivalence state = new CompareToVisitorWithTypeEquivalence(map);
    return compareToAccept.acceptCompareTo(item1, item2, state);
  }

  private final RepresentativeMap representatives;

  public CompareToVisitorWithTypeEquivalence(RepresentativeMap representatives) {
    this.representatives = representatives;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public int visitDexType(DexType type1, DexType type2) {
    if (type1 == type2) {
      return 0;
    }
    DexType repr1 = representatives.getRepresentative(type1);
    DexType repr2 = representatives.getRepresentative(type2);
    return debug(repr1.getDescriptor().acceptCompareTo(repr2.getDescriptor(), this));
  }
}
