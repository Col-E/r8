// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

/**
 * Simple mapping from a register to an int value.
 *
 * <p>The backing for the mapping grows as needed up to a given limit. If no mapping exists for a
 * register number the value is assumed to be Integer.MAX_VALUE.
 */
public abstract class RegisterPositions {

  enum Type {
    MONITOR,
    CONST_NUMBER,
    OTHER,
    ANY
  }

  public abstract boolean hasType(int index, Type type);

  public abstract void set(int index, int value, LiveIntervals intervals);

  public abstract int get(int index);

  public abstract int getLimit();

  public abstract void setBlocked(int index);

  public abstract boolean isBlocked(int index);

  public final boolean isBlocked(int index, boolean isWide) {
    if (isBlocked(index)) {
      return true;
    }
    return isWide && isBlocked(index + 1);
  }
}
