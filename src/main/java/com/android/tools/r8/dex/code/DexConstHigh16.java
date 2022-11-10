// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.SingleConstant;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.StringUtils;

public class DexConstHigh16 extends DexFormat21h implements SingleConstant {

  public static final int OPCODE = 0x15;
  public static final String NAME = "ConstHigh16";
  public static final String SMALI_NAME = "const/high16";

  /*package*/ DexConstHigh16(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexConstHigh16(int register, int constantHighBits) {
    super(register, constantHighBits);
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
    return BBBB << 16;
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
