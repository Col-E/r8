// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.google.common.collect.Iterables;
import java.util.function.Function;

public abstract class DexMember<D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
    extends DexReference implements NamingLensComparable<R> {

  @SuppressWarnings("ReferenceEquality")
  public static boolean identical(DexMember<?, ?> t1, DexMember<?, ?> t2) {
    return t1 == t2;
  }

  public final DexType holder;
  public final DexString name;

  public DexMember(DexType holder, DexString name) {
    assert holder != null;
    this.holder = holder;
    assert name != null;
    this.name = name;
  }

  public abstract <T> T apply(
      Function<DexField, T> fieldConsumer, Function<DexMethod, T> methodConsumer);

  public final boolean isDefinedOnClass(DexClass clazz) {
    return lookupOnClass(clazz) != null;
  }

  public abstract DexEncodedMember<?, ?> lookupOnClass(DexClass clazz);

  public abstract DexClassAndMember<?, ?> lookupMemberOnClass(DexClass clazz);

  public abstract ProgramMember<?, ?> lookupOnProgramClass(DexProgramClass clazz);

  public abstract boolean match(R entry);

  public abstract boolean match(D entry);

  @Override
  public DexType getContextType() {
    return holder;
  }

  public DexType getHolderType() {
    return holder;
  }

  public DexString getName() {
    return name;
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

  public abstract DexMember<D, R> withHolder(DexType holder, DexItemFactory dexItemFactory);
}
