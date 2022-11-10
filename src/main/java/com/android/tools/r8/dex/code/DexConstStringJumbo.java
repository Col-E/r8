// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.OffsetToObjectMapping;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.utils.RetracerForCodePrinting;

public class DexConstStringJumbo extends DexFormat31c {

  public static final int OPCODE = 0x1b;
  public static final String NAME = "ConstStringJumbo";
  public static final String SMALI_NAME = "const-string/jumbo";

  DexConstStringJumbo(int high, BytecodeStream stream, OffsetToObjectMapping mapping) {
    super(high, stream, mapping.getStringMap());
  }

  public DexConstStringJumbo(int register, DexString string) {
    super(register, string);
  }

  public DexString getString() {
    return BBBBBBBB;
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
  public DexConstStringJumbo asConstStringJumbo() {
    return this;
  }

  @Override
  public boolean isConstStringJumbo() {
    return true;
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return formatString("v" + AA + ", \"" + BBBBBBBB.toString() + "\"");
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    return formatSmaliString("v" + AA + ", \"" + BBBBBBBB.toString() + "\"");
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addConstString(AA, BBBBBBBB);
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
