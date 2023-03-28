// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.InitClassLens;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.CfState.Slot;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.optimize.interfaces.analysis.CfAnalysisConfig;
import com.android.tools.r8.optimize.interfaces.analysis.CfFrameState;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.objectweb.asm.MethodVisitor;

public class CfRecordFieldValues extends CfInstruction {

  private final DexField[] fields;

  public CfRecordFieldValues(DexField[] fields) {
    this.fields = fields;
  }

  private static void specify(StructuralSpecification<CfRecordFieldValues, ?> spec) {
    spec.withItemArray(f -> f.fields);
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
    throw new Unreachable();
  }

  @Override
  public int bytecodeSizeUpperBound() {
    throw new Unreachable();
  }

  @Override
  public CfRecordFieldValues asRecordFieldValues() {
    return this;
  }

  @Override
  public boolean isRecordFieldValues() {
    return true;
  }

  public DexField[] getFields() {
    return fields;
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  public int getCompareToId() {
    return CfCompareHelper.RECORD_FIELD_VALUES_COMPARE_ID;
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    return visitor.visit(this, other.asRecordFieldValues(), CfRecordFieldValues::specify);
  }

  @Override
  public void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, CfRecordFieldValues::specify);
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    int parameterCount = fields.length;
    int[] registers = new int[parameterCount];
    for (int i = parameterCount - 1; i >= 0; i--) {
      Slot slot = state.pop();
      registers[i] = slot.register;
    }
    builder.addRecordFieldValues(
        fields,
        IntArrayList.wrap(registers),
        state.push(builder.dexItemFactory().objectArrayType).register);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return inliningConstraints.forRecordFieldValues();
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    for (DexField ignored : fields) {
      frame = frame.popInitialized(appView, config, dexItemFactory.objectType);
    }
    return frame.push(config, dexItemFactory.objectArrayType);
  }
}
