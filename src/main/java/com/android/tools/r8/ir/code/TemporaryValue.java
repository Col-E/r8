// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;

public class TemporaryValue extends Value {

  private final Instruction owner;

  private int maxRegister = Constants.U16BIT_MAX;

  TemporaryValue(Instruction owner, int number, TypeLatticeElement typeLattice) {
    super(number, typeLattice);
    assert owner.requiresTemporaryRegisters();
    this.owner = owner;
  }

  public Instruction getOwner() {
    return owner;
  }

  public int maxRegister() {
    return maxRegister;
  }

  public TemporaryValue setMaxRegister(int value) {
    maxRegister = value;
    return this;
  }

  @Override
  public boolean isConstant() {
    return false;
  }

  @Override
  public boolean isTemporary() {
    return true;
  }

  @Override
  public TemporaryValue asTemporary() {
    return this;
  }
}
