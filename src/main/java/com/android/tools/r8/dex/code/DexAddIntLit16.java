// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class DexAddIntLit16 extends DexFormat22s {

  public static final int OPCODE = 0xd0;
  public static final String NAME = "AddIntLit16";
  public static final String SMALI_NAME = "add-int/lit16";

  DexAddIntLit16(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexAddIntLit16(int dest, int register, int constant) {
    super(dest, register, constant);
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
    builder.addAddLiteral(NumericType.INT, A, B, CCCC);
  }
}
