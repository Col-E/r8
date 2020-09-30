// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import org.objectweb.asm.MethodVisitor;

public class CfPosition extends CfInstruction {

  private final CfLabel label;
  private final Position position;

  public CfPosition(CfLabel label, Position position) {
    this.label = label;
    this.position = position;
  }

  @Override
  public int getCompareToId() {
    return CfCompareHelper.POSITION_COMPARE_ID;
  }

  @Override
  public int internalCompareTo(CfInstruction other, CfCompareHelper helper) {
    CfPosition otherPosition = (CfPosition) other;
    int lineDiff = position.line - otherPosition.position.line;
    return lineDiff != 0 ? lineDiff : helper.compareLabels(label, otherPosition.label);
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
    visitor.visitLineNumber(position.line, label.getLabel());
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  public Position getPosition() {
    return position;
  }

  public CfLabel getLabel() {
    return label;
  }

  @Override
  public boolean emitsIR() {
    return false;
  }

  @Override
  public boolean isPosition() {
    return true;
  }

  @Override
  public CfPosition asPosition() {
    return this;
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    Position canonical = code.getCanonicalPosition(position);
    state.setPosition(canonical);
    builder.addDebugPosition(canonical);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, DexProgramClass context) {
    return ConstraintWithTarget.ALWAYS;
  }

  @Override
  public void evaluate(
      CfFrameVerificationHelper frameBuilder,
      DexType context,
      DexType returnType,
      DexItemFactory factory,
      InitClassLens initClassLens) {
    // This is a no-op.
  }
}
