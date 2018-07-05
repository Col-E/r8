// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppInfo.ResolutionResult;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.ir.optimize.Inliner.Constraint;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import java.util.Collection;

// Computes the inlining constraint for a given instruction.
//
// TODO(christofferqa): This class is incomplete.
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
    this.appInfo = appInfo;
    this.graphLense = graphLense;
  }

  public Constraint forCheckCast(DexType type, DexType invocationContext) {
    return Constraint.classIsVisible(invocationContext, type, appInfo);
  }

  public Constraint forInvokeDirect(DexMethod method, DexType invocationContext) {
    return forSingleTargetInvoke(method, appInfo.lookupDirectTarget(method), invocationContext);
  }

  public Constraint forInvokeInterface(DexMethod method, DexType invocationContext) {
    return forVirtualInvoke(method, appInfo.lookupInterfaceTargets(method), invocationContext);
  }

  public Constraint forInvokePolymorphic(DexMethod method, DexType invocationContext) {
    return Constraint.NEVER;
  }

  public Constraint forInvokeStatic(DexMethod method, DexType invocationContext) {
    return forSingleTargetInvoke(method, appInfo.lookupStaticTarget(method), invocationContext);
  }

  public Constraint forInvokeSuper(DexMethod method, DexType invocationContext) {
    // The semantics of invoke super depend on the context.
    return Constraint.SAMECLASS;
  }

  public Constraint forInvokeVirtual(DexMethod method, DexType invocationContext) {
    return forVirtualInvoke(method, appInfo.lookupVirtualTargets(method), invocationContext);
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
