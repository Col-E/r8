// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Base type for a reference to a synthetic item.
 *
 * <p>This class is internal to the synthetic items collection, thus package-protected.
 */
abstract class SyntheticReference<
    R extends SyntheticReference<R, D>, D extends SyntheticDefinition<R, D>> {

  private final SyntheticKind kind;
  private final SynthesizingContext context;

  SyntheticReference(SyntheticKind kind, SynthesizingContext context) {
    assert kind != null;
    assert context != null;
    this.kind = kind;
    this.context = context;
  }

  abstract D lookupDefinition(Function<DexType, DexClass> definitions);

  final SyntheticKind getKind() {
    return kind;
  }

  final SynthesizingContext getContext() {
    return context;
  }

  abstract DexType getHolder();

  abstract R rewrite(NonIdentityGraphLens lens);

  abstract void apply(
      Consumer<SyntheticMethodReference> onMethod, Consumer<SyntheticClassReference> onClass);
}
