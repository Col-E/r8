// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexCheckCast;
import com.android.tools.r8.dex.code.DexMoveObject;
import com.android.tools.r8.dex.code.DexMoveObjectFrom16;
import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.analysis.VerifyTypesHelper;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;

public class CheckCast extends Instruction {

  private final DexType type;
  private final boolean ignoreCompatRules;

  // A CheckCast dex instruction takes only one register containing a value and changes
  // the associated type information for that value. In the IR we let the CheckCast
  // instruction define a new value. During register allocation we then need to arrange it
  // so that the source and destination are assigned the same register.
  public CheckCast(Value dest, Value value, DexType type) {
    this(dest, value, type, false);
  }

  public CheckCast(Value dest, Value value, DexType type, boolean ignoreCompatRules) {
    super(dest, value);
    this.type = type;
    this.ignoreCompatRules = ignoreCompatRules;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isRefiningStaticType(InternalOptions options) {
    TypeElement inType = object().getType();
    if (inType.isNullType()) {
      // If the in-value is `null` and the cast-type is a float-array type, then trivial check-cast
      // elimination may lead to verification errors. See b/123269162.
      if (options.canHaveArtCheckCastVerifierBug()
          && getType().isArrayType()
          && getType().toBaseType(options.dexItemFactory()).isFloatType()) {
        return true;
      }
      return false;
    }
    if (!inType.isClassType()) {
      // Conservatively return true.
      assert inType.isArrayType();
      return true;
    }
    ClassTypeElement inClassType = inType.asClassType();
    return type != inClassType.getClassType();
  }

  @Override
  public boolean ignoreCompatRules() {
    return ignoreCompatRules;
  }

  @Override
  public int opcode() {
    return Opcodes.CHECK_CAST;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public DexType getType() {
    return type;
  }

  public Value object() {
    return inValues().get(0);
  }

  @Override
  public void buildDex(DexBuilder builder) {
    // The check cast instruction in dex doesn't write a new register. Therefore,
    // if the register allocator could not put input and output in the same register
    // we have to insert a move before the check cast instruction.
    int inRegister = builder.allocatedRegister(inValues.get(0), getNumber());
    if (outValue == null) {
      builder.add(this, createCheckCast(inRegister));
    } else {
      int outRegister = builder.allocatedRegister(outValue, getNumber());
      if (inRegister == outRegister) {
        builder.add(this, createCheckCast(outRegister));
      } else {
        DexCheckCast cast = createCheckCast(outRegister);
        if (outRegister <= Constants.U4BIT_MAX && inRegister <= Constants.U4BIT_MAX) {
          builder.add(this, new DexMoveObject(outRegister, inRegister), cast);
        } else {
          builder.add(this, new DexMoveObjectFrom16(outRegister, inRegister), cast);
        }
      }
    }
  }

  DexCheckCast createCheckCast(int register) {
    return new DexCheckCast(register, getType(), ignoreCompatRules());
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isCheckCast() && other.asCheckCast().type == type;
  }

  @Override
  public int maxInValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public boolean instructionInstanceCanThrow(
      AppView<?> appView,
      ProgramMethod context,
      AbstractValueSupplier abstractValueSupplier,
      SideEffectAssumption assumption) {
    if (appView.options().debug || !appView.appInfo().hasLiveness()) {
      return true;
    }
    if (type.isPrimitiveType()) {
      return true;
    }
    AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
    DexType baseType = type.toBaseType(appView.dexItemFactory());
    if (baseType.isClassType()) {
      DexClass definition = appView.definitionFor(baseType);
      // Check that the class and its super types are present.
      if (definition == null || !definition.isResolvable(appView)) {
        return true;
      }
      // Check that the class is accessible.
      if (AccessControl.isClassAccessible(definition, context, appViewWithLiveness)
          .isPossiblyFalse()) {
        return true;
      }
    }
    if (!appView
        .getOpenClosedInterfacesCollection()
        .isDefinitelyInstanceOfStaticType(appViewWithLiveness, object())) {
      return true;
    }
    TypeElement castType = TypeElement.fromDexType(type, definitelyNotNull(), appView);
    if (object()
        .getDynamicUpperBoundType(appViewWithLiveness)
        .lessThanOrEqualUpToNullability(castType, appView)) {
      // This is a check-cast that has to be there for bytecode correctness, but R8 has proven
      // that this cast will never throw.
      return false;
    }
    return true;
  }

  @Override
  public boolean instructionTypeCanThrow() {
    return true;
  }

  @Override
  public boolean isCheckCast() {
    return true;
  }

  @Override
  public CheckCast asCheckCast() {
    return this;
  }

  @Override
  public String toString() {
    return super.toString() + "; " + type;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forCheckCast(type, context);
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return TypeElement.fromDexType(type, object().getType().nullability(), appView);
  }

  @Override
  public boolean verifyTypes(
      AppView<?> appView, ProgramMethod context, VerifyTypesHelper verifyTypesHelper) {
    assert super.verifyTypes(appView, context, verifyTypesHelper);

    TypeElement inType = object().getType();

    assert inType.isPreciseType();

    TypeElement outType = getOutType();
    TypeElement castType = TypeElement.fromDexType(getType(), inType.nullability(), appView);

    // We don't have enough information to remove the cast. Check that the out-value does not
    // have a more precise type than the cast-type.
    assert outType.equalUpToNullability(castType);

    // Check soundness of null information.
    assert inType.nullability() == outType.nullability() || inType.isNullType()
        : "Expected nullability of value "
            + outValue()
            + " defined by "
            + this
            + " to be "
            + inType.nullability()
            + ", but was "
            + outType.nullability()
            + "(context: "
            + context.toSourceString()
            + ")";

    return true;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.loadInValues(this, it);
    helper.storeOutValue(this, it);
  }

  @Override
  public boolean hasInvariantOutType() {
    // Nullability of in-value can be refined.
    return false;
  }

  @Override
  public DexType computeVerificationType(AppView<?> appView, TypeVerificationHelper helper) {
    return type;
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfCheckCast(type), this);
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
  }

  @Override
  void internalRegisterUse(UseRegistry<?> registry, DexClassAndMethod context) {
    registry.registerCheckCast(type, ignoreCompatRules);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addCheckCast(type, object(), ignoreCompatRules);
  }

  public static class Builder extends BuilderBase<Builder, CheckCast> {

    protected DexType castType;
    protected Value object;

    public Builder setCastType(DexType castType) {
      this.castType = castType;
      return this;
    }

    public Builder setObject(Value object) {
      this.object = object;
      return this;
    }

    @Override
    public CheckCast build() {
      return amend(new CheckCast(outValue, object, castType));
    }

    @Override
    public Builder self() {
      return this;
    }
  }
}
