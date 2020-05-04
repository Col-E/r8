// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.origin.Origin;

public class DexClassAndMethod implements LookupTarget {

  private final DexClass holder;
  private final DexEncodedMethod method;

  DexClassAndMethod(DexClass holder, DexEncodedMethod method) {
    assert holder != null;
    assert method != null;
    assert holder.type == method.holder();
    assert holder.isProgramClass() == (this instanceof ProgramMethod);
    this.holder = holder;
    this.method = method;
  }

  public static DexClassAndMethod create(DexClass holder, DexEncodedMethod method) {
    if (holder.isProgramClass()) {
      return new ProgramMethod(holder.asProgramClass(), method);
    } else if (holder.isLibraryClass()) {
      return new DexClassAndMethod(holder, method);
    } else {
      assert holder.isClasspathClass();
      return new ClasspathMethod(holder.asClasspathClass(), method);
    }
  }

  @Override
  public boolean equals(Object object) {
    throw new Unreachable("Unsupported attempt at comparing Class and DexClassAndMethod");
  }

  @Override
  public int hashCode() {
    throw new Unreachable("Unsupported attempt at computing the hashcode of DexClassAndMethod");
  }

  @Override
  public boolean isMethodTarget() {
    return true;
  }

  @Override
  public DexClassAndMethod asMethodTarget() {
    return this;
  }

  public DexClass getHolder() {
    return holder;
  }

  public DexType getHolderType() {
    return holder.type;
  }

  public DexEncodedMethod getDefinition() {
    return method;
  }

  public DexMethod getReference() {
    return method.method;
  }

  public Origin getOrigin() {
    return holder.origin;
  }

  public boolean isClasspathMethod() {
    return false;
  }

  public ClasspathMethod asClasspathMethod() {
    return null;
  }

  public boolean isProgramMethod() {
    return false;
  }

  public ProgramMethod asProgramMethod() {
    return null;
  }

  public String toSourceString() {
    return method.method.toSourceString();
  }

  @Override
  public String toString() {
    return toSourceString();
  }
}
