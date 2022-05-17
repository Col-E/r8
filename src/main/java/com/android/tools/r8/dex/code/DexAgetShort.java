// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class DexAgetShort extends DexFormat23x {

  public static final int OPCODE = 0x4a;
  public static final String NAME = "AgetShort";
  public static final String SMALI_NAME = "aget-short";

  DexAgetShort(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexAgetShort(int AA, int BB, int CC) {
    super(AA, BB, CC);
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
    builder.addArrayGet(MemberType.SHORT, AA, BB, CC);
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
