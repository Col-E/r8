// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.errors.Unreachable;

public class DexClassAndMethod {

  private final DexClass holder;
  private final DexEncodedMethod method;

  DexClassAndMethod(DexClass holder, DexEncodedMethod method) {
    assert holder.type == method.method.holder;
    this.holder = holder;
    this.method = method;
  }

  public static DexClassAndMethod create(DexClass holder, DexEncodedMethod method) {
    if (holder.isProgramClass()) {
      return new ProgramMethod(holder.asProgramClass(), method);
    } else {
      return new DexClassAndMethod(holder, method);
    }
  }

  @Override
  public boolean equals(Object obj) {
    throw new Unreachable("Unsupported attempt at comparing Class and DexClassAndMethod");
  }

  @Override
  public int hashCode() {
    throw new Unreachable("Unsupported attempt at computing the hashcode of DexClassAndMethod");
  }

  public DexClass getHolder() {
    return holder;
  }

  public DexEncodedMethod getMethod() {
    return method;
  }

  public boolean isProgramMethod() {
    return false;
  }

  public ProgramMethod asProgramMethod() {
    return null;
  }
}
