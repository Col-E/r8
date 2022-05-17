// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class DexRemInt2Addr extends DexFormat12x {

  public static final int OPCODE = 0xb4;
  public static final String NAME = "RemInt2Addr";
  public static final String SMALI_NAME = "rem-int/2addr";

  DexRemInt2Addr(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexRemInt2Addr(int left, int right) {
    super(left, right);
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
    builder.addRem(NumericType.INT, A, A, B);
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
