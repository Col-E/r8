// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.origin.Origin;

/**
 * A synthesizing context is the input type and origin that gives rise to a synthetic item.
 *
 * <p>Note that a context can only itself be a synthetic item if it was provided as an input that
 * was marked as synthetic already, in which case it is its own context. In other words,
 *
 * <pre>
 *   for any synthetic item, I:
 *     context(I) == holder(I)  iff  I is a synthetic input
 * </pre>
 *
 * <p>This class is internal to the synthetic items collection, thus package-protected.
 */
class SynthesizingContext {
  final DexType type;
  final Origin origin;

  SynthesizingContext(DexType type, Origin origin) {
    this.type = type;
    this.origin = origin;
  }

  SynthesizingContext rewrite(NonIdentityGraphLens lens) {
    DexType rewritten = lens.lookupType(type);
    return rewritten == type ? this : new SynthesizingContext(type, origin);
  }
}
