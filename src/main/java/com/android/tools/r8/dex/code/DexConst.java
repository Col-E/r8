// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.SingleConstant;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.StringUtils;

public class DexConst extends DexFormat31i implements SingleConstant {

  public static final int OPCODE = 0x14;
  public static final String NAME = "Const";
  public static final String SMALI_NAME = "const";

  DexConst(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexConst(int register, int constant) {
    super(register, constant);
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
  public int decodedValue() {
    return BBBBBBBB;
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return formatString(
        "v" + AA + ", " + StringUtils.hexString(decodedValue(), 8) + " (" + decodedValue() + ")");
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    return formatSmaliString(
        "v" + AA + ", " + StringUtils.hexString(decodedValue(), 8) + "  # " + decodedValue());
  }

  @Override
  public void buildIR(IRBuilder builder) {
    int value = decodedValue();
    TypeElement type = value == 0 ? TypeElement.getTop() : TypeElement.getSingle();
    builder.addConst(type, AA, value);
  }
}
