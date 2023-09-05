// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexNewInstance;
import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.AnalysisAssumption;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.Query;
import com.android.tools.r8.ir.analysis.VerifyTypesHelper;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class NewInstance extends Instruction {

  public final DexType clazz;
  private boolean allowSpilling = true;

  public NewInstance(DexType clazz, Value dest) {
    super(dest);
    assert clazz != null;
    this.clazz = clazz;
  }

  public static Builder builder() {
    return new Builder();
  }

  public DexType getType() {
    return clazz;
  }

  public InvokeDirect getUniqueConstructorInvoke(DexItemFactory dexItemFactory) {
    return IRCodeUtils.getUniqueConstructorInvoke(outValue(), dexItemFactory);
  }

  @Override
  public int opcode() {
    return Opcodes.NEW_INSTANCE;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public Value dest() {
    return outValue;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    int dest = builder.allocatedRegister(dest(), getNumber());
    builder.add(this, new DexNewInstance(dest, clazz));
  }

  @Override
  public String toString() {
    return super.toString() + " " + clazz;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isNewInstance() && other.asNewInstance().clazz == clazz;
  }

  @Override
  public int maxInValueRegister() {
    assert false : "NewInstance has no register arguments";
    return 0;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public boolean instructionTypeCanThrow() {
    // Creating a new instance can throw if the type is not found, or on out-of-memory.
    return true;
  }

  @Override
  public boolean isNewInstance() {
    return true;
  }

  @Override
  public NewInstance asNewInstance() {
    return this;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forNewInstance(clazz, context);
  }

  @Override
  public boolean hasInvariantOutType() {
    return true;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.storeOutValue(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfNew(clazz), this);
  }

  @Override
  public DexType computeVerificationType(AppView<?> appView, TypeVerificationHelper helper) {
    return clazz;
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return TypeElement.fromDexType(clazz, Nullability.definitelyNotNull(), appView);
  }

  @Override
  public boolean definitelyTriggersClassInitialization(
      DexType clazz,
      ProgramMethod context,
      AppView<AppInfoWithLiveness> appView,
      Query mode,
      AnalysisAssumption assumption) {
    return ClassInitializationAnalysis.InstructionUtils.forNewInstance(
        this, clazz, appView, mode, assumption);
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean instructionMayHaveSideEffects(
      AppView<?> appView,
      ProgramMethod context,
      AbstractValueSupplier abstractValueSupplier,
      SideEffectAssumption assumption) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    if (!appView.enableWholeProgramOptimizations()) {
      return !(dexItemFactory.libraryTypesAssumedToBePresent.contains(clazz)
          && dexItemFactory.libraryClassesWithoutStaticInitialization.contains(clazz));
    }

    assert appView.appInfo().hasClassHierarchy();
    AppView<? extends AppInfoWithClassHierarchy> appViewWithClassHierarchy =
        appView.withClassHierarchy();

    if (clazz.isPrimitiveType() || clazz.isArrayType()) {
      assert false : "Unexpected new-instance instruction with primitive or array type";
      return true;
    }

    DexClass definition = appView.definitionFor(clazz);
    if (definition == null || definition.isAbstract() || !definition.isResolvable(appView)) {
      return true;
    }

    // Verify that the instruction does not lead to an IllegalAccessError.
    if (AccessControl.isClassAccessible(definition, context, appViewWithClassHierarchy)
        .isPossiblyFalse()) {
      return true;
    }

    // Verify that the new-instance instruction won't lead to class initialization.
    if (definition.classInitializationMayHaveSideEffectsInContext(
        appViewWithClassHierarchy, context)) {
      return true;
    }

    // Verify that the object does not have a finalizer.
    MethodResolutionResult finalizeResolutionResult =
        appViewWithClassHierarchy
            .appInfo()
            .resolveMethodOnClassLegacy(clazz, dexItemFactory.objectMembers.finalize);
    if (finalizeResolutionResult.isSingleResolution()) {
      DexMethod finalizeMethod = finalizeResolutionResult.getSingleTarget().getReference();
      if (finalizeMethod != dexItemFactory.enumMembers.finalize
          && finalizeMethod != dexItemFactory.objectMembers.finalize) {
        return true;
      }
    }

    return false;
  }

  public void markNoSpilling() {
    allowSpilling = false;
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean isSpillingAllowed() {
    return allowSpilling;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    if (appView.enableWholeProgramOptimizations()) {
      // In R8, check if the class initialization of the holder or any of its ancestor types may
      // have side effects.
      return clazz.classInitializationMayHaveSideEffectsInContext(appView, context);
    } else {
      // In D8, this instruction may trigger class initialization if the holder of the field is
      // different from the current context.
      return clazz != context.getHolderType();
    }
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean verifyTypes(
      AppView<?> appView, ProgramMethod context, VerifyTypesHelper verifyTypesHelper) {
    TypeElement type = getOutType();
    assert type.isClassType();
    assert type.asClassType().getClassType() == clazz || appView.options().testing.allowTypeErrors;
    assert type.isDefinitelyNotNull();
    return true;
  }

  @Override
  void internalRegisterUse(UseRegistry<?> registry, DexClassAndMethod context) {
    registry.registerNewInstance(clazz);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addNewInstance(clazz);
  }

  public static class Builder extends BuilderBase<Builder, NewInstance> {

    private DexType type;

    public Builder setType(DexType type) {
      this.type = type;
      return this;
    }

    @Override
    public NewInstance build() {
      return amend(new NewInstance(type, outValue));
    }

    @Override
    public Builder self() {
      return this;
    }
  }
}
