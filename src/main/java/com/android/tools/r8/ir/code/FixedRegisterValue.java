// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.code.MoveType;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;

// Value that has a fixed register allocated. These are used for inserting spill, restore, and phi
// moves in the spilling register allocator.
public class FixedRegisterValue extends Value {
  private final int register;
  private final MoveType moveType;

  public FixedRegisterValue(TypeLatticeElement typeLattice, int register) {
    this(MoveType.fromTypeLattice(typeLattice), register);
  }

  public FixedRegisterValue(MoveType moveType, int register) {
    // Set local info to null since these values are never representatives of live-ranges.
    super(-1, createMoveTypeRepresentative(moveType), null);
    setNeedsRegister(true);
    this.register = register;
    this.moveType = moveType;
  }

  private static TypeLatticeElement createMoveTypeRepresentative(MoveType moveType) {
    switch (moveType) {
      case SINGLE:
        return TypeLatticeElement.SINGLE;
      case WIDE:
        return TypeLatticeElement.WIDE;
      case OBJECT:
        return TypeLatticeElement.NULL;
      default:
        throw new Unreachable("Unexpected move type: " + moveType);
    }
  }

  @Override
  public ValueType outType() {
    // FixedRegisterValue is a bit special as it is only confined to value widths.
    switch (moveType) {
      case SINGLE:
        return ValueType.INT_OR_FLOAT;
      case WIDE:
        return ValueType.LONG_OR_DOUBLE;
      case OBJECT:
        return ValueType.OBJECT;
    }
    throw new Unreachable("Unexpected lattice in FixedRegisterValue: " + typeLattice);
  }

  public int getRegister() {
    return register;
  }

  @Override
  public boolean isFixedRegisterValue() {
    return true;
  }

  @Override
  public FixedRegisterValue asFixedRegisterValue() {
    return this;
  }

  @Override
  public boolean isConstant() {
    return false;
  }

  @Override
  public String toString() {
    return "r" + register;
  }
}
