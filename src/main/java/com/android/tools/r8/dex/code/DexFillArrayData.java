// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.utils.RetracerForCodePrinting;

public class DexFillArrayData extends DexFormat31t {

  public static final int OPCODE = 0x26;
  public static final String NAME = "FillArrayData";
  public static final String SMALI_NAME = "fill-array-data";

  DexFillArrayData(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexFillArrayData(int value) {
    super(value, -1);
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
    builder.resolveAndBuildNewArrayFilledData(AA, getOffset() + getPayloadOffset());
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    return formatSmaliString("v" + AA + ", :label_" + (getOffset() + BBBBBBBB));
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
