// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.VerifyTypesHelper;
import com.android.tools.r8.ir.analysis.type.DynamicTypeWithUpperBound;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;
import java.util.Objects;
import java.util.Set;

public class Assume extends Instruction {

  private static final String ERROR_MESSAGE =
      "Expected Assume instructions to be removed after IR processing.";

  private DynamicTypeAssumption dynamicTypeAssumption;
  private final NonNullAssumption nonNullAssumption;
  private final Instruction origin;

  public Assume(
      DynamicTypeAssumption dynamicTypeAssumption,
      NonNullAssumption nonNullAssumption,
      Value dest,
      Value src,
      Instruction origin,
      AppView<?> appView) {
    super(dest, src);
    assert dynamicTypeAssumption != null || nonNullAssumption != null;
    assert dynamicTypeAssumption == null
        || dynamicTypeAssumption.verifyCorrectnessOfValues(dest, src, appView);
    assert nonNullAssumption == null
        || nonNullAssumption.verifyCorrectnessOfValues(dest, src, appView);
    assert dest != null;
    this.dynamicTypeAssumption = dynamicTypeAssumption;
    this.nonNullAssumption = nonNullAssumption;
    this.origin = origin;
  }

  public static Assume createAssumeNonNullInstruction(
      Value dest, Value src, Instruction origin, AppView<?> appView) {
    return new Assume(null, NonNullAssumption.get(), dest, src, origin, appView);
  }

  public static Assume createAssumeDynamicTypeInstruction(
      DynamicTypeWithUpperBound dynamicType,
      Value dest,
      Value src,
      Instruction origin,
      AppView<?> appView) {
    return new Assume(new DynamicTypeAssumption(dynamicType), null, dest, src, origin, appView);
  }

  @Override
  public int opcode() {
    return Opcodes.ASSUME;
  }

  public boolean verifyInstructionIsNeeded(AppView<?> appView) {
    if (hasDynamicTypeAssumption()) {
      assert dynamicTypeAssumption.verifyCorrectnessOfValues(outValue(), src(), appView);
    }
    return true;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public DynamicTypeAssumption getDynamicTypeAssumption() {
    return dynamicTypeAssumption;
  }

  public NonNullAssumption getNonNullAssumption() {
    return nonNullAssumption;
  }

  public Value src() {
    return inValues.get(0);
  }

  public Instruction origin() {
    return origin;
  }

  @Override
  public boolean outTypeKnownToBeBoolean(Set<Phi> seen) {
    return src().knownToBeBoolean(seen);
  }

  @Override
  public String getInstructionName() {
    return "Assume";
  }

  @Override
  public boolean isAssume() {
    return true;
  }

  @Override
  public Assume asAssume() {
    return this;
  }

  public boolean hasDynamicTypeAssumption() {
    return dynamicTypeAssumption != null;
  }

  public void unsetDynamicTypeAssumption() {
    dynamicTypeAssumption = null;
  }

  public boolean hasNonNullAssumption() {
    return nonNullAssumption != null;
  }

  @Override
  public boolean couldIntroduceAnAlias(AppView<?> appView, Value root) {
    assert root != null && root.getType().isReferenceType();
    assert outValue != null;
    TypeElement outType = outValue.getType();
    if (outType.isPrimitiveType()) {
      return false;
    }
    if (hasDynamicTypeAssumption()) {
      outType = dynamicTypeAssumption.getDynamicType().getDynamicUpperBoundType();
    }
    if (appView.appInfo().hasLiveness()) {
      if (outType.isClassType()
          && root.getType().isClassType()
          && appView
              .appInfo()
              .withLiveness()
              .inDifferentHierarchy(
                  outType.asClassType().getClassType(),
                  root.getType().asClassType().getClassType())) {
        return false;
      }
    }
    return outType.isReferenceType();
  }

  @Override
  public boolean isIntroducingAnAlias() {
    return true;
  }

  @Override
  public Value getAliasForOutValue() {
    return src();
  }

  @Override
  public void buildDex(DexBuilder builder) {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public int maxInValueRegister() {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public int maxOutValueRegister() {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public boolean isOutConstant() {
    return false;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    if (!other.isAssume()) {
      return false;
    }
    Assume assumeInstruction = other.asAssume();
    return Objects.equals(dynamicTypeAssumption, assumeInstruction.dynamicTypeAssumption)
        && Objects.equals(nonNullAssumption, assumeInstruction.nonNullAssumption);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forAssume();
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    if (hasNonNullAssumption()) {
      assert src().getType().isReferenceType();
      return src().getType().asReferenceType().asMeetWithNotNull();
    }
    return src().getType();
  }

  @Override
  public DexType computeVerificationType(AppView<?> appView, TypeVerificationHelper helper) {
    return helper.getDexType(src());
  }

  @Override
  public boolean hasInvariantOutType() {
    return false;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
  }

  @Override
  public Value getNonNullInput() {
    return src();
  }

  @Override
  public boolean throwsOnNullInput() {
    return hasNonNullAssumption();
  }

  @Override
  public boolean verifyTypes(AppView<?> appView, VerifyTypesHelper verifyTypesHelper) {
    assert super.verifyTypes(appView, verifyTypesHelper);

    TypeElement inType = src().getType();
    assert inType.isReferenceType() : inType;

    TypeElement outType = getOutType();
    if (hasNonNullAssumption()) {
      assert inType.isNullType() || outType.equals(inType.asReferenceType().asMeetWithNotNull())
          : "At " + this + System.lineSeparator() + outType + " != " + inType;
    } else {
      assert hasDynamicTypeAssumption();
      assert !src().isConstNumber();
      assert outType.equals(inType)
          : "At " + this + System.lineSeparator() + outType + " != " + inType;
    }
    return true;
  }

  @Override
  public String toString() {
    // `origin` could become obsolete:
    //   1) during branch simplification, the origin `if` could be simplified, which means the
    //     assumption became "truth."
    //   2) invoke-interface could be devirtualized, while its dynamic type and/or non-null receiver
    //     are still valid.
    StringBuilder builder = new StringBuilder(super.toString());
    if (hasNonNullAssumption()) {
      builder.append("; not null");
    }
    if (hasDynamicTypeAssumption()) {
      DynamicTypeWithUpperBound dynamicType = dynamicTypeAssumption.getDynamicType();
      if (hasOutValue()) {
        if (!dynamicType.getDynamicUpperBoundType().equalUpToNullability(outValue.getType())) {
          builder.append("; upper bound: ").append(dynamicType.getDynamicUpperBoundType());
        }
      }
      if (dynamicType.hasDynamicLowerBoundType()) {
        builder.append("; lower bound: ").append(dynamicType.getDynamicLowerBoundType());
      }
    }
    return builder.toString();
  }

  public static class DynamicTypeAssumption {

    private final DynamicTypeWithUpperBound dynamicType;

    public DynamicTypeAssumption(DynamicTypeWithUpperBound dynamicType) {
      assert dynamicType != null;
      this.dynamicType = dynamicType;
    }

    public DynamicTypeWithUpperBound getDynamicType() {
      return dynamicType;
    }

    public boolean verifyCorrectnessOfValues(Value dest, Value src, AppView<?> appView) {
      assert !dynamicType.isBottom();
      assert !dynamicType.isUnknown();
      assert dynamicType
          .getDynamicUpperBoundType()
          .lessThanOrEqualUpToNullability(src.getType(), appView);
      return true;
    }

    @Override
    public boolean equals(Object other) {
      if (other == null) {
        return false;
      }
      if (getClass() != other.getClass()) {
        return false;
      }
      DynamicTypeAssumption assumption = (DynamicTypeAssumption) other;
      return dynamicType.equals(assumption.dynamicType);
    }

    @Override
    public int hashCode() {
      return dynamicType.hashCode();
    }
  }

  public static class NonNullAssumption {

    private static final NonNullAssumption instance = new NonNullAssumption();

    private NonNullAssumption() {}

    public static NonNullAssumption get() {
      return instance;
    }

    public boolean verifyCorrectnessOfValues(Value dest, Value src, AppView<?> appView) {
      assert !src.isNeverNull();
      return true;
    }
  }
}
