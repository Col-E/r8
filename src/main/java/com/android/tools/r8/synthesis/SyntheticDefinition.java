// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexProgramClass;
import com.google.common.hash.HashCode;

/**
 * Base type for the definition of a synthetic item.
 *
 * <p>This class is internal to the synthetic items collection, thus package-protected.
 */
abstract class SyntheticDefinition {
  private final SynthesizingContext context;

  SyntheticDefinition(SynthesizingContext context) {
    this.context = context;
  }

  abstract SyntheticReference toReference();

  SynthesizingContext getContext() {
    return context;
  }

  abstract DexProgramClass getHolder();

  abstract HashCode computeHash(boolean intermediate);

  abstract boolean isEquivalentTo(SyntheticDefinition other, boolean intermediate);
}
