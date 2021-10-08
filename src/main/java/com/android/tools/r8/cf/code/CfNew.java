// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
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
import com.android.tools.r8.utils.structural.CompareToVisitor;
import java.util.ListIterator;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfNew extends CfInstruction implements CfTypeInstruction {

  private final DexType type;

  public CfNew(DexType type) {
    this.type = type;
  }

  @Override
  public CfTypeInstruction asTypeInstruction() {
    return this;
  }

  @Override
  public boolean isTypeInstruction() {
    return true;
  }

  @Override
  public DexType getType() {
    return type;
  }

  @Override
  public CfInstruction withType(DexType newType) {
    return new CfNew(newType);
  }

  @Override
  public int getCompareToId() {
    return Opcodes.NEW;
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    return type.acceptCompareTo(((CfNew) other).type, visitor);
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
    visitor.visitTypeInsn(Opcodes.NEW, namingLens.lookupInternalName(rewrittenType));
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  void internalRegisterUse(
      UseRegistry<?> registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
    registry.registerNewInstance(type);
  }

  @Override
  public boolean canThrow() {
    return true;
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    builder.addNewInstance(state.push(type).register, type);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return inliningConstraints.forNewInstance(type, context);
  }

  @Override
  public void evaluate(
      CfFrameVerificationHelper frameBuilder,
      DexType context,
      DexType returnType,
      DexItemFactory factory,
      InitClassLens initClassLens) {
    // ... →
    // ..., objectref
    frameBuilder.push(FrameType.uninitializedNew(new CfLabel(), type));
  }
}
