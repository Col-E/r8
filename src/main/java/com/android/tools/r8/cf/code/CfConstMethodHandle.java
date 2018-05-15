// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.ApiLevelException;
import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.naming.NamingLens;
import org.objectweb.asm.MethodVisitor;

public class CfConstMethodHandle extends CfInstruction {

  private DexMethodHandle handle;

  public CfConstMethodHandle(DexMethodHandle handle) {
    this.handle = handle;
  }

  public DexMethodHandle getHandle() {
    return handle;
  }

  @Override
  public void write(MethodVisitor visitor, NamingLens lens) {
    visitor.visitLdcInsn(handle.toAsmHandle(lens));
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  public void registerUse(UseRegistry registry, DexType clazz) {
    registry.registerMethodHandle(handle);
  }

  @Override
  public boolean canThrow() {
    // const-class and const-string* may throw in dex.
    return true;
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code)
      throws ApiLevelException {
    builder.addConstMethodHandle(
        state.push(builder.getFactory().methodHandleType).register, handle);
  }
}
