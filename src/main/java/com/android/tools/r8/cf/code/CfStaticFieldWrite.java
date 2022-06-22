// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.CfState.Slot;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.optimize.interfaces.analysis.CfAnalysisConfig;
import com.android.tools.r8.optimize.interfaces.analysis.CfFrameState;
import java.util.ListIterator;
import org.objectweb.asm.Opcodes;

public class CfStaticFieldWrite extends CfFieldInstruction {

  public CfStaticFieldWrite(DexField field) {
    super(field);
  }

  public CfStaticFieldWrite(DexField field, DexField declaringField) {
    super(field, declaringField);
  }

  @Override
  public CfFieldInstruction createWithField(DexField otherField) {
    return new CfStaticFieldWrite(otherField);
  }

  @Override
  public int getOpcode() {
    return Opcodes.PUTSTATIC;
  }

  @Override
  public boolean isFieldPut() {
    return true;
  }

  @Override
  public boolean isStaticFieldPut() {
    return true;
  }

  @Override
  public CfStaticFieldWrite asStaticFieldPut() {
    return this;
  }

  @Override
  void internalRegisterUse(
      UseRegistry<?> registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
    registry.registerStaticFieldWrite(getField());
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    Slot value = state.pop();
    builder.addStaticPut(value.register, getField());
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return inliningConstraints.forStaticPut(getField(), context);
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    // ..., value →
    // ...
    return frame.popInitialized(appView, config, getField().getType());
  }
}
