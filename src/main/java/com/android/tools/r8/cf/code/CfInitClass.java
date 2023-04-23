// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
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
import java.util.Map;

import javax.annotation.Nonnull;
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
  public int getCompareToId() {
    return CfCompareHelper.CONST_CLASS_COMPARE_ID;
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    return clazz.acceptCompareTo(((CfInitClass) other).clazz, visitor);
  }

  @Override
  public void internalAcceptHashing(HashingVisitor visitor) {
    clazz.acceptHashing(visitor);
  }

  @Override
  public boolean isInitClass() {
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
    // We intentionally apply the graph lens first, and then the init class lens, using the fact
    // that the init class lens maps classes in the final program to fields in the final program.
    DexType rewrittenClass = graphLens.lookupType(clazz);
    DexField clinitField = initClassLens.getInitClassField(rewrittenClass);
    String owner = namingLens.lookupInternalName(clinitField.holder);
    String name = namingLens.lookupName(clinitField).toString();
    String desc = namingLens.lookupDescriptor(clinitField.type).toString();
    visitor.visitFieldInsn(OPCODE, owner, name, desc);
  }

  @Override
  public int bytecodeSizeUpperBound() {
    return 3;
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Nonnull
  @Override
  public CfInstruction copy(@Nonnull Map<CfLabel, CfLabel> labelMap) {
    return this;
  }

  @Override
  void internalRegisterUse(
      UseRegistry<?> registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
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
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return inliningConstraints.forInitClass(clazz, context);
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    // ..., →
    // ..., value
    return frame.push(config, appView.dexItemFactory().intType);
  }
}
