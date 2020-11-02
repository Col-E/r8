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
import com.android.tools.r8.utils.InternalOptions;
import java.util.Comparator;
import java.util.ListIterator;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfMultiANewArray extends CfInstruction {

  private final DexType type;
  private final int dimensions;

  public CfMultiANewArray(DexType type, int dimensions) {
    this.type = type;
    this.dimensions = dimensions;
  }

  public DexType getType() {
    return type;
  }

  public int getDimensions() {
    return dimensions;
  }

  @Override
  public int getCompareToId() {
    return Opcodes.MULTIANEWARRAY;
  }

  @Override
  public int internalCompareTo(CfInstruction other, CfCompareHelper helper) {
    return Comparator.comparingInt(CfMultiANewArray::getDimensions)
        .thenComparing(CfMultiANewArray::getType, DexType::slowCompareTo)
        .compare(this, ((CfMultiANewArray) other));
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
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  void internalRegisterUse(
      UseRegistry registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
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
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forInvokeMultiNewArray(type, context);
  }

  @Override
  public void evaluate(
      CfFrameVerificationHelper frameBuilder,
      DexType context,
      DexType returnType,
      DexItemFactory factory,
      InitClassLens initClassLens) {
    // ..., count1, [count2, ...] →
    // ..., arrayref
    for (int i = 0; i < dimensions; i++) {
      frameBuilder.pop(factory.intType);
    }
    frameBuilder.push(type);
  }
}
