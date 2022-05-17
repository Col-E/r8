// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.conversion.IRBuilder;

public class DexGoto32 extends DexFormat30t {

  public static final int OPCODE = 0x2a;
  public static final String NAME = "Goto32";
  public static final String SMALI_NAME = "goto/32";

  DexGoto32(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexGoto32(int AAAAAAAA) {
    super(AAAAAAAA);
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
  public int[] getTargets() {
    return new int[] {AAAAAAAA};
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addGoto(getOffset() + AAAAAAAA);
  }
}
