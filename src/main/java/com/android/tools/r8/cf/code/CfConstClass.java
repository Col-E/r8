// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.InitClassLens;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.optimize.interfaces.analysis.CfAnalysisConfig;
import com.android.tools.r8.optimize.interfaces.analysis.CfFrameState;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import java.util.ListIterator;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class CfConstClass extends CfInstruction implements CfTypeInstruction {

  private final DexType type;
  private final boolean ignoreCompatRules;

  public CfConstClass(DexType type) {
    this(type, false);
  }

  public CfConstClass(DexType type, boolean ignoreCompatRules) {
    // Primitive types and void should be retrieved using, for example, java.lang.Integer.TYPE.
    assert !type.isPrimitiveType();
    assert !type.isVoidType();
    this.type = type;
    this.ignoreCompatRules = ignoreCompatRules;
  }

  @Override
  public boolean isConstClass() {
    return true;
  }

  @Override
  public CfConstClass asConstClass() {
    return this;
  }

  public boolean ignoreCompatRules() {
    return ignoreCompatRules;
  }

  @Override
  public int getCompareToId() {
    return CfCompareHelper.CONST_CLASS_COMPARE_ID;
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    return type.acceptCompareTo(((CfConstClass) other).type, visitor);
  }

  @Override
  public void internalAcceptHashing(HashingVisitor visitor) {
    type.acceptHashing(visitor);
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
    return new CfConstClass(newType);
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
    visitor.visitLdcInsn(Type.getObjectType(getInternalName(graphLens, namingLens)));
  }

  @Override
  public int bytecodeSizeUpperBound() {
    // ldc or ldc_w
    return 3;
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  public boolean canThrow() {
    return true;
  }

  private String getInternalName(GraphLens graphLens, NamingLens namingLens) {
    DexType rewrittenType = graphLens.lookupType(type);
    switch (rewrittenType.toShorty()) {
      case '[':
      case 'L':
        return namingLens.lookupInternalName(rewrittenType);
      default:
        throw new Unreachable("Unexpected type in const-class: " + rewrittenType);
    }
  }

  @Override
  void internalRegisterUse(
      UseRegistry<?> registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
    registry.registerConstClass(type, iterator, ignoreCompatRules());
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    builder.addConstClass(state.push(builder.appView.dexItemFactory().classType).register, type);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return inliningConstraints.forConstClass(type, context);
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    // ... →
    // ..., value
    return frame.push(config, appView.dexItemFactory().classType);
  }
}
