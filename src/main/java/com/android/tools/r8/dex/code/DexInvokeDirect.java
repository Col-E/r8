// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.OffsetToObjectMapping;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class DexInvokeDirect extends DexInvokeMethod {

  public static final int OPCODE = 0x70;
  public static final String NAME = "InvokeDirect";
  public static final String SMALI_NAME = "invoke-direct";

  DexInvokeDirect(int high, BytecodeStream stream, OffsetToObjectMapping mapping) {
    super(high, stream, mapping.getMethodMap());
  }

  public DexInvokeDirect(int A, DexMethod BBBB, int C, int D, int E, int F, int G) {
    super(A, BBBB, C, D, E, F, G);
  }

  @Override
  public InvokeType getInvokeType() {
    return InvokeType.DIRECT;
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
  public void registerUse(UseRegistry<?> registry) {
    registry.registerInvokeDirect(getMethod());
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addInvokeRegisters(
        InvokeType.DIRECT, getMethod(), getProto(), A, new int[] {C, D, E, F, G});
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
