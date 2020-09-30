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
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.CfState.Slot;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfReturn extends CfInstruction {

  private final ValueType type;

  public CfReturn(ValueType type) {
    this.type = type;
  }

  public ValueType getType() {
    return type;
  }

  @Override
  public int getCompareToId() {
    return getOpcode();
  }

  @Override
  public int internalCompareTo(CfInstruction other, CfCompareHelper helper) {
    return CfCompareHelper.compareIdUniquelyDeterminesEquality(this, other);
  }

  private int getOpcode() {
    switch (type) {
      case INT:
        return Opcodes.IRETURN;
      case FLOAT:
        return Opcodes.FRETURN;
      case LONG:
        return Opcodes.LRETURN;
      case DOUBLE:
        return Opcodes.DRETURN;
      case OBJECT:
        return Opcodes.ARETURN;
      default:
        throw new Unreachable("Unexpected return type: " + type);
    }
  }

  @Override
  public boolean isJump() {
    return true;
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
    visitor.visitInsn(getOpcode());
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  public boolean isReturn() {
    return true;
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    Slot pop = state.pop();
    builder.addReturn(pop.register);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, DexProgramClass context) {
    return inliningConstraints.forReturn();
  }

  @Override
  public void evaluate(
      CfFrameVerificationHelper frameBuilder,
      DexType context,
      DexType returnType,
      DexItemFactory factory,
      InitClassLens initClassLens) {
    assert returnType != null;
    frameBuilder.popAndDiscard(returnType);
    frameBuilder.setNoFrame();
  }
}
