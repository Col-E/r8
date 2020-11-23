// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import com.android.tools.r8.utils.structural.StructuralItem.CompareToAccept;

/**
 * Default implementation for defining compareTo on a structural type.
 *
 * <p>Internally this is using CompareToVisitorWithTypeEquivalence with the identity map, but should
 * not be assumed to have that implementation.
 */
public class DefaultCompareToVisitor {

  public static <T> int run(T item1, T item2, StructuralMapping<T> visit) {
    return run(item1, item2, (i1, i2, visitor) -> visitor.visit(i1, i2, visit));
  }

  public static <T> int run(T item1, T item2, CompareToAccept<T> compareToAccept) {
    return CompareToVisitorWithTypeEquivalence.run(item1, item2, t -> t, compareToAccept);
  }
}
