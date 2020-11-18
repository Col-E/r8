// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

/** Mapping of a specification over an item. */
// TODO(b/171867022): Rename this to StructuralMapping to avoid confusion with the Acceptor and
//  accept classes.
@FunctionalInterface
public interface StructuralAccept<T> {
  void apply(StructuralSpecification<T, ?> spec);
}
