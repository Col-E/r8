// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

public class ProgramField extends DexClassAndField {

  public ProgramField(DexProgramClass holder, DexEncodedField field) {
    super(holder, field);
  }

  public boolean isStructurallyEqualTo(ProgramField other) {
    return getDefinition() == other.getDefinition() && getHolder() == other.getHolder();
  }

  @Override
  public boolean isProgramField() {
    return true;
  }

  @Override
  public ProgramField asProgramField() {
    return this;
  }

  @Override
  public DexProgramClass getHolder() {
    DexClass holder = super.getHolder();
    assert holder.isProgramClass();
    return holder.asProgramClass();
  }
}
