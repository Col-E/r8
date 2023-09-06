// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.SingleConstant;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.StringUtils;

public class DexConst4 extends DexFormat11n implements SingleConstant {

  public static final int OPCODE = 0x12;
  public static final String NAME = "Const4";
  public static final String SMALI_NAME = "const/4";

  DexConst4(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexConst4(int dest, int constant) {
    super(dest, constant);
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
    return B;
  }

  @Override
  public boolean isDexConst4() {
    return true;
  }

  @Override
  public DexConst4 asDexConst4() {
    return this;
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return formatString(
        "v" + A + ", " + StringUtils.hexString(decodedValue(), 1) + " (" + decodedValue() + ")");
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    return formatSmaliString(
        "v" + A + ", " + StringUtils.hexString(decodedValue(), 2) + "  # " + decodedValue());
  }

  @Override
  public void buildIR(IRBuilder builder) {
    int value = decodedValue();
    TypeElement type = value == 0 ? TypeElement.getTop() : TypeElement.getSingle();
    builder.addConst(type, A, value);
  }
}
