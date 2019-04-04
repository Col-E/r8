// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import static com.android.tools.r8.optimize.MemberRebindingAnalysis.isMemberVisibleFromOriginalContext;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.AnalysisAssumption;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.Query;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;

public class NewInstance extends Instruction {

  public final DexType clazz;
  private boolean allowSpilling = true;

  public NewInstance(DexType clazz, Value dest) {
    super(dest);
    assert clazz != null;
    this.clazz = clazz;
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
    builder.add(this, new com.android.tools.r8.code.NewInstance(dest, clazz));
  }

  @Override
  public String toString() {
    return super.toString() + " " + clazz;
  }

  @Override
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
      InliningConstraints inliningConstraints, DexType invocationContext) {
    return inliningConstraints.forNewInstance(clazz, invocationContext);
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
    builder.add(new CfNew(clazz));
  }

  @Override
  public DexType computeVerificationType(
      AppView<? extends AppInfo> appView, TypeVerificationHelper helper) {
    return clazz;
  }

  @Override
  public TypeLatticeElement evaluate(AppView<? extends AppInfo> appView) {
    return TypeLatticeElement.fromDexType(clazz, Nullability.definitelyNotNull(), appView);
  }

  @Override
  public boolean definitelyTriggersClassInitialization(
      DexType clazz,
      DexType context,
      AppView<? extends AppInfo> appView,
      Query mode,
      AnalysisAssumption assumption) {
    return ClassInitializationAnalysis.InstructionUtils.forNewInstance(
        this, clazz, appView, mode, assumption);
  }

  @Override
  public boolean instructionMayHaveSideEffects(
      AppView<? extends AppInfo> appView, DexType context) {
    if (!appView.enableWholeProgramOptimizations()) {
      return true;
    }

    if (clazz.isPrimitiveType() || clazz.isArrayType()) {
      assert false : "Unexpected new-instance instruction with primitive or array type";
      return true;
    }

    DexClass definition = appView.definitionFor(clazz);
    if (definition == null || definition.accessFlags.isAbstract()) {
      return true;
    }

    if (definition.isLibraryClass()
        && !appView.dexItemFactory().libraryTypesAssumedToBePresent.contains(clazz)) {
      return true;
    }

    // Verify that the instruction does not lead to an IllegalAccessError.
    if (appView.appInfo().hasSubtyping()
        && !isMemberVisibleFromOriginalContext(
            appView, context, definition.type, definition.accessFlags)) {
      return true;
    }

    // Verify that the new-instance instruction won't lead to class initialization.
    if (definition.classInitializationMayHaveSideEffects(
        appView,
        // Types that are a super type of `context` are guaranteed to be initialized already.
        type -> appView.isSubtype(context, type).isTrue())) {
      return true;
    }

    return false;
  }

  @Override
  public boolean canBeDeadCode(AppView<? extends AppInfo> appView, IRCode code) {
    return !instructionMayHaveSideEffects(appView, code.method.method.holder);
  }

  public void markNoSpilling() {
    allowSpilling = false;
  }

  public boolean isSpillingAllowed() {
    return allowSpilling;
  }
}
