// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

public class DexClassAndMethod extends DexClassAndMember<DexEncodedMethod, DexMethod>
    implements LookupTarget {

  DexClassAndMethod(DexClass holder, DexEncodedMethod method) {
    super(holder, method);
    assert holder.isProgramClass() == (this instanceof ProgramMethod);
  }

  public static ProgramMethod asProgramMethodOrNull(DexClassAndMethod method) {
    return method != null ? method.asProgramMethod() : null;
  }

  public static DexClassAndMethod create(DexClass holder, DexEncodedMethod method) {
    if (holder.isProgramClass()) {
      return new ProgramMethod(holder.asProgramClass(), method);
    } else if (holder.isLibraryClass()) {
      return new LibraryMethod(holder.asLibraryClass(), method);
    } else {
      assert holder.isClasspathClass();
      return new ClasspathMethod(holder.asClasspathClass(), method);
    }
  }

  @Override
  public MethodAccessFlags getAccessFlags() {
    return getDefinition().getAccessFlags();
  }

  @Override
  public boolean isMethodTarget() {
    return true;
  }

  @Override
  public DexClassAndMethod asMethodTarget() {
    return this;
  }

  public boolean isClasspathMethod() {
    return false;
  }

  public ClasspathMethod asClasspathMethod() {
    return null;
  }

  public boolean isLibraryMethod() {
    return false;
  }

  public LibraryMethod asLibraryMethod() {
    return null;
  }

  public boolean isProgramMethod() {
    return false;
  }

  public ProgramMethod asProgramMethod() {
    return null;
  }
}
