// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Reference to a synthetic class item.
 *
 * <p>This class is internal to the synthetic items collection, thus package-protected.
 */
class SyntheticProgramClassReference
    extends SyntheticClassReference<
        SyntheticProgramClassReference, SyntheticProgramClassDefinition, DexProgramClass>
    implements SyntheticProgramReference, Rewritable<SyntheticProgramClassReference> {

  SyntheticProgramClassReference(SyntheticKind kind, SynthesizingContext context, DexType type) {
    super(kind, context, type);
  }

  @Override
  SyntheticProgramClassDefinition lookupDefinition(Function<DexType, DexClass> definitions) {
    DexClass clazz = definitions.apply(type);
    if (clazz == null) {
      return null;
    }
    assert clazz.isProgramClass();
    return new SyntheticProgramClassDefinition(getKind(), getContext(), clazz.asProgramClass());
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  SyntheticProgramClassReference internalRewrite(
      SynthesizingContext rewrittenContext, NonIdentityGraphLens lens) {
    DexType rewritten = lens.lookupType(type);
    // If the reference has been non-trivially rewritten the compiler has changed it and it can no
    // longer be considered a synthetic. The context may or may not have changed.
    if (type != rewritten && !lens.isSimpleRenaming(type, rewritten)) {
      return null;
    }
    if (rewrittenContext == getContext() && rewritten == type) {
      return this;
    }
    return new SyntheticProgramClassReference(getKind(), rewrittenContext, rewritten);
  }

  @Override
  public void apply(
      Consumer<SyntheticMethodReference> onMethod,
      Consumer<SyntheticProgramClassReference> onClass) {
    onClass.accept(this);
  }
}
