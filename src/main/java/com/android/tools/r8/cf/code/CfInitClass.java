// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import org.objectweb.asm.MethodVisitor;

public class CfInitClass extends CfInstruction {

  private static final int OPCODE = org.objectweb.asm.Opcodes.GETSTATIC;

  private final DexType clazz;

  public CfInitClass(DexType clazz) {
    this.clazz = clazz;
  }

  public DexType getClassValue() {
    return clazz;
  }

  public int getOpcode() {
    return OPCODE;
  }

  @Override
  public void write(MethodVisitor visitor, InitClassLens initClassLens, NamingLens lens) {
    DexField field = initClassLens.getInitClassField(clazz);
    String owner = lens.lookupInternalName(field.holder);
    String name = lens.lookupName(field).toString();
    String desc = lens.lookupDescriptor(field.type).toString();
    visitor.visitFieldInsn(OPCODE, owner, name, desc);
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  void internalRegisterUse(UseRegistry registry, DexClassAndMethod context) {
    registry.registerInitClass(clazz);
  }

  @Override
  public boolean canThrow() {
    return true;
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    int dest = state.push(builder.appView.dexItemFactory().intType).register;
    builder.addInitClass(dest, clazz);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, DexProgramClass context) {
    return inliningConstraints.forInitClass(clazz, context);
  }
}
