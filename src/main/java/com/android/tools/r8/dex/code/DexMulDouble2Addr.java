// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class DexMulDouble2Addr extends DexFormat12x {

  public static final int OPCODE = 0xcd;
  public static final String NAME = "MulDouble2Addr";
  public static final String SMALI_NAME = "mul-double/2addr";

  DexMulDouble2Addr(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexMulDouble2Addr(int left, int right) {
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
    builder.addMul(NumericType.DOUBLE, A, A, B);
  }
}
