// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.optimize.ReflectionOptimizer.ClassNameComputationInfo;
import com.android.tools.r8.naming.NamingLens;
import org.objectweb.asm.MethodVisitor;

public class CfDexItemBasedConstString extends CfInstruction {

  private final DexReference item;
  private final ClassNameComputationInfo classNameComputationInfo;

  public CfDexItemBasedConstString(
      DexReference item, ClassNameComputationInfo classNameComputationInfo) {
    this.item = item;
    this.classNameComputationInfo = classNameComputationInfo;
  }

  public DexReference getItem() {
    return item;
  }

  public ClassNameComputationInfo getClassNameComputationInfo() {
    return classNameComputationInfo;
  }

  @Override
  public CfDexItemBasedConstString asDexItemBasedConstString() {
    return this;
  }

  @Override
  public boolean isDexItemBasedConstString() {
    return true;
  }

  @Override
  public void write(MethodVisitor visitor, NamingLens lens) {
    throw new Unreachable(
        "CfDexItemBasedConstString instructions should always be rewritten into CfConstString");
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  public boolean canThrow() {
    // The ldc instruction may throw in Java bytecode.
    return true;
  }

  @Override
  public void registerUse(UseRegistry registry, DexType clazz) {
    if (item.isDexType() && classNameComputationInfo.needsToRegisterTypeReference()) {
      registry.registerTypeReference(item.asDexType());
    }
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    builder.addDexItemBasedConstString(state.push(builder.getFactory().stringType).register, item);
  }
}
