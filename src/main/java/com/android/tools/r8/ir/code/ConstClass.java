// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfConstClass;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.Constraint;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import java.util.function.Function;

public class ConstClass extends ConstInstruction {

  private final DexType clazz;

  public ConstClass(Value dest, DexType clazz) {
    super(dest);
    dest.markNeverNull();
    this.clazz = clazz;
  }

  public Value dest() {
    return outValue;
  }

  public DexType getValue() {
    return clazz;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    int dest = builder.allocatedRegister(dest(), getNumber());
    builder.add(this, new com.android.tools.r8.code.ConstClass(dest, clazz));
  }

  @Override
  public int maxInValueRegister() {
    assert false : "ConstClass has no register arguments.";
    return 0;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public String toString() {
    return super.toString() + clazz.toSourceString();
  }

  @Override
  public boolean instructionTypeCanThrow() {
    return true;
  }

  @Override
  public boolean isOutConstant() {
    return true;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isConstClass() && other.asConstClass().clazz == clazz;
  }

  @Override
  public int compareNonValueParts(Instruction other) {
    return clazz.slowCompareTo(other.asConstClass().clazz);
  }

  @Override
  public boolean canBeDeadCode(IRCode code, InternalOptions options) {
    // A const-class instruction can be dead code only if the resulting program is known to contain
    // the class mentioned.
    // The simple conservative check is for the holder of the method.
    // TODO(sgjesse): It might be beneficial to check for program classes in the super hierarchy or
    // interfaces implemented.
    return code.method.method.holder == clazz;
  }

  @Override
  public boolean isConstClass() {
    return true;
  }

  @Override
  public ConstClass asConstClass() {
    return this;
  }

  @Override
  public Constraint inliningConstraint(AppInfoWithLiveness info, DexType invocationContext) {
    return Constraint.classIsVisible(invocationContext, clazz, info);
  }

  @Override
  public TypeLatticeElement evaluate(
      AppInfo appInfo, Function<Value, TypeLatticeElement> getLatticeElement) {
    return TypeLatticeElement.fromDexType(appInfo.dexItemFactory.classType, false);
  }

  @Override
  public boolean hasInvariantVerificationType() {
    return true;
  }

  @Override
  public DexType computeVerificationType(TypeVerificationHelper helper) {
    return helper.getFactory().classType;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.storeOutValue(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfConstClass(clazz));
  }
}
