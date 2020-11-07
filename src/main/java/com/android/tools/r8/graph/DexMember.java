// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.google.common.collect.Iterables;

public abstract class DexMember<D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
    extends DexReference implements PresortedComparable<R> {

  public final DexType holder;
  public final DexString name;

  public DexMember(DexType holder, DexString name) {
    assert holder != null;
    this.holder = holder;
    assert name != null;
    this.name = name;
  }

  public abstract DexEncodedMember<?, ?> lookupOnClass(DexClass clazz);

  public abstract ProgramMember<?, ?> lookupOnProgramClass(DexProgramClass clazz);

  public abstract boolean match(R entry);

  public abstract boolean match(D entry);

  @Override
  public DexType getContextType() {
    return holder;
  }

  @Override
  public boolean isDexMember() {
    return true;
  }

  @Override
  public DexMember<D, R> asDexMember() {
    return this;
  }

  public abstract Iterable<DexType> getReferencedTypes();

  public Iterable<DexType> getReferencedBaseTypes(DexItemFactory dexItemFactory) {
    return Iterables.transform(getReferencedTypes(), type -> type.toBaseType(dexItemFactory));
  }
}
