// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.errors.Unreachable;

public enum IfType {
  EQ,
  GE,
  GT,
  LE,
  LT,
  NE;

  public boolean isEqualsOrNotEquals() {
    return this == EQ || this == NE;
  }

  // Returns the comparison type if the operands are swapped.
  public IfType forSwappedOperands() {
    switch (this) {
      case EQ:
      case NE:
        return this;
      case GE:
        return IfType.LE;
      case GT:
        return IfType.LT;
      case LE:
        return IfType.GE;
      case LT:
        return IfType.GT;
      default:
        throw new Unreachable("Unknown if condition type.");
    }
  }

  public IfType inverted() {
    switch (this) {
      case EQ:
        return IfType.NE;
      case GE:
        return IfType.LT;
      case GT:
        return IfType.LE;
      case LE:
        return IfType.GT;
      case LT:
        return IfType.GE;
      case NE:
        return IfType.EQ;
      default:
        throw new Unreachable("Unknown if condition type.");
    }
  }
}
