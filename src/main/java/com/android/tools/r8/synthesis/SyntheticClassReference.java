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
 * Reference to a synthetic class item.
 *
 * <p>This class is internal to the synthetic items collection, thus package-protected.
 */
class SyntheticClassReference
    extends SyntheticReference<SyntheticClassReference, SyntheticClassDefinition> {
  final DexType type;

  SyntheticClassReference(SyntheticKind kind, SynthesizingContext context, DexType type) {
    super(kind, context);
    this.type = type;
  }

  @Override
  DexType getHolder() {
    return type;
  }

  @Override
  SyntheticClassDefinition lookupDefinition(Function<DexType, DexClass> definitions) {
    DexClass clazz = definitions.apply(type);
    if (clazz == null) {
      return null;
    }
    assert clazz.isProgramClass();
    return new SyntheticClassDefinition(getKind(), getContext(), clazz.asProgramClass());
  }

  @Override
  SyntheticClassReference rewrite(NonIdentityGraphLens lens) {
    DexType rewritten = lens.lookupType(type);
    // If the reference has been non-trivially rewritten the compiler has changed it and it can no
    // longer be considered a synthetic. The context may or may not have changed.
    if (type != rewritten && !lens.isSimpleRenaming(type, rewritten)) {
      // If the referenced item is rewritten, it should be moved to another holder as the
      // synthetic holder is no longer part of the synthetic collection.
      assert SyntheticNaming.verifyNotInternalSynthetic(rewritten);
      return null;
    }
    SynthesizingContext context = getContext().rewrite(lens);
    if (context == getContext() && rewritten == type) {
      return this;
    }
    // Ensure that if a synthetic moves its context moves consistently.
    if (type != rewritten) {
      context =
          SynthesizingContext.fromSyntheticContextChange(rewritten, context, lens.dexItemFactory());
      if (context == null) {
        return null;
      }
    }
    return new SyntheticClassReference(getKind(), context, rewritten);
  }

  @Override
  void apply(
      Consumer<SyntheticMethodReference> onMethod, Consumer<SyntheticClassReference> onClass) {
    onClass.accept(this);
  }
}
