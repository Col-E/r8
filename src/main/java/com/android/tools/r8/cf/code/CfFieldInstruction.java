// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.CfState.Slot;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfFieldInstruction extends CfInstruction {

  private final int opcode;
  private final DexField field;
  private final DexField declaringField;

  public CfFieldInstruction(int opcode, DexField field, DexField declaringField) {
    this.opcode = opcode;
    this.field = field;
    this.declaringField = declaringField;
    assert field.type == declaringField.type;
  }

  public DexField getField() {
    return field;
  }

  public int getOpcode() {
    return opcode;
  }

  @Override
  public CfFieldInstruction asFieldInstruction() {
    return this;
  }

  @Override
  public boolean isFieldInstruction() {
    return true;
  }

  @Override
  public void write(MethodVisitor visitor, InitClassLens initClassLens, NamingLens lens) {
    String owner = lens.lookupInternalName(field.holder);
    String name = lens.lookupName(declaringField).toString();
    String desc = lens.lookupDescriptor(field.type).toString();
    visitor.visitFieldInsn(opcode, owner, name, desc);
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  void internalRegisterUse(UseRegistry registry, DexClassAndMethod context) {
    switch (opcode) {
      case Opcodes.GETFIELD:
        registry.registerInstanceFieldRead(field);
        break;
      case Opcodes.PUTFIELD:
        registry.registerInstanceFieldWrite(field);
        break;
      case Opcodes.GETSTATIC:
        registry.registerStaticFieldRead(field);
        break;
      case Opcodes.PUTSTATIC:
        registry.registerStaticFieldWrite(field);
        break;
      default:
        throw new Unreachable("Unexpected opcode " + opcode);
    }
  }

  @Override
  public boolean canThrow() {
    return true;
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    DexType type = field.type;
    switch (opcode) {
      case Opcodes.GETSTATIC:
        {
          builder.addStaticGet(state.push(type).register, field);
          break;
        }
      case Opcodes.PUTSTATIC:
        {
          Slot value = state.pop();
          builder.addStaticPut(value.register, field);
          break;
        }
      case Opcodes.GETFIELD:
        {
          Slot object = state.pop();
          builder.addInstanceGet(state.push(type).register, object.register, field);
          break;
        }
      case Opcodes.PUTFIELD:
        {
          Slot value = state.pop();
          Slot object = state.pop();
          builder.addInstancePut(value.register, object.register, field);
          break;
        }
      default:
        throw new Unreachable("Unexpected opcode " + opcode);
    }
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, DexProgramClass context) {
    switch (opcode) {
      case Opcodes.GETSTATIC:
        return inliningConstraints.forStaticGet(field, context);
      case Opcodes.PUTSTATIC:
        return inliningConstraints.forStaticPut(field, context);
      case Opcodes.GETFIELD:
        return inliningConstraints.forInstanceGet(field, context);
      case Opcodes.PUTFIELD:
        return inliningConstraints.forInstancePut(field, context);
      default:
        throw new Unreachable("Unexpected opcode " + opcode);
    }
  }
}
