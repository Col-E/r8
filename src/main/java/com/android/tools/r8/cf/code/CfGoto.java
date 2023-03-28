// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.ProgramMethod;
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
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import java.util.function.BiFunction;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfGoto extends CfJumpInstruction {

  private final CfLabel target;

  public CfGoto(CfLabel target) {
    this.target = target;
  }

  @Override
  public int getCompareToId() {
    return Opcodes.GOTO;
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    return helper.compareLabels(target, ((CfGoto) other).target, visitor);
  }

  @Override
  public void internalAcceptHashing(HashingVisitor visitor) {
    // We have no label identity to hash based on.
  }

  @Override
  public CfGoto asGoto() {
    return this;
  }

  @Override
  public boolean isGoto() {
    return true;
  }

  @Override
  public boolean isJumpWithNormalTarget() {
    return true;
  }

  @Override
  public CfLabel getTarget() {
    return target;
  }

  @Override
  public <BT, CT> TraversalContinuation<BT, CT> traverseNormalTargets(
      BiFunction<? super CfInstruction, ? super CT, TraversalContinuation<BT, CT>> fn,
      CfInstruction fallthroughInstruction,
      CT initialValue) {
    return fn.apply(target, initialValue);
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
    visitor.visitJumpInsn(Opcodes.GOTO, target.getLabel());
  }

  @Override
  public int bytecodeSizeUpperBound() {
    return 3;
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    builder.addGoto(code.getLabelOffset(target));
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return inliningConstraints.forJumpInstruction();
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    return frame;
  }
}
