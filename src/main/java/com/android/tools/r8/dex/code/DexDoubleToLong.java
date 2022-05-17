// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class DexDoubleToLong extends DexFormat12x {

  public static final int OPCODE = 0x8b;
  public static final String NAME = "DoubleToLong";
  public static final String SMALI_NAME = "double-to-long";

  DexDoubleToLong(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexDoubleToLong(int dest, int source) {
    super(dest, source);
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
    builder.addConversion(NumericType.LONG, NumericType.DOUBLE, A, B);
  }
}
