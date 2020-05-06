// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Assume.Assumption;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import java.util.Objects;
import java.util.Set;

public class Assume<An extends Assumption> extends Instruction {

  private static final String ERROR_MESSAGE =
      "Expected Assume instructions to be removed after IR processing.";

  private final An assumption;
  private final Instruction origin;

  private Assume(An assumption, Value dest, Value src, Instruction origin, AppView<?> appView) {
    super(dest, src);
    assert assumption != null;
    assert assumption.verifyCorrectnessOfValues(dest, src, appView);
    assert dest != null;
    this.assumption = assumption;
    this.origin = origin;
  }

  public static Assume<NoAssumption> createAssumeNoneInstruction(
      Value dest, Value src, Instruction origin, AppView<?> appView) {
    return new Assume<>(NoAssumption.get(), dest, src, origin, appView);
  }

  public static Assume<NonNullAssumption> createAssumeNonNullInstruction(
      Value dest, Value src, Instruction origin, AppView<?> appView) {
    return new Assume<>(NonNullAssumption.get(), dest, src, origin, appView);
  }

  public static Assume<DynamicTypeAssumption> createAssumeDynamicTypeInstruction(
      TypeElement dynamicUpperBoundType,
      ClassTypeElement dynamicLowerBoundType,
      Value dest,
      Value src,
      Instruction origin,
      AppView<?> appView) {
    return new Assume<>(
        new DynamicTypeAssumption(dynamicUpperBoundType, dynamicLowerBoundType),
        dest,
        src,
        origin,
        appView);
  }

  @Override
  public int opcode() {
    return Opcodes.ASSUME;
  }

  public boolean verifyInstructionIsNeeded(AppView<?> appView) {
    if (isAssumeDynamicType()) {
      assert assumption.verifyCorrectnessOfValues(outValue(), src(), appView);
    }
    return true;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public An getAssumption() {
    return assumption;
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
    if (isAssumeNone()) {
      return "AssumeNone";
    }
    if (isAssumeDynamicType()) {
      return "AssumeDynamicType";
    }
    if (isAssumeNonNull()) {
      return "AssumeNonNull";
    }
    throw new Unimplemented();
  }

  @Override
  public boolean isAssume() {
    return true;
  }

  @Override
  public Assume<An> asAssume() {
    return this;
  }

  @Override
  public boolean isAssumeNone() {
    return assumption.isAssumeNone();
  }

  @Override
  public Assume<NoAssumption> asAssumeNone() {
    assert isAssumeNone();
    @SuppressWarnings("unchecked")
    Assume<NoAssumption> self = (Assume<NoAssumption>) this;
    return self;
  }

  @Override
  public boolean isAssumeDynamicType() {
    return assumption.isAssumeDynamicType();
  }

  @Override
  public Assume<DynamicTypeAssumption> asAssumeDynamicType() {
    assert isAssumeDynamicType();
    @SuppressWarnings("unchecked")
    Assume<DynamicTypeAssumption> self = (Assume<DynamicTypeAssumption>) this;
    return self;
  }

  @Override
  public boolean isAssumeNonNull() {
    return assumption.isAssumeNonNull();
  }

  @Override
  public Assume<NonNullAssumption> asAssumeNonNull() {
    assert isAssumeNonNull();
    @SuppressWarnings("unchecked")
    Assume<NonNullAssumption> self = (Assume<NonNullAssumption>) this;
    return self;
  }

  public boolean mayAffectStaticType() {
    return isAssumeNonNull();
  }

  @Override
  public boolean couldIntroduceAnAlias(AppView<?> appView, Value root) {
    assert root != null && root.getType().isReferenceType();
    assert outValue != null;
    TypeElement outType = outValue.getType();
    if (outType.isPrimitiveType()) {
      return false;
    }
    if (assumption.isAssumeNone()) {
      // The main purpose of AssumeNone is to test local alias tracking.
      return true;
    }
    if (assumption.isAssumeDynamicType()) {
      outType = asAssumeDynamicType().assumption.getDynamicUpperBoundType();
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
    Assume<?> assumeInstruction = other.asAssume();
    return assumption.equals(assumeInstruction.assumption);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forAssume();
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    if (assumption.isAssumeNone() || assumption.isAssumeDynamicType()) {
      return src().getType();
    }
    if (assumption.isAssumeNonNull()) {
      assert src().getType().isReferenceType();
      return src().getType().asReferenceType().asMeetWithNotNull();
    }
    throw new Unimplemented();
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
  public boolean verifyTypes(AppView<?> appView) {
    assert super.verifyTypes(appView);

    TypeElement inType = src().getType();
    TypeElement outType = getOutType();
    if (isAssumeNone() || isAssumeDynamicType()) {
      assert inType.isReferenceType() : inType;
      assert outType.equals(inType)
          : "At " + this + System.lineSeparator() + outType + " != " + inType;
    } else {
      assert isAssumeNonNull() : this;
      assert inType.isReferenceType() : inType;
      assert inType.isNullType() || outType.equals(inType.asReferenceType().asMeetWithNotNull())
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
    String originString =
        origin.hasBlock() ? " (origin: `" + origin.toString() + "`)" : " (obsolete origin)";
    if (isAssumeNone() || isAssumeNonNull()) {
      return super.toString() + originString;
    }
    if (isAssumeDynamicType()) {
      DynamicTypeAssumption assumption = asAssumeDynamicType().getAssumption();
      return super.toString()
          + "; upper bound: "
          + assumption.dynamicUpperBoundType
          + (assumption.dynamicLowerBoundType != null
              ? "; lower bound: " + assumption.dynamicLowerBoundType
              : "")
          + originString;
    }
    return super.toString();
  }

  abstract static class Assumption {

    public boolean isAssumeNone() {
      return false;
    }

    public boolean isAssumeDynamicType() {
      return false;
    }

    public boolean isAssumeNonNull() {
      return false;
    }

    public boolean verifyCorrectnessOfValues(Value dest, Value src, AppView<?> appView) {
      return true;
    }
  }

  public static class NoAssumption extends Assumption {
    private static final NoAssumption instance = new NoAssumption();

    private NoAssumption() {}

    static NoAssumption get() {
      return instance;
    }

    @Override
    public boolean isAssumeNone() {
      return true;
    }

    @Override
    public boolean verifyCorrectnessOfValues(Value dest, Value src, AppView<?> appView) {
      assert dest.getType() == src.getType();
      return true;
    }
  }

  public static class DynamicTypeAssumption extends Assumption {

    private final TypeElement dynamicUpperBoundType;
    private final ClassTypeElement dynamicLowerBoundType;

    private DynamicTypeAssumption(
        TypeElement dynamicUpperBoundType, ClassTypeElement dynamicLowerBoundType) {
      this.dynamicUpperBoundType = dynamicUpperBoundType;
      this.dynamicLowerBoundType = dynamicLowerBoundType;
    }

    public TypeElement getDynamicUpperBoundType() {
      return dynamicUpperBoundType;
    }

    public ClassTypeElement getDynamicLowerBoundType() {
      return dynamicLowerBoundType;
    }

    @Override
    public boolean isAssumeDynamicType() {
      return true;
    }

    @Override
    public boolean verifyCorrectnessOfValues(Value dest, Value src, AppView<?> appView) {
      assert dynamicUpperBoundType.lessThanOrEqualUpToNullability(src.getType(), appView);
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
      return dynamicUpperBoundType == assumption.dynamicUpperBoundType
          && dynamicLowerBoundType == assumption.dynamicLowerBoundType;
    }

    @Override
    public int hashCode() {
      return Objects.hash(dynamicUpperBoundType, dynamicLowerBoundType);
    }
  }

  public static class NonNullAssumption extends Assumption {

    private static final NonNullAssumption instance = new NonNullAssumption();

    private NonNullAssumption() {}

    public static NonNullAssumption get() {
      return instance;
    }

    @Override
    public boolean isAssumeNonNull() {
      return true;
    }

    @Override
    public boolean verifyCorrectnessOfValues(Value dest, Value src, AppView<?> appView) {
      assert !src.isNeverNull();
      return true;
    }
  }
}
