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
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.DynamicTypeWithUpperBound;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;
import java.util.Set;

public class Assume extends Instruction {

  private static final String ERROR_MESSAGE =
      "Expected Assume instructions to be removed after IR processing.";

  private DynamicType dynamicType;
  private final Instruction origin;

  public Assume(DynamicType dynamicType, Value dest, Value src, Instruction origin) {
    super(dest, src);
    assert dynamicType != null;
    assert !dynamicType.isUnknown();
    this.dynamicType = dynamicType;
    this.origin = origin;
  }

  public static Assume create(
      DynamicType dynamicType,
      Value dest,
      Value src,
      Instruction origin,
      AppView<?> appView,
      ProgramMethod context) {
    Assume assume = new Assume(dynamicType, dest, src, origin);
    assert assume.verifyInstruction(appView, context);
    return assume;
  }

  public void clearDynamicTypeAssumption() {
    assert dynamicType.getNullability().isDefinitelyNotNull();
    dynamicType = DynamicType.definitelyNotNull();
  }

  @Override
  public int opcode() {
    return Opcodes.ASSUME;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public DynamicType getDynamicType() {
    return dynamicType;
  }

  public DynamicTypeWithUpperBound getDynamicTypeAssumption() {
    return dynamicType.asDynamicTypeWithUpperBound();
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

  public boolean hasDynamicTypeIgnoringNullability() {
    if (dynamicType.isNotNullType()) {
      return false;
    }
    assert !dynamicType.isUnknown();
    assert dynamicType.isDynamicTypeWithUpperBound();
    return true;
  }

  public boolean hasNonNullAssumption() {
    return dynamicType.getNullability().isDefinitelyNotNull();
  }

  @Override
  public boolean couldIntroduceAnAlias(AppView<?> appView, Value root) {
    assert root != null && root.getType().isReferenceType();
    assert outValue != null;
    TypeElement outType = outValue.getType();
    if (outType.isPrimitiveType()) {
      return false;
    }
    if (hasDynamicTypeIgnoringNullability()) {
      outType = dynamicType.asDynamicTypeWithUpperBound().getDynamicUpperBoundType();
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
    return dynamicType.equals(assumeInstruction.dynamicType);
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
  public boolean verifyTypes(
      AppView<?> appView, ProgramMethod context, VerifyTypesHelper verifyTypesHelper) {
    verifyInstruction(appView, context);
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
    if (hasDynamicTypeIgnoringNullability()) {
      DynamicTypeWithUpperBound dynamicType = getDynamicType().asDynamicTypeWithUpperBound();
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

  public boolean verifyInstruction(AppView<?> appView, ProgramMethod context) {
    assert !src().isConstant()
        : "Unexpected Assume value "
            + outValue()
            + " for constant value "
            + src()
            + " defined by "
            + src().getDefinition()
            + " (context: "
            + context.toSourceString()
            + ", type: "
            + src().getType()
            + ")";
    assert !src().getType().isDefinitelyNull();
    assert !src().getType().isNullType();
    assert hasOutValue();
    if (hasDynamicTypeIgnoringNullability()) {
      assert !dynamicType.isBottom();
      assert !dynamicType.isNotNullType();
      assert !dynamicType.isNullType();
      assert !dynamicType.isUnknown();
      DynamicTypeWithUpperBound dynamicTypeWithUpperBound =
          dynamicType.asDynamicTypeWithUpperBound();
      assert dynamicTypeWithUpperBound
          .getDynamicUpperBoundType()
          .lessThanOrEqualUpToNullability(src().getType(), appView);
    } else {
      assert dynamicType.isNotNullType();
      assert hasNonNullAssumption();
      assert !src().getType().isDefinitelyNotNull()
          : "Unexpected AssumeNotNull instruction for non-null value "
              + src()
              + " defined by "
              + (src().isPhi() ? "phi" : src().getDefinition())
              + " (context: "
              + context.toSourceString()
              + ", type: "
              + src().getType()
              + ")";
    }
    assert !hasNonNullAssumption() || outValue().getType().isDefinitelyNotNull()
        : "Unexpected nullability for value "
            + outValue()
            + " defined by "
            + this
            + ": "
            + outValue().getType().nullability()
            + ", but expected: "
            + Nullability.definitelyNotNull()
            + " (context: "
            + context.toSourceString()
            + ")";
    return true;
  }
}
