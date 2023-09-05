// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Reference to a synthetic method item.
 *
 * <p>This class is internal to the synthetic items collection, thus package-protected.
 */
class SyntheticMethodReference
    extends SyntheticReference<SyntheticMethodReference, SyntheticMethodDefinition, DexProgramClass>
    implements SyntheticProgramReference, Rewritable<SyntheticMethodReference> {
  final DexMethod method;

  SyntheticMethodReference(SyntheticKind kind, SynthesizingContext context, DexMethod method) {
    super(kind, context);
    this.method = method;
  }

  @Override
  public DexType getHolder() {
    return method.holder;
  }

  @Override
  DexMethod getReference() {
    return method;
  }

  @Override
  SyntheticMethodDefinition lookupDefinition(Function<DexType, DexClass> definitions) {
    DexClass clazz = definitions.apply(method.holder);
    if (clazz == null) {
      return null;
    }
    assert clazz.isProgramClass();
    ProgramMethod definition = clazz.asProgramClass().lookupProgramMethod(method);
    return definition != null
        ? new SyntheticMethodDefinition(getKind(), getContext(), definition)
        : null;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  SyntheticMethodReference internalRewrite(
      SynthesizingContext rewrittenContext, NonIdentityGraphLens lens) {
    DexMethod rewritten = lens.getRenamedMethodSignature(method);
    // If the reference has been non-trivially rewritten the compiler has changed it and it can no
    // longer be considered a synthetic. The context may or may not have changed.
    if (method.getHolderType() != rewritten.getHolderType()
        && !lens.isSimpleRenaming(method, rewritten)) {
      // If the referenced item is rewritten, it should be moved to a non-internal synthetic holder
      // as the synthetic holder is no longer part of the synthetic collection.
      assert SyntheticNaming.verifyNotInternalSynthetic(rewritten.holder);
      return null;
    }
    if (rewrittenContext == getContext() && rewritten == method) {
      return this;
    }
    return new SyntheticMethodReference(getKind(), rewrittenContext, rewritten);
  }

  @Override
  public void apply(
      Consumer<SyntheticMethodReference> onMethod,
      Consumer<SyntheticProgramClassReference> onClass) {
    onMethod.accept(this);
  }
}
