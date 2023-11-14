// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.features.FeatureSplitBoundaryOptimizationUtils;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.optimize.Inliner.Constraint;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.TriFunction;
import com.android.tools.r8.verticalclassmerging.VerticalClassMerger.SingleTypeMapperGraphLens;

// Computes the inlining constraint for a given instruction.
public class InliningConstraints {

  private AppView<AppInfoWithLiveness> appView;

  private boolean allowStaticInterfaceMethodCalls = true;

  // Currently used only by the vertical class merger (in all other cases this is the identity).
  //
  // When merging a type A into its subtype B we need to inline A.<init>() into B.<init>().
  // Therefore, we need to be sure that A.<init>() can in fact be inlined into B.<init>() *before*
  // we merge the two classes. However, at this point, we may reject the method A.<init>() from
  // being inlined into B.<init>() only because it is not declared in the same class as B (which
  // it would be after merging A and B).
  //
  // To circumvent this problem, the vertical class merger creates a graph lens that maps the
  // type A to B, to create a temporary view of what the world would look like after class merging.
  private GraphLens graphLens;

  public InliningConstraints(AppView<AppInfoWithLiveness> appView, GraphLens graphLens) {
    this.appView = appView;
    this.graphLens = graphLens; // Note: Intentionally *not* appView.graphLens().
  }

  public AppView<AppInfoWithLiveness> getAppView() {
    return appView;
  }

  public GraphLens getGraphLens() {
    return graphLens;
  }

  public void disallowStaticInterfaceMethodCalls() {
    allowStaticInterfaceMethodCalls = false;
  }

  private boolean isVerticalClassMerging() {
    return graphLens instanceof SingleTypeMapperGraphLens;
  }

  public ConstraintWithTarget forAlwaysMaterializingUser() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forArgument() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forArrayGet() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forArrayLength() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forArrayPut() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forBinop() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forDexItemBasedConstString(DexReference type, ProgramMethod context) {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forCheckCast(DexType type, ProgramMethod context) {
    return ConstraintWithTarget.classIsVisible(context, type, appView);
  }

  public ConstraintWithTarget forConstClass(DexType type, ProgramMethod context) {
    return ConstraintWithTarget.classIsVisible(context, type, appView);
  }

  public ConstraintWithTarget forConstInstruction() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forDebugLocalRead() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forDebugLocalsChange() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forDebugPosition() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forDup() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forDup2() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forInitClass(DexType clazz, ProgramMethod context) {
    return ConstraintWithTarget.classIsVisible(context, clazz, appView);
  }

  public ConstraintWithTarget forInstanceGet(DexField field, ProgramMethod context) {
    return forFieldInstruction(field, context);
  }

  public ConstraintWithTarget forInstanceOf(DexType type, ProgramMethod context) {
    return ConstraintWithTarget.classIsVisible(context, type, appView);
  }

  public ConstraintWithTarget forInstancePut(DexField field, ProgramMethod context) {
    return forFieldInstruction(field, context);
  }

  public ConstraintWithTarget forInvoke(DexMethod method, InvokeType type, ProgramMethod context) {
    switch (type) {
      case DIRECT:
        return forInvokeDirect(method, context);
      case INTERFACE:
        return forInvokeInterface(method, context);
      case STATIC:
        return forInvokeStatic(method, context);
      case SUPER:
        return forInvokeSuper(method, context);
      case VIRTUAL:
        return forInvokeVirtual(method, context);
      case CUSTOM:
        return forInvokeCustom();
      case POLYMORPHIC:
        return forInvokePolymorphic(method, context);
      default:
        throw new Unreachable("Unexpected type: " + type);
    }
  }

  public ConstraintWithTarget forInvokeCustom() {
    // TODO(b/135965362): Test and support inlining invoke dynamic.
    return ConstraintWithTarget.NEVER;
  }

  public ConstraintWithTarget forInvokeDirect(DexMethod method, ProgramMethod context) {
    DexMethod lookup =
        graphLens.lookupMethod(method, context.getReference(), InvokeType.DIRECT).getReference();
    if (lookup.holder.isArrayType()) {
      return ConstraintWithTarget.ALWAYS;
    }
    MethodResolutionResult resolutionResult =
        appView.appInfo().unsafeResolveMethodDueToDexFormatLegacy(lookup);
    DexClassAndMethod target =
        singleTargetWhileVerticalClassMerging(
            resolutionResult, context, MethodResolutionResult::lookupInvokeDirectTarget);
    if (target != null) {
      return forResolvedMember(
          resolutionResult.getInitialResolutionHolder(), context, target.getDefinition());
    }
    return ConstraintWithTarget.NEVER;
  }

  public ConstraintWithTarget forInvokeInterface(DexMethod method, ProgramMethod context) {
    DexMethod lookup =
        graphLens.lookupMethod(method, context.getReference(), InvokeType.INTERFACE).getReference();
    return forVirtualInvoke(lookup, context, true);
  }

  public ConstraintWithTarget forInvokeMultiNewArray(DexType type, ProgramMethod context) {
    return ConstraintWithTarget.classIsVisible(context, type, appView);
  }

  public ConstraintWithTarget forNewArrayFilled(DexType type, ProgramMethod context) {
    return ConstraintWithTarget.classIsVisible(context, type, appView);
  }

  public ConstraintWithTarget forInvokePolymorphic(DexMethod method, ProgramMethod context) {
    return ConstraintWithTarget.NEVER;
  }

  public ConstraintWithTarget forInvokeStatic(DexMethod method, ProgramMethod context) {
    DexMethod lookup =
        graphLens.lookupMethod(method, context.getReference(), InvokeType.STATIC).getReference();
    if (lookup.holder.isArrayType()) {
      return ConstraintWithTarget.ALWAYS;
    }
    MethodResolutionResult resolutionResult =
        appView.appInfo().unsafeResolveMethodDueToDexFormatLegacy(lookup);
    DexClassAndMethod target =
        singleTargetWhileVerticalClassMerging(
            resolutionResult, context, MethodResolutionResult::lookupInvokeStaticTarget);
    if (!allowStaticInterfaceMethodCalls && target != null) {
      // See b/120121170.
      DexClass methodClass = appView.definitionFor(graphLens.lookupType(target.getHolderType()));
      if (methodClass != null && methodClass.isInterface() && target.getDefinition().hasCode()) {
        return ConstraintWithTarget.NEVER;
      }
    }
    if (target != null) {
      return forResolvedMember(
          resolutionResult.getInitialResolutionHolder(), context, target.getDefinition());
    }
    return ConstraintWithTarget.NEVER;
  }

  @SuppressWarnings({"ConstantConditions", "ReferenceEquality"})
  private DexClassAndMethod singleTargetWhileVerticalClassMerging(
      MethodResolutionResult resolutionResult,
      ProgramMethod context,
      TriFunction<
              MethodResolutionResult,
              DexProgramClass,
              AppView<? extends AppInfoWithClassHierarchy>,
              DexClassAndMethod>
          lookup) {
    if (!resolutionResult.isSingleResolution()) {
      return null;
    }
    DexClassAndMethod lookupResult = lookup.apply(resolutionResult, context.getHolder(), appView);
    if (!isVerticalClassMerging() || lookupResult != null) {
      return lookupResult;
    }
    assert isVerticalClassMerging();
    assert graphLens.lookupType(context.getHolder().superType) == context.getHolderType();
    DexProgramClass superContext =
        appView.programDefinitionFor(context.getHolder().superType, context);
    if (superContext == null) {
      return null;
    }
    DexClassAndMethod alternativeDexEncodedMethod =
        lookup.apply(resolutionResult, superContext, appView);
    if (alternativeDexEncodedMethod != null
        && alternativeDexEncodedMethod.getHolderType() == superContext.type) {
      return alternativeDexEncodedMethod;
    }
    return null;
  }

  public ConstraintWithTarget forInvokeSuper(DexMethod method, ProgramMethod context) {
    // The semantics of invoke super depend on the context.
    return new ConstraintWithTarget(Constraint.SAMECLASS, context.getHolderType());
  }

  public ConstraintWithTarget forInvokeVirtual(DexMethod method, ProgramMethod context) {
    DexMethod lookup =
        graphLens.lookupMethod(method, context.getReference(), InvokeType.VIRTUAL).getReference();
    return forVirtualInvoke(lookup, context, false);
  }

  public ConstraintWithTarget forJumpInstruction() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forLoad() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forMonitor() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forMove() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forMoveException() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forNewArrayEmpty(DexType type, ProgramMethod context) {
    return ConstraintWithTarget.classIsVisible(context, type, appView);
  }

  public ConstraintWithTarget forRecordFieldValues() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forNewArrayFilledData() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forNewInstance(DexType type, ProgramMethod context) {
    return ConstraintWithTarget.classIsVisible(context, type, appView);
  }

  public ConstraintWithTarget forNewUnboxedEnumInstance(DexType type, ProgramMethod context) {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forAssume() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forPop() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forReturn() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forStaticGet(DexField field, ProgramMethod context) {
    return forFieldInstruction(field, context);
  }

  public ConstraintWithTarget forStaticPut(DexField field, ProgramMethod context) {
    return forFieldInstruction(field, context);
  }

  public ConstraintWithTarget forStore() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forSwap() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forThrow() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forUnop() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forConstMethodHandle() {
    return ConstraintWithTarget.NEVER;
  }

  public ConstraintWithTarget forConstMethodType() {
    return ConstraintWithTarget.NEVER;
  }

  private ConstraintWithTarget forFieldInstruction(DexField field, ProgramMethod context) {
    DexField lookup = graphLens.lookupField(field);
    FieldResolutionResult fieldResolutionResult = appView.appInfo().resolveField(lookup);
    if (fieldResolutionResult.isMultiFieldResolutionResult()) {
      return ConstraintWithTarget.NEVER;
    }
    return forResolvedMember(
        fieldResolutionResult.getInitialResolutionHolder(),
        context,
        fieldResolutionResult.getResolvedField());
  }

  private ConstraintWithTarget forVirtualInvoke(
      DexMethod method, ProgramMethod context, boolean isInterface) {
    if (method.holder.isArrayType()) {
      return ConstraintWithTarget.ALWAYS;
    }

    // Perform resolution and derive inlining constraints based on the accessibility of the
    // resolution result.
    MethodResolutionResult resolutionResult =
        appView.appInfo().resolveMethodLegacy(method, isInterface);
    if (!resolutionResult.isVirtualTarget()) {
      return ConstraintWithTarget.NEVER;
    }

    return forResolvedMember(
        resolutionResult.getInitialResolutionHolder(), context, resolutionResult.getSingleTarget());
  }

  @SuppressWarnings("ReferenceEquality")
  private ConstraintWithTarget forResolvedMember(
      DexClass initialResolutionHolder,
      ProgramMethod context,
      DexEncodedMember<?, ?> resolvedMember) {
    if (resolvedMember == null) {
      // This will fail at runtime.
      return ConstraintWithTarget.NEVER;
    }
    ConstraintWithTarget featureSplitInliningConstraint =
        FeatureSplitBoundaryOptimizationUtils.getInliningConstraintForResolvedMember(
            context, resolvedMember, appView);
    assert featureSplitInliningConstraint == ConstraintWithTarget.ALWAYS
        || featureSplitInliningConstraint == ConstraintWithTarget.NEVER;
    if (featureSplitInliningConstraint == ConstraintWithTarget.NEVER) {
      return featureSplitInliningConstraint;
    }
    DexType resolvedHolder = graphLens.lookupType(resolvedMember.getHolderType());
    assert initialResolutionHolder != null;
    ConstraintWithTarget memberConstraintWithTarget =
        ConstraintWithTarget.deriveConstraint(
            context, resolvedHolder, resolvedMember.getAccessFlags(), appView);
    // We also have to take the constraint of the initial resolution holder into account.
    ConstraintWithTarget classConstraintWithTarget =
        ConstraintWithTarget.deriveConstraint(
            context, initialResolutionHolder.type, initialResolutionHolder.accessFlags, appView);
    return ConstraintWithTarget.meet(
        classConstraintWithTarget, memberConstraintWithTarget, appView);
  }
}
