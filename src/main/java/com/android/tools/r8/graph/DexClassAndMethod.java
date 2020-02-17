// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.errors.Unreachable;

public class DexClassAndMethod<C extends DexClass> {

  private static final DexClassAndMethod<?> NO_RESULT = new DexClassAndMethod<>(null, null);

  public final C holder;
  public final DexEncodedMethod method;

  protected DexClassAndMethod(C holder, DexEncodedMethod method) {
    assert (holder == null && method == null)
        || (holder != null && method != null && holder.type == method.method.holder);
    this.holder = holder;
    this.method = method;
  }

  public static <C extends DexClass> DexClassAndMethod<C> createResult(
      C c, DexEncodedMethod method) {
    assert c != null;
    assert method != null;
    assert c.type == method.method.holder;
    return new DexClassAndMethod<C>(c, method);
  }

  public static DexClassAndMethod<?> createNoResult() {
    return NO_RESULT;
  }

  @Override
  public boolean equals(Object obj) {
    throw new Unreachable("Unsupported attempt at comparing Class and DexClassAndMethod");
  }

  @Override
  public int hashCode() {
    throw new Unreachable("Unsupported attempt at computing the hashcode of DexClassAndMethod");
  }

  public boolean hasResult() {
    return this != NO_RESULT;
  }

  public boolean hasNoResult() {
    return this == NO_RESULT;
  }
}
