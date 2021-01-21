// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;

/**
 * Definition of a synthetic class item.
 *
 * <p>This class is internal to the synthetic items collection, thus package-protected.
 */
public abstract class SyntheticClassDefinition<
        R extends SyntheticClassReference<R, D, C>,
        D extends SyntheticClassDefinition<R, D, C>,
        C extends DexClass>
    extends SyntheticDefinition<R, D, C> {

  final C clazz;

  SyntheticClassDefinition(SyntheticKind kind, SynthesizingContext context, C clazz) {
    super(kind, context);
    this.clazz = clazz;
  }

  @Override
  public final C getHolder() {
    return clazz;
  }

  @Override
  public boolean isValid() {
    return clazz.isPublic() && clazz.isFinal() && clazz.accessFlags.isSynthetic();
  }

  @Override
  public String toString() {
    return "SyntheticClass{ clazz = "
        + clazz.type.toSourceString()
        + ", kind = "
        + getKind()
        + ", context = "
        + getContext()
        + " }";
  }
}
