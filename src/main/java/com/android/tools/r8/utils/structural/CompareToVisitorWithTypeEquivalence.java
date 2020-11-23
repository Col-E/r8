// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.structural.StructuralItem.CompareToAccept;

public class CompareToVisitorWithTypeEquivalence extends CompareToVisitorBase {

  public static <T> int run(T item1, T item2, RepresentativeMap map, StructuralAccept<T> visit) {
    return run(item1, item2, map, (i1, i2, visitor) -> visitor.visit(i1, i2, visit));
  }

  public static <T> int run(
      T item1, T item2, RepresentativeMap map, CompareToAccept<T> compareToAccept) {
    CompareToVisitorWithTypeEquivalence state = new CompareToVisitorWithTypeEquivalence(map);
    return compareToAccept.acceptCompareTo(item1, item2, state);
  }

  private final RepresentativeMap representatives;

  public CompareToVisitorWithTypeEquivalence(RepresentativeMap representatives) {
    this.representatives = representatives;
  }

  @Override
  public int visitDexType(DexType type1, DexType type2) {
    DexType repr1 = representatives.getRepresentative(type1);
    DexType repr2 = representatives.getRepresentative(type2);
    return repr1.getDescriptor().acceptCompareTo(repr2.getDescriptor(), this);
  }
}
