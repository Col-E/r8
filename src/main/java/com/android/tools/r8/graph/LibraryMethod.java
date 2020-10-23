// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

/** Type representing a method definition from the library and its holder. */
public final class LibraryMethod extends DexClassAndMethod {

  public LibraryMethod(DexLibraryClass holder, DexEncodedMethod method) {
    super(holder, method);
  }

  @Override
  public boolean isLibraryMethod() {
    return true;
  }

  @Override
  public LibraryMethod asLibraryMethod() {
    return this;
  }

  @Override
  public DexLibraryClass getHolder() {
    DexClass holder = super.getHolder();
    assert holder.isLibraryClass();
    return holder.asLibraryClass();
  }
}
