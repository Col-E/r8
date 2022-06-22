// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code;

import static com.android.tools.r8.optimize.interfaces.analysis.ErroneousCfFrameState.formatActual;

import com.android.tools.r8.cf.code.frame.PreciseFrameType;
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
import com.android.tools.r8.optimize.interfaces.analysis.ErroneousCfFrameState;
import java.util.ListIterator;
import org.objectweb.asm.Opcodes;

public class CfInstanceFieldWrite extends CfFieldInstruction {

  public CfInstanceFieldWrite(DexField field) {
    this(field, field);
  }

  public CfInstanceFieldWrite(DexField field, DexField declaringField) {
    super(field, declaringField);
  }

  @Override
  public CfFieldInstruction createWithField(DexField otherField) {
    return new CfInstanceFieldWrite(otherField);
  }

  @Override
  public int getOpcode() {
    return Opcodes.PUTFIELD;
  }

  @Override
  public boolean isFieldPut() {
    return true;
  }

  @Override
  public boolean isInstanceFieldPut() {
    return true;
  }

  @Override
  public CfInstanceFieldWrite asInstanceFieldPut() {
    return this;
  }

  @Override
  void internalRegisterUse(
      UseRegistry<?> registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
    registry.registerInstanceFieldWrite(getField());
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    Slot value = state.pop();
    Slot object = state.pop();
    builder.addInstancePut(value.register, object.register, getField());
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return inliningConstraints.forInstancePut(getField(), context);
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    // ..., objectref, value →
    // ...
    return frame
        .popInitialized(appView, config, getField().getType())
        .popObject(
            appView,
            getField().getHolderType(),
            config,
            (state, head) -> head.isUninitializedNew() ? error(head) : state);
  }

  private ErroneousCfFrameState error(PreciseFrameType objectType) {
    return CfFrameState.error(
        "Frame type "
            + formatActual(objectType)
            + " is not assignable to "
            + getField().getHolderType().getTypeName());
  }
}
