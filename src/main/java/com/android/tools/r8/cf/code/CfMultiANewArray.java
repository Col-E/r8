// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
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
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.ListIterator;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfMultiANewArray extends CfInstruction implements CfTypeInstruction {

  private final DexType type;
  private final int dimensions;

  private static void specify(StructuralSpecification<CfMultiANewArray, ?> spec) {
    spec.withInt(CfMultiANewArray::getDimensions).withItem(CfMultiANewArray::getType);
  }

  public CfMultiANewArray(DexType type, int dimensions) {
    this.type = type;
    this.dimensions = dimensions;
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
    return new CfMultiANewArray(newType, dimensions);
  }

  public int getDimensions() {
    return dimensions;
  }

  @Override
  public int getCompareToId() {
    return Opcodes.MULTIANEWARRAY;
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    return visitor.visit(this, (CfMultiANewArray) other, CfMultiANewArray::specify);
  }

  @Override
  public void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, CfMultiANewArray::specify);
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
    visitor.visitMultiANewArrayInsn(namingLens.lookupInternalName(rewrittenType), dimensions);
  }

  @Override
  public int bytecodeSizeUpperBound() {
    return 4;
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  void internalRegisterUse(
      UseRegistry<?> registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
    registry.registerTypeReference(type);
  }

  @Override
  public boolean canThrow() {
    return true;
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    InternalOptions options = builder.appView.options();
    assert !options.isGeneratingDex();
    int[] dimensions = state.popReverse(this.dimensions);
    builder.addMultiNewArray(type, state.push(type).register, dimensions);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return inliningConstraints.forInvokeMultiNewArray(type, context);
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    // ..., count1, [count2, ...] →
    // ..., arrayref
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    for (int i = 0; i < dimensions; i++) {
      frame = frame.popInitialized(appView, config, dexItemFactory.intType);
    }
    return frame.push(config, type);
  }
}
