// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.structural.HasherWrapper;
import com.android.tools.r8.utils.structural.RepresentativeMap;

/**
 * Definition of a synthetic classpath class.
 *
 * <p>This class is internal to the synthetic items collection, thus package-protected.
 */
class SyntheticClasspathClassDefinition
    extends SyntheticClassDefinition<
        SyntheticClasspathClassReference, SyntheticClasspathClassDefinition, DexClasspathClass>
    implements SyntheticClasspathDefinition {

  SyntheticClasspathClassDefinition(
      SyntheticKind kind, SynthesizingContext context, DexClasspathClass clazz) {
    super(kind, context, clazz);
  }

  @Override
  public boolean isClasspathDefinition() {
    return true;
  }

  @Override
  public SyntheticClasspathDefinition asClasspathDefinition() {
    return this;
  }

  @Override
  SyntheticClasspathClassReference toReference() {
    return new SyntheticClasspathClassReference(getKind(), getContext(), clazz.getType());
  }

  @Override
  public boolean isValid() {
    return clazz.isPublic() && clazz.isFinal() && clazz.accessFlags.isSynthetic();
  }

  @Override
  void internalComputeHash(HasherWrapper hasher, RepresentativeMap map) {
    clazz.hashWithTypeEquivalence(hasher, map);
  }

  @Override
  int internalCompareTo(SyntheticClasspathClassDefinition o, RepresentativeMap map) {
    return clazz.compareWithTypeEquivalenceTo(o.clazz, map);
  }

  @Override
  public String toString() {
    return "SyntheticClasspathClass{ clazz = "
        + clazz.type.toSourceString()
        + ", kind = "
        + getKind()
        + ", context = "
        + getContext()
        + " }";
  }
}
