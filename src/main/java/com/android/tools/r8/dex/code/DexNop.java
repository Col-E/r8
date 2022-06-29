// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;

public class DexNop extends DexFormat10x {

  public static final int OPCODE = 0x0;
  public static final String NAME = "Nop";
  public static final String SMALI_NAME = "nop";

  DexNop(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexNop() {}

  public static DexNop create(int high, BytecodeStream stream) {
    switch (high) {
      case 0x01:
        return new DexPackedSwitchPayload(high, stream);
      case 0x02:
        return new DexSparseSwitchPayload(high, stream);
      case 0x03:
        return new DexFillArrayDataPayload(high, stream);
      default:
        return new DexNop(high, stream);
    }
  }

  // Notice that this must be overridden by the "Nop" subtypes!
  @Override
  int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return DexCompareHelper.compareIdUniquelyDeterminesEquality(this, other);
  }

  // Notice that this must be overridden by the "Nop" subtypes!
  @Override
  void internalAcceptHashing(HashingVisitor visitor) {
    // Nothing to hash besides the compare-id.
  }

  // Notice that this must be overridden by the "Nop" subtypes!
  @Override
  public int hashCode() {
    return NAME.hashCode() * 7;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getSmaliName() {
    return SMALI_NAME;
  }

  @Override
  public int getOpcode() {
    return OPCODE;
  }

  @Override
  public void buildIR(IRBuilder builder) {
    // The IR does not represent nops.
    // Nops needed by dex, eg, for tight infinite loops, will be created upon conversion to dex.
  }
}
