// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

/** Type representing a method definition in the programs compilation unit and its holder. */
public final class ProgramMethod extends DexClassAndMethod {

  public ProgramMethod(DexProgramClass holder, DexEncodedMethod method) {
    super(holder, method);
  }

  @Override
  public boolean isProgramMethod() {
    return true;
  }

  @Override
  public ProgramMethod asProgramMethod() {
    return this;
  }

  @Override
  public DexProgramClass getHolder() {
    DexClass holder = super.getHolder();
    assert holder.isProgramClass();
    return holder.asProgramClass();
  }
}
