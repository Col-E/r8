// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.structural.RepresentativeMap;
import com.google.common.hash.Hasher;

/**
 * Definition of a synthetic class item.
 *
 * <p>This class is internal to the synthetic items collection, thus package-protected.
 */
class SyntheticClassDefinition
    extends SyntheticDefinition<SyntheticClassReference, SyntheticClassDefinition> {

  private final DexProgramClass clazz;

  SyntheticClassDefinition(SyntheticKind kind, SynthesizingContext context, DexProgramClass clazz) {
    super(kind, context);
    this.clazz = clazz;
  }

  public DexProgramClass getProgramClass() {
    return clazz;
  }

  @Override
  SyntheticClassReference toReference() {
    return new SyntheticClassReference(getKind(), getContext(), clazz.getType());
  }

  @Override
  DexProgramClass getHolder() {
    return clazz;
  }

  @Override
  public boolean isValid() {
    return clazz.isPublic() && clazz.isFinal() && clazz.accessFlags.isSynthetic();
  }

  @Override
  void internalComputeHash(Hasher hasher, RepresentativeMap map) {
    clazz.hashWithTypeEquivalence(hasher, map);
  }

  @Override
  int internalCompareTo(SyntheticClassDefinition o, RepresentativeMap map) {
    return clazz.compareWithTypeEquivalenceTo(o.clazz, map);
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
