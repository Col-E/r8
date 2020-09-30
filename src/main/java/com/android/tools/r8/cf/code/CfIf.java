// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.If.Type;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfIf extends CfInstruction {

  private final If.Type kind;
  private final ValueType type;
  private final CfLabel target;

  public CfIf(If.Type kind, ValueType type, CfLabel target) {
    this.kind = kind;
    this.type = type;
    this.target = target;
  }

  @Override
  public int getCompareToId() {
    return getOpcode();
  }

  @Override
  public int internalCompareTo(CfInstruction other, CfCompareHelper helper) {
    CfIf otherIf = (CfIf) other;
    assert kind == otherIf.kind;
    assert type == otherIf.type;
    return helper.compareLabels(target, otherIf.target);
  }

  public ValueType getType() {
    return type;
  }

  public Type getKind() {
    return kind;
  }

  @Override
  public CfLabel getTarget() {
    return target;
  }

  public int getOpcode() {
    switch (kind) {
      case EQ:
        return type.isObject() ? Opcodes.IFNULL : Opcodes.IFEQ;
      case GE:
        return Opcodes.IFGE;
      case GT:
        return Opcodes.IFGT;
      case LE:
        return Opcodes.IFLE;
      case LT:
        return Opcodes.IFLT;
      case NE:
        return type.isObject() ? Opcodes.IFNONNULL : Opcodes.IFNE;
      default:
        throw new Unreachable("Unexpected type " + type);
    }
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  public void write(
      AppView<?> appView,
      ProgramMethod context,
      DexItemFactory dexItemFactory,
      GraphLens graphLens,
      InitClassLens initClassLens,
      NamingLens namingLens,
      LensCodeRewriterUtils rewriter,
      MethodVisitor visitor) {
    visitor.visitJumpInsn(getOpcode(), target.getLabel());
  }

  @Override
  public boolean isConditionalJump() {
    return true;
  }

  @Override
  public boolean isJump() {
    return true;
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    int value = state.pop().register;
    int trueTargetOffset = code.getLabelOffset(target);
    int falseTargetOffset = code.getCurrentInstructionIndex() + 1;
    builder.addIfZero(kind, type, value, trueTargetOffset, falseTargetOffset);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, DexProgramClass context) {
    return inliningConstraints.forJumpInstruction();
  }

  @Override
  public void evaluate(
      CfFrameVerificationHelper frameBuilder,
      DexType context,
      DexType returnType,
      DexItemFactory factory,
      InitClassLens initClassLens) {
    // ..., value →
    // ...
    frameBuilder.pop(
        type.isObject() ? factory.objectType : type.toPrimitiveType().toDexType(factory));
    frameBuilder.verifyTarget(target);
  }
}
