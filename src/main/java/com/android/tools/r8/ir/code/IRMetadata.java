// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

public class IRMetadata {

  private long first;
  private long second;

  public IRMetadata() {}

  private IRMetadata(long first, long second) {
    this.first = first;
    this.second = second;
  }

  public static IRMetadata unknown() {
    return new IRMetadata(0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL);
  }

  private boolean get(int bit) {
    long masked;
    if (bit < 64) {
      masked = first & (1L << bit);
    } else {
      assert bit < 128;
      int adjusted = bit - 64;
      masked = second & (1L << adjusted);
    }
    return masked != 0;
  }

  private void set(int bit) {
    if (bit < 64) {
      first |= (1L << bit);
    } else {
      assert bit < 128;
      int adjusted = bit - 64;
      second |= (1L << adjusted);
    }
  }

  public void record(Instruction instruction) {
    set(instruction.opcode());
  }

  public void merge(IRMetadata metadata) {
    first |= metadata.first;
    second |= metadata.second;
  }

  public boolean mayHaveConstString() {
    return get(Opcodes.CONST_STRING);
  }

  public boolean mayHaveDebugPosition() {
    return get(Opcodes.DEBUG_POSITION);
  }

  public boolean mayHaveDexItemBasedConstString() {
    return get(Opcodes.DEX_ITEM_BASED_CONST_STRING);
  }

  public boolean mayHaveMonitorInstruction() {
    return get(Opcodes.MONITOR);
  }

  public boolean mayHaveStringSwitch() {
    return get(Opcodes.STRING_SWITCH);
  }
}
