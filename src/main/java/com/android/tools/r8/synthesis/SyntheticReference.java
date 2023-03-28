// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import java.util.function.Function;

/**
 * Base type for a reference to a synthetic item.
 *
 * <p>This class is internal to the synthetic items collection, thus package-protected.
 */
abstract class SyntheticReference<
    R extends SyntheticReference<R, D, C>,
    D extends SyntheticDefinition<R, D, C>,
    C extends DexClass> {

  private final SyntheticKind kind;
  private final SynthesizingContext rewrittenContext;

  SyntheticReference(SyntheticKind kind, SynthesizingContext context) {
    assert kind != null;
    assert context != null;
    this.kind = kind;
    this.rewrittenContext = context;
  }

  abstract D lookupDefinition(Function<DexType, DexClass> definitions);

  final SyntheticKind getKind() {
    return kind;
  }

  final SynthesizingContext getContext() {
    return rewrittenContext;
  }

  abstract DexType getHolder();

  abstract DexReference getReference();

  public final R rewrite(NonIdentityGraphLens lens) {
    SynthesizingContext rewrittenContext = getContext().rewrite(lens);
    return internalRewrite(rewrittenContext, lens);
  }

  abstract R internalRewrite(SynthesizingContext rewrittenContext, NonIdentityGraphLens lens);
}
