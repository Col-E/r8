// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppInfo.ResolutionResult;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.ir.optimize.Inliner.Constraint;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import java.util.Collection;

// Computes the inlining constraint for a given instruction.
public class InliningConstraints {

  private AppInfoWithLiveness appInfo;

  // Currently used only by the vertical class merger (in all other cases this is the identity).
  //
  // When merging a type A into its subtype B we need to inline A.<init>() into B.<init>().
  // Therefore, we need to be sure that A.<init>() can in fact be inlined into B.<init>() *before*
  // we merge the two classes. However, at this point, we may reject the method A.<init>() from
  // being inlined into B.<init>() only because it is not declared in the same class as B (which
  // it would be after merging A and B).
  //
  // To circumvent this problem, the vertical class merger creates a graph lense that maps the
  // type A to B, to create a temporary view of what the world would look like after class merging.
  private GraphLense graphLense;

  public InliningConstraints(AppInfoWithLiveness appInfo) {
    this(appInfo, GraphLense.getIdentityLense());
  }

  public InliningConstraints(AppInfoWithLiveness appInfo, GraphLense graphLense) {
    assert graphLense.isContextFreeForMethods();
    this.appInfo = appInfo;
    this.graphLense = graphLense;
  }

  public Constraint forAlwaysMaterializingUser() {
    return Constraint.ALWAYS;
  }

  public Constraint forArgument() {
    return Constraint.ALWAYS;
  }

  public Constraint forArrayGet() {
    return Constraint.ALWAYS;
  }

  public Constraint forArrayLength() {
    return Constraint.ALWAYS;
  }

  public Constraint forArrayPut() {
    return Constraint.ALWAYS;
  }

  public Constraint forBinop() {
    return Constraint.ALWAYS;
  }

  public Constraint forCheckCast(DexType type, DexType invocationContext) {
    return Constraint.classIsVisible(invocationContext, type, appInfo);
  }

  public Constraint forConstClass(DexType type, DexType invocationContext) {
    return Constraint.classIsVisible(invocationContext, type, appInfo);
  }

  public Constraint forConstInstruction() {
    return Constraint.ALWAYS;
  }

  public Constraint forDebugLocalRead() {
    return Constraint.ALWAYS;
  }

  public Constraint forDebugLocalsChange() {
    return Constraint.ALWAYS;
  }

  public Constraint forDebugPosition() {
    return Constraint.ALWAYS;
  }

  public Constraint forInstanceGet(DexField field, DexType invocationContext) {
    DexField lookup = graphLense.lookupField(field);
    return forFieldInstruction(
        lookup, appInfo.lookupInstanceTarget(lookup.clazz, lookup), invocationContext);
  }

  public Constraint forInstanceOf(DexType type, DexType invocationContext) {
    return Constraint.classIsVisible(invocationContext, type, appInfo);
  }

  public Constraint forInvokeCustom() {
    return Constraint.NEVER;
  }

  public Constraint forInstancePut(DexField field, DexType invocationContext) {
    DexField lookup = graphLense.lookupField(field);
    return forFieldInstruction(
        lookup, appInfo.lookupInstanceTarget(lookup.clazz, lookup), invocationContext);
  }

  public Constraint forInvokeDirect(DexMethod method, DexType invocationContext) {
    DexMethod lookup = graphLense.lookupMethod(method);
    return forSingleTargetInvoke(lookup, appInfo.lookupDirectTarget(lookup), invocationContext);
  }

  public Constraint forInvokeInterface(DexMethod method, DexType invocationContext) {
    DexMethod lookup = graphLense.lookupMethod(method);
    return forVirtualInvoke(lookup, appInfo.lookupInterfaceTargets(lookup), invocationContext);
  }

  public Constraint forInvokeMultiNewArray(DexType type, DexType invocationContext) {
    return Constraint.classIsVisible(invocationContext, type, appInfo);
  }

  public Constraint forInvokeNewArray(DexType type, DexType invocationContext) {
    return Constraint.classIsVisible(invocationContext, type, appInfo);
  }

  public Constraint forInvokePolymorphic(DexMethod method, DexType invocationContext) {
    return Constraint.NEVER;
  }

  public Constraint forInvokeStatic(DexMethod method, DexType invocationContext) {
    DexMethod lookup = graphLense.lookupMethod(method);
    return forSingleTargetInvoke(lookup, appInfo.lookupStaticTarget(lookup), invocationContext);
  }

  public Constraint forInvokeSuper(DexMethod method, DexType invocationContext) {
    // The semantics of invoke super depend on the context.
    return Constraint.SAMECLASS;
  }

  public Constraint forInvokeVirtual(DexMethod method, DexType invocationContext) {
    DexMethod lookup = graphLense.lookupMethod(method);
    return forVirtualInvoke(lookup, appInfo.lookupVirtualTargets(lookup), invocationContext);
  }

  public Constraint forJumpInstruction() {
    return Constraint.ALWAYS;
  }

  public Constraint forLoad() {
    return Constraint.ALWAYS;
  }

  public Constraint forMonitor() {
    // Conservative choice.
    return Constraint.NEVER;
  }

  public Constraint forMove() {
    return Constraint.ALWAYS;
  }

  public Constraint forMoveException() {
    // TODO(64432527): Revisit this constraint.
    return Constraint.NEVER;
  }

  public Constraint forNewArrayEmpty(DexType type, DexType invocationContext) {
    return Constraint.classIsVisible(invocationContext, type, appInfo);
  }

  public Constraint forNewArrayFilledData() {
    return Constraint.ALWAYS;
  }

  public Constraint forNewInstance(DexType type, DexType invocationContext) {
    return Constraint.classIsVisible(invocationContext, type, appInfo);
  }

  public Constraint forNonNull() {
    return Constraint.ALWAYS;
  }

  public Constraint forPop() {
    return Constraint.ALWAYS;
  }

  public Constraint forReturn() {
    return Constraint.ALWAYS;
  }

  public Constraint forStaticGet(DexField field, DexType invocationContext) {
    DexField lookup = graphLense.lookupField(field);
    return forFieldInstruction(
        lookup, appInfo.lookupStaticTarget(lookup.clazz, lookup), invocationContext);
  }

  public Constraint forStaticPut(DexField field, DexType invocationContext) {
    DexField lookup = graphLense.lookupField(field);
    return forFieldInstruction(
        lookup, appInfo.lookupStaticTarget(lookup.clazz, lookup), invocationContext);
  }

  public Constraint forStore() {
    return Constraint.ALWAYS;
  }

  public Constraint forThrow() {
    return Constraint.ALWAYS;
  }

  public Constraint forUnop() {
    return Constraint.ALWAYS;
  }

  private Constraint forFieldInstruction(
      DexField field, DexEncodedField target, DexType invocationContext) {
    // Resolve the field if possible and decide whether the instruction can inlined.
    DexType fieldHolder = graphLense.lookupType(field.clazz);
    DexClass fieldClass = appInfo.definitionFor(fieldHolder);
    if (target != null && fieldClass != null) {
      Constraint fieldConstraint =
          Constraint.deriveConstraint(invocationContext, fieldHolder, target.accessFlags, appInfo);
      Constraint classConstraint =
          Constraint.deriveConstraint(
              invocationContext, fieldHolder, fieldClass.accessFlags, appInfo);
      return Constraint.min(fieldConstraint, classConstraint);
    }
    return Constraint.NEVER;
  }

  private Constraint forSingleTargetInvoke(
      DexMethod method, DexEncodedMethod target, DexType invocationContext) {
    if (method.holder.isArrayType()) {
      return Constraint.ALWAYS;
    }
    if (target != null) {
      DexType methodHolder = graphLense.lookupType(target.method.holder);
      DexClass methodClass = appInfo.definitionFor(methodHolder);
      if (methodClass != null) {
        Constraint methodConstraint =
            Constraint.deriveConstraint(
                invocationContext, methodHolder, target.accessFlags, appInfo);
        // We also have to take the constraint of the enclosing class into account.
        Constraint classConstraint =
            Constraint.deriveConstraint(
                invocationContext, methodHolder, methodClass.accessFlags, appInfo);
        return Constraint.min(methodConstraint, classConstraint);
      }
    }
    return Constraint.NEVER;
  }

  private Constraint forVirtualInvoke(
      DexMethod method, Collection<DexEncodedMethod> targets, DexType invocationContext) {
    if (method.holder.isArrayType()) {
      return Constraint.ALWAYS;
    }
    if (targets == null) {
      return Constraint.NEVER;
    }

    // Perform resolution and derive inlining constraints based on the accessibility of the
    // resolution result.
    ResolutionResult resolutionResult = appInfo.resolveMethod(method.holder, method);
    DexEncodedMethod resolutionTarget = resolutionResult.asResultOfResolve();
    if (resolutionTarget == null) {
      // This will fail at runtime.
      return Constraint.NEVER;
    }

    DexType methodHolder = graphLense.lookupType(resolutionTarget.method.holder);
    DexClass methodClass = appInfo.definitionFor(methodHolder);
    assert methodClass != null;
    Constraint methodConstraint =
        Constraint.deriveConstraint(
            invocationContext, methodHolder, resolutionTarget.accessFlags, appInfo);
    // We also have to take the constraint of the enclosing class of the resolution result
    // into account. We do not allow inlining this method if it is calling something that
    // is inaccessible. Inlining in that case could move the code to another package making a
    // call succeed that should not succeed. Conversely, if the resolution result is accessible,
    // we have to make sure that inlining cannot make it inaccessible.
    Constraint classConstraint =
        Constraint.deriveConstraint(
            invocationContext, methodHolder, methodClass.accessFlags, appInfo);
    Constraint result = Constraint.min(methodConstraint, classConstraint);
    if (result == Constraint.NEVER) {
      return result;
    }

    // For each of the actual potential targets, derive constraints based on the accessibility
    // of the method itself.
    for (DexEncodedMethod target : targets) {
      methodHolder = graphLense.lookupType(target.method.holder);
      assert appInfo.definitionFor(methodHolder) != null;
      methodConstraint =
          Constraint.deriveConstraint(invocationContext, methodHolder, target.accessFlags, appInfo);
      result = Constraint.min(result, methodConstraint);
      if (result == Constraint.NEVER) {
        return result;
      }
    }

    return result;
  }
}
