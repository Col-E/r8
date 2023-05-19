// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.code.CfRecordFieldValues;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexRecordFieldValues;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;
import java.util.Arrays;
import java.util.List;

public class RecordFieldValues extends Instruction {

  private final DexField[] fields;

  public RecordFieldValues(DexField[] fields, Value outValue, List<Value> fieldValues) {
    super(outValue, fieldValues);
    assert fields.length == fieldValues.size();
    this.fields = fields;
  }

  public DexField[] getFields() {
    return fields;
  }

  @Override
  public int opcode() {
    return Opcodes.RECORD_FIELD_VALUES;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public RecordFieldValues asRecordFieldValues() {
    return this;
  }

  @Override
  public boolean isRecordFieldValues() {
    return true;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    // There are no restrictions on the registers since this instruction eventually has to be
    // removed through IR.
    int[] arguments = new int[inValues().size()];
    for (int i = 0; i < inValues().size(); i++) {
      arguments[i] = builder.allocatedRegister(inValues().get(i), getNumber());
    }
    int dest = builder.allocatedRegister(outValue(), getNumber());
    builder.add(this, new DexRecordFieldValues(dest, arguments, fields));
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return TypeElement.fromDexType(
        appView.dexItemFactory().objectArrayType, Nullability.definitelyNotNull(), appView);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfRecordFieldValues(fields), this);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addRecordFieldValues(getFields(), inValues());
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    if (!other.isRecordFieldValues()) {
      return false;
    }
    RecordFieldValues o = other.asRecordFieldValues();
    return Arrays.equals(o.fields, fields);
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
  }

  @Override
  public int maxInValueRegister() {
    return Constants.U16BIT_MAX;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U16BIT_MAX;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forRecordFieldValues();
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.loadInValues(this, it);
    helper.storeOutValue(this, it);
  }

  @Override
  public boolean hasInvariantOutType() {
    return true;
  }
}
