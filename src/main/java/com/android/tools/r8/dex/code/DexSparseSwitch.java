// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.utils.RetracerForCodePrinting;

public class DexSparseSwitch extends DexFormat31t {

  public static final int OPCODE = 0x2c;
  public static final String NAME = "SparseSwitch";
  public static final String SMALI_NAME = "sparse-switch";

  DexSparseSwitch(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexSparseSwitch(int value) {
    super(value, -1);
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public int getOpcode() {
    return OPCODE;
  }

  @Override
  public String getSmaliName() {
    return SMALI_NAME;
  }

  @Override
  public boolean isIntSwitch() {
    return true;
  }

  @Override
  public void buildIR(IRBuilder builder) {
    int offset = getOffset();
    int payloadOffset = offset + getPayloadOffset();
    int fallthroughOffset = offset + getSize();
    builder.resolveAndBuildSwitch(AA, fallthroughOffset, payloadOffset);
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    return formatSmaliString("v" + AA + ", :label_" + (getOffset() + BBBBBBBB));
  }
}
