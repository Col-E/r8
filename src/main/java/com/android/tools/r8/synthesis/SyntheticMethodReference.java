// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.function.Function;

/**
 * Reference to a synthetic method item.
 *
 * <p>This class is internal to the synthetic items collection, thus package-protected.
 */
class SyntheticMethodReference extends SyntheticReference {
  final DexMethod method;

  SyntheticMethodReference(SynthesizingContext context, DexMethod method) {
    super(context);
    this.method = method;
  }

  @Override
  DexReference getReference() {
    return method;
  }

  @Override
  DexType getHolder() {
    return method.holder;
  }

  @Override
  SyntheticDefinition lookupDefinition(Function<DexType, DexClass> definitions) {
    DexClass clazz = definitions.apply(method.holder);
    if (clazz == null) {
      return null;
    }
    assert clazz.isProgramClass();
    ProgramMethod definition = clazz.asProgramClass().lookupProgramMethod(method);
    return definition != null ? new SyntheticMethodDefinition(getContext(), definition) : null;
  }

  @Override
  SyntheticReference rewrite(NonIdentityGraphLens lens) {
    DexMethod rewritten = lens.lookupMethod(method);
    // If the reference has been non-trivially rewritten the compiler has changed it and it can no
    // longer be considered a synthetic. The context may or may not have changed.
    if (method != rewritten && !lens.isSimpleRenaming(method.holder, rewritten.holder)) {
      // If the referenced item is rewritten, it should be moved to another holder as the
      // synthetic holder is no longer part of the synthetic collection.
      assert method.holder != rewritten.holder;
      assert SyntheticItems.verifyNotInternalSynthetic(rewritten.holder);
      return null;
    }
    SynthesizingContext context = getContext().rewrite(lens);
    return context == getContext() && rewritten == method
        ? this
        : new SyntheticMethodReference(context, rewritten);
  }
}
