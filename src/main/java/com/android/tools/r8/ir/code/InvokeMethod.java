// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeEnvironment;
import com.android.tools.r8.ir.optimize.Inliner.Constraint;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;
import com.android.tools.r8.ir.optimize.InliningOracle;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import java.util.Collection;
import java.util.List;

public abstract class InvokeMethod extends Invoke {

  private final DexMethod method;

  public InvokeMethod(DexMethod target, Value result, List<Value> arguments) {
    super(result, arguments);
    this.method = target;
  }

  @Override
  public DexType getReturnType() {
    return method.proto.returnType;
  }

  public DexMethod getInvokedMethod() {
    return method;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return method == other.asInvokeMethod().getInvokedMethod();
  }

  @Override
  public int compareNonValueParts(Instruction other) {
    return getInvokedMethod().slowCompareTo(other.asInvokeMethod().getInvokedMethod());
  }

  @Override
  public String toString() {
    return super.toString() + "; method: " + method.toSourceString();
  }

  @Override
  public boolean isInvokeMethod() {
    return true;
  }

  @Override
  public InvokeMethod asInvokeMethod() {
    return this;
  }

  // TODO(jsjeon): merge lookupSingleTarget and computeSingleTarget.
  public abstract DexEncodedMethod lookupSingleTarget(AppInfoWithLiveness appInfo,
      DexType invocationContext);

  public abstract Collection<DexEncodedMethod> lookupTargets(AppInfoWithSubtyping appInfo,
      DexType invocationContext);

  // This method is used for inlining and/or other optimizations, such as value propagation.
  // It returns the target method iff this invoke has only one target.
  public DexEncodedMethod computeSingleTarget(AppInfoWithLiveness appInfo) {
    // TODO(jsjeon): revisit all usage of this method and pass proper invocation context.
    return computeSingleTarget(appInfo, TypeAnalysis.getDefaultTypeEnvironment(), null);
  }

  // TODO(b/72693244): By annotating type lattice to value, avoid passing type env.
  public DexEncodedMethod computeSingleTarget(
      AppInfoWithLiveness appInfo, TypeEnvironment typeEnvironment, DexType invocationContext) {
    // In subclasses, e.g., invoke-virtual or invoke-super, use a narrower receiver type by using
    // receiver type and type environment or invocation context---where the current invoke is.
    return lookupSingleTarget(appInfo, appInfo.dexItemFactory.objectType);
  }

  @Override
  public abstract Constraint inliningConstraint(AppInfoWithLiveness info,
      DexType invocationContext);

  protected Constraint inliningConstraintForSinlgeTargetInvoke(AppInfoWithLiveness info,
      DexType invocationContext) {
    if (method.holder.isArrayType()) {
      return Constraint.ALWAYS;
    }
    DexEncodedMethod target = lookupSingleTarget(info, invocationContext);
    if (target != null) {
      DexType methodHolder = target.method.holder;
      DexClass methodClass = info.definitionFor(methodHolder);
      if ((methodClass != null)) {
        Constraint methodConstraint = Constraint
            .deriveConstraint(invocationContext, methodHolder, target.accessFlags, info);
        // We also have to take the constraint of the enclosing class into account.
        Constraint classConstraint = Constraint
            .deriveConstraint(invocationContext, methodHolder, methodClass.accessFlags, info);
        return Constraint.min(methodConstraint, classConstraint);
      }
    }
    return Constraint.NEVER;
  }

  protected Constraint inliningConstraintForVirtualInvoke(AppInfoWithSubtyping info,
      DexType invocationContext) {
    if (method.holder.isArrayType()) {
      return Constraint.ALWAYS;
    }
    Collection<DexEncodedMethod> targets = lookupTargets(info, invocationContext);
    if (targets == null || targets.isEmpty()) {
      return Constraint.NEVER;
    }
    Constraint result = Constraint.ALWAYS;
    for (DexEncodedMethod target : targets) {
      DexType methodHolder = target.method.holder;
      DexClass methodClass = info.definitionFor(methodHolder);
      if ((methodClass != null)) {
        Constraint methodConstraint = Constraint
            .deriveConstraint(invocationContext, methodHolder, target.accessFlags, info);
        result = Constraint.min(result, methodConstraint);
        // We also have to take the constraint of the enclosing class into account.
        Constraint classConstraint = Constraint
            .deriveConstraint(invocationContext, methodHolder, methodClass.accessFlags, info);
        result = Constraint.min(result, classConstraint);
      }
      if (result == Constraint.NEVER) {
        break;
      }
    }
    return result;
  }

  public abstract InlineAction computeInlining(InliningOracle decider, DexType invocationContext);

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.loadInValues(this, it);
    if (method.proto.returnType.isVoidType()) {
      return;
    }
    if (outValue == null) {
      helper.popOutType(method.proto.returnType, this, it);
    } else {
      assert outValue.isUsed();
      helper.storeOutValue(this, it);
    }
  }

  @Override
  public boolean hasInvariantVerificationType() {
    return true;
  }

  @Override
  public DexType computeVerificationType(TypeVerificationHelper helper) {
    return getInvokedMethod().proto.returnType;
  }

}
