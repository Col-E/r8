// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

public abstract class DexMember<D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
    extends DexReference implements PresortedComparable<R> {

  public final DexType holder;

  public DexMember(DexType holder) {
    assert holder != null;
    this.holder = holder;
  }

  public DexEncodedMember<?, ?> lookupOnClass(DexClass clazz) {
    return clazz != null ? clazz.lookupMember(this) : null;
  }

  public abstract boolean match(R entry);

  public abstract boolean match(D entry);

  @Override
  public boolean isDexMember() {
    return true;
  }

  @Override
  public DexMember<D, R> asDexMember() {
    return this;
  }
}
