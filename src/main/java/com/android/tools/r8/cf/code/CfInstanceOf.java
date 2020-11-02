// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import java.util.ListIterator;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfInstanceOf extends CfInstruction {

  private final DexType type;

  public CfInstanceOf(DexType type) {
    this.type = type;
  }

  public DexType getType() {
    return type;
  }

  @Override
  public int getCompareToId() {
    return Opcodes.INSTANCEOF;
  }

  @Override
  public int internalCompareTo(CfInstruction other, CfCompareHelper helper) {
    return type.slowCompareTo(other.asInstanceOf().type);
  }

  @Override
  public boolean isInstanceOf() {
    return true;
  }

  @Override
  public CfInstanceOf asInstanceOf() {
    return this;
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
    DexType rewrittenType = graphLens.lookupType(getType());
    visitor.visitTypeInsn(Opcodes.INSTANCEOF, namingLens.lookupInternalName(rewrittenType));
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  void internalRegisterUse(
      UseRegistry registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
    registry.registerInstanceOf(type);
  }

  @Override
  public boolean canThrow() {
    return true;
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    int value = state.pop().register;
    builder.addInstanceOf(
        state.push(builder.appView.dexItemFactory().booleanType).register, value, type);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forInstanceOf(type, context);
  }

  @Override
  public void evaluate(
      CfFrameVerificationHelper frameBuilder,
      DexType context,
      DexType returnType,
      DexItemFactory factory,
      InitClassLens initClassLens) {
    // ..., objectref →
    // ..., result
    frameBuilder.popAndDiscard(factory.objectType).push(factory.intType);
  }
}
