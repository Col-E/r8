// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.OffsetToObjectMapping;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class InvokeVirtualRange extends InvokeMethodRange {

  public static final int OPCODE = 0x74;
  public static final String NAME = "InvokeVirtualRange";
  public static final String SMALI_NAME = "invoke-virtual/range";

  InvokeVirtualRange(int high, BytecodeStream stream, OffsetToObjectMapping mapping) {
    super(high, stream, mapping.getMethodMap());
  }

  public InvokeVirtualRange(int firstArgumentRegister, int argumentCount, DexMethod method) {
    super(firstArgumentRegister, argumentCount, method);
  }

  @Override
  public Type getInvokeType() {
    return Type.VIRTUAL;
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
  public boolean isInvokeVirtualRange() {
    return true;
  }

  @Override
  public InvokeVirtualRange asInvokeVirtualRange() {
    return this;
  }

  @Override
  public void registerUse(UseRegistry registry) {
    registry.registerInvokeVirtual(getMethod());
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addInvokeRange(Type.VIRTUAL, getMethod(), getProto(), AA, CCCC);
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
