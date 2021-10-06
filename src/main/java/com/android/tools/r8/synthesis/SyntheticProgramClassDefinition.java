// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.structural.HasherWrapper;
import com.android.tools.r8.utils.structural.RepresentativeMap;
import java.util.function.Consumer;

/**
 * Definition of a synthetic class item.
 *
 * <p>This class is internal to the synthetic items collection, thus package-protected.
 */
class SyntheticProgramClassDefinition
    extends SyntheticClassDefinition<
        SyntheticProgramClassReference, SyntheticProgramClassDefinition, DexProgramClass>
    implements SyntheticProgramDefinition {

  SyntheticProgramClassDefinition(
      SyntheticKind kind, SynthesizingContext context, DexProgramClass clazz) {
    super(kind, context, clazz);
  }

  @Override
  public void apply(
      Consumer<SyntheticMethodDefinition> onMethod,
      Consumer<SyntheticProgramClassDefinition> onClass) {
    onClass.accept(this);
  }

  @Override
  public boolean isProgramDefinition() {
    return true;
  }

  @Override
  public SyntheticProgramDefinition asProgramDefinition() {
    return this;
  }

  @Override
  SyntheticProgramClassReference toReference() {
    return new SyntheticProgramClassReference(getKind(), getContext(), clazz.getType());
  }

  @Override
  public boolean isValid() {
    return clazz.isPublic()
        && clazz.accessFlags.isSynthetic()
        && (clazz.isFinal() || clazz.isAbstract());
  }

  @Override
  void internalComputeHash(HasherWrapper hasher, RepresentativeMap map) {
    clazz.hashWithTypeEquivalence(hasher, map);
  }

  @Override
  int internalCompareTo(SyntheticProgramClassDefinition o, RepresentativeMap map) {
    return clazz.compareWithTypeEquivalenceTo(o.clazz, map);
  }

  @Override
  public String toString() {
    return "SyntheticProgramClass{ clazz = "
        + clazz.type.toSourceString()
        + ", kind = "
        + getKind()
        + ", context = "
        + getContext()
        + " }";
  }
}
