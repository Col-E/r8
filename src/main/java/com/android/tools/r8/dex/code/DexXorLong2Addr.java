// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class DexXorLong2Addr extends DexFormat12x {

  public static final int OPCODE = 0xc2;
  public static final String NAME = "XorLong2Addr";
  public static final String SMALI_NAME = "xor-long/2addr";

  DexXorLong2Addr(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexXorLong2Addr(int left, int right) {
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
    builder.addXor(NumericType.LONG, A, A, B);
  }
}
