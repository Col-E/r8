// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import java.util.function.Function;

/**
 * Reference to a synthetic class item.
 *
 * <p>This class is internal to the synthetic items collection, thus package-protected.
 */
class SyntheticClasspathClassReference
    extends SyntheticClassReference<
        SyntheticClasspathClassReference, SyntheticClasspathClassDefinition, DexClasspathClass> {

  SyntheticClasspathClassReference(SyntheticKind kind, SynthesizingContext context, DexType type) {
    super(kind, context, type);
  }

  @Override
  SyntheticClasspathClassDefinition lookupDefinition(Function<DexType, DexClass> definitions) {
    DexClass clazz = definitions.apply(type);
    if (clazz == null) {
      return null;
    }
    assert clazz.isClasspathClass();
    return new SyntheticClasspathClassDefinition(getKind(), getContext(), clazz.asClasspathClass());
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  SyntheticClasspathClassReference internalRewrite(
      SynthesizingContext rewrittenContext, NonIdentityGraphLens lens) {
    assert type == lens.lookupType(type)
        : "Unexpected classpath rewrite of type " + type.toSourceString();
    assert getContext() == rewrittenContext
        : "Unexpected classpath rewrite of context type " + getContext();
    return this;
  }
}
