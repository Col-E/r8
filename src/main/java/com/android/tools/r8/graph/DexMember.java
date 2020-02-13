// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

public abstract class DexMember<T extends DexEncodedMember<T, S>, S extends DexMember<T, S>>
    extends DexReference implements PresortedComparable<S> {

  public final DexType holder;

  public DexMember(DexType holder) {
    this.holder = holder;
  }

  public abstract boolean match(S entry);

  public abstract boolean match(T entry);

  @Override
  public boolean isDexMember() {
    return true;
  }

  @Override
  public DexMember<T, S> asDexMember() {
    return this;
  }
}
