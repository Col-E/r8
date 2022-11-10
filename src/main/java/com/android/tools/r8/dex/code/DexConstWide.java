// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.WideConstant;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.StringUtils;

public class DexConstWide extends DexFormat51l implements WideConstant {

  public static final int OPCODE = 0x18;
  public static final String NAME = "ConstWide";
  public static final String SMALI_NAME = "const-wide";

  DexConstWide(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexConstWide(int dest, long constant) {
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
  public long decodedValue() {
    return BBBBBBBBBBBBBBBB;
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return formatString(
        "v" + AA + ", " + StringUtils.hexString(decodedValue(), 16) + " (" + decodedValue() + ")");
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    return formatSmaliString(
        "v" + AA + ", " + StringUtils.hexString(decodedValue(), 16) + "L  # " + decodedValue());
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addConst(TypeElement.getWide(), AA, decodedValue());
  }
}
