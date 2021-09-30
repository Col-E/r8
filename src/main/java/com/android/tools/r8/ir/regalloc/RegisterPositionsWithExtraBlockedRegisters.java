// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.regalloc;

import java.util.BitSet;

public class RegisterPositionsWithExtraBlockedRegisters extends RegisterPositions {

  private final RegisterPositions positions;
  private final BitSet extraBlockedRegisters;

  public RegisterPositionsWithExtraBlockedRegisters(RegisterPositions positions) {
    this.positions = positions;
    this.extraBlockedRegisters = new BitSet(positions.getLimit());
  }

  @Override
  public boolean hasType(int index, Type type) {
    assert !isBlockedTemporarily(index);
    return positions.hasType(index, type);
  }

  @Override
  public void set(int index, int value, LiveIntervals intervals) {
    positions.set(index, value, intervals);
  }

  @Override
  public int get(int index) {
    assert !isBlockedTemporarily(index);
    return positions.get(index);
  }

  @Override
  public int getLimit() {
    return positions.getLimit();
  }

  @Override
  public void setBlocked(int index) {
    positions.setBlocked(index);
  }

  public void setBlockedTemporarily(int index) {
    extraBlockedRegisters.set(index);
  }

  @Override
  public boolean isBlocked(int index) {
    return positions.isBlocked(index) || isBlockedTemporarily(index);
  }

  public boolean isBlockedTemporarily(int index) {
    return extraBlockedRegisters.get(index);
  }
}
