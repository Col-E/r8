// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code;

import com.android.tools.r8.dex.code.CfOrDexInstanceFieldRead;
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

public class CfInstanceFieldRead extends CfFieldInstruction implements CfOrDexInstanceFieldRead {

  public CfInstanceFieldRead(DexField field) {
    this(field, field);
  }

  public CfInstanceFieldRead(DexField field, DexField declaringField) {
    super(field, declaringField);
  }

  @Override
  public int getOpcode() {
    return Opcodes.GETFIELD;
  }

  @Override
  public boolean isFieldGet() {
    return true;
  }

  @Override
  public boolean isInstanceFieldGet() {
    return true;
  }

  @Override
  public CfFieldInstruction createWithField(DexField otherField) {
    return new CfInstanceFieldRead(otherField);
  }

  @Override
  void internalRegisterUse(
      UseRegistry<?> registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
    registry.registerInstanceFieldReadInstruction(this);
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    Slot object = state.pop();
    builder.addInstanceGet(state.push(getField().getType()).register, object.register, getField());
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return inliningConstraints.forInstanceGet(getField(), context);
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    // ..., objectref →
    // ..., value
    return frame
        .popInitialized(appView, config, getField().getHolderType())
        .push(config, getField().getType());
  }
}
