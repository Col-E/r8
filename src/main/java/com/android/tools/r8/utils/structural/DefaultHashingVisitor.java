// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import com.android.tools.r8.utils.structural.StructuralItem.HashingAccept;

/**
 * Default visitor for hashing a structural item.
 *
 * <p>Internally this is using HashingVisitorWithTypeEquivalence with the identity map, but should
 * not be assumed to have that implementation.
 */
public class DefaultHashingVisitor {

  public static <T> void run(T item, HasherWrapper hasher, StructuralMapping<T> accept) {
    run(item, hasher, (i, visitor) -> visitor.visit(i, accept));
  }

  public static <T> void run(T item, HasherWrapper hasher, HashingAccept<T> hashingAccept) {
    HashingVisitorWithTypeEquivalence.run(item, hasher, t -> t, hashingAccept);
  }
}
