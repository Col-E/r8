// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;

/**
 * Reference to a synthetic class item.
 *
 * <p>This class is internal to the synthetic items collection, thus package-protected.
 */
abstract class SyntheticClassReference<
        R extends SyntheticClassReference<R, D, C>,
        D extends SyntheticClassDefinition<R, D, C>,
        C extends DexClass>
    extends SyntheticReference<R, D, C> {

  final DexType type;

  SyntheticClassReference(SyntheticKind kind, SynthesizingContext context, DexType type) {
    super(kind, context);
    this.type = type;
  }

  @Override
  DexType getHolder() {
    return type;
  }
}
