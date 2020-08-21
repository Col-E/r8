// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

public abstract class DexEncodedMember<D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
    extends DexDefinition {

  public DexEncodedMember(DexAnnotationSet annotations) {
    super(annotations);
  }

  public DexType holder() {
    return toReference().holder;
  }

  @Override
  public abstract R toReference();

  @Override
  public boolean isDexEncodedMember() {
    return true;
  }

  @Override
  public DexEncodedMember<D, R> asDexEncodedMember() {
    return this;
  }

  public abstract ProgramMember<D, R> asProgramMember(DexDefinitionSupplier definitions);

  @Override
  public final boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    return other.getClass() == getClass()
        && ((DexEncodedMember<?, ?>) other).toReference().equals(toReference());
  }

  @Override
  public final int hashCode() {
    return toReference().hashCode();
  }
}
