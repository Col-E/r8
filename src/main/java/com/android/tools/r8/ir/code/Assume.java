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
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.Assume.Assumption;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
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

  public static Assume<NonNullAssumption> createAssumeNonNullInstruction(
      Value dest, Value src, Instruction origin, AppView<?> appView) {
    return new Assume<>(NonNullAssumption.get(), dest, src, origin, appView);
  }

  public static Assume<DynamicTypeAssumption> createAssumeDynamicTypeInstruction(
      TypeLatticeElement type, Value dest, Value src, Instruction origin, AppView<?> appView) {
    return new Assume<>(new DynamicTypeAssumption(type), dest, src, origin, appView);
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

  @Override
  public boolean couldIntroduceAnAlias(AppView<?> appView, Value root) {
    assert root != null && root.getTypeLattice().isReference();
    assert outValue != null;
    TypeLatticeElement outType = outValue.getTypeLattice();
    if (outType.isPrimitive()) {
      return false;
    }
    if (assumption.isAssumeDynamicType()) {
      outType = asAssumeDynamicType().assumption.getType();
    }
    if (appView.appInfo().hasSubtyping()) {
      if (outType.isClassType()
          && root.getTypeLattice().isClassType()
          && appView.appInfo().withSubtyping().inDifferentHierarchy(
              outType.asClassTypeLatticeElement().getClassType(),
              root.getTypeLattice().asClassTypeLatticeElement().getClassType())) {
        return false;
      }
    }
    return outType.isReference();
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
      InliningConstraints inliningConstraints, DexType invocationContext) {
    return inliningConstraints.forAssume();
  }

  @Override
  public TypeLatticeElement evaluate(AppView<?> appView) {
    if (assumption.isAssumeDynamicType()) {
      return src().getTypeLattice();
    }
    if (assumption.isAssumeNonNull()) {
      assert src().getTypeLattice().isReference();
      return src().getTypeLattice().asReferenceTypeLatticeElement().asNotNull();
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
  public String toString() {
    if (isAssumeDynamicType()) {
      return super.toString() + "; type: " + asAssumeDynamicType().getAssumption().type;
    }
    if (isAssumeNonNull()) {
      return super.toString() + "; not null";
    }
    return super.toString();
  }

  abstract static class Assumption {

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

  public static class DynamicTypeAssumption extends Assumption {

    private final TypeLatticeElement type;

    private DynamicTypeAssumption(TypeLatticeElement type) {
      this.type = type;
    }

    public TypeLatticeElement getType() {
      return type;
    }

    @Override
    public boolean isAssumeDynamicType() {
      return true;
    }

    @Override
    public boolean verifyCorrectnessOfValues(Value dest, Value src, AppView<?> appView) {
      assert type.lessThanOrEqualUpToNullability(src.getTypeLattice(), appView);
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
      return type == assumption.type;
    }

    @Override
    public int hashCode() {
      return type.hashCode();
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
