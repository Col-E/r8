// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.ValueTypeConstraint;

public class DexIfNez extends DexFormat21t {

  public static final int OPCODE = 0x39;
  public static final String NAME = "IfNez";
  public static final String SMALI_NAME = "if-nez";

  DexIfNez(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexIfNez(int register, int offset) {
    super(register, offset);
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
  public IfType getType() {
    return IfType.NE;
  }

  @Override
  protected ValueTypeConstraint getOperandTypeConstraint() {
    return ValueTypeConstraint.INT_OR_FLOAT_OR_OBJECT;
  }
}
