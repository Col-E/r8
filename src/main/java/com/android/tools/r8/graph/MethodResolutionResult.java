// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.LookupResult.LookupResultSuccess;
import com.android.tools.r8.graph.LookupResult.LookupResultSuccess.LookupResultCollectionState;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeSuper;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.ir.optimize.info.DefaultMethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MethodResolutionOptimizationInfoCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.InstantiatedObject;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public abstract class MethodResolutionResult
    extends MemberResolutionResult<DexEncodedMethod, DexMethod> {

  public static UnknownMethodResolutionResult unknown() {
    return UnknownMethodResolutionResult.get();
  }

  @Override
  public boolean isMethodResolutionResult() {
    return true;
  }

  @Override
  public MethodResolutionResult asMethodResolutionResult() {
    return this;
  }

  /**
   * Returns true if resolution succeeded *and* the resolved method has a known definition.
   *
   * <p>Note that {@code !isSingleResolution() && !isFailedResolution()} can be true. In that case
   * that resolution has succeeded, but the definition of the resolved method is unknown. In
   * particular this is the case for the clone() method on arrays.
   */
  public boolean isSingleResolution() {
    return false;
  }

  /** Returns non-null if isSingleResolution() is true, otherwise null. */
  public SingleResolutionResult<?> asSingleResolution() {
    return null;
  }

  @Override
  public boolean isSuccessfulMemberResolutionResult() {
    return false;
  }

  @Override
  public SuccessfulMemberResolutionResult<DexEncodedMethod, DexMethod>
      asSuccessfulMemberResolutionResult() {
    return null;
  }

  public boolean isIncompatibleClassChangeErrorResult() {
    return false;
  }

  public boolean isNoSuchMethodResultDueToMultipleClassDefinitions() {
    return false;
  }

  public final boolean isNoSuchMethodErrorResult(
      DexClass context, AppView<? extends AppInfoWithClassHierarchy> appView) {
    return isNoSuchMethodErrorResult(context, appView, appView.appInfo());
  }

  public boolean isNoSuchMethodErrorResult(
      DexClass context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
    return false;
  }

  public boolean internalIsInstanceOfNoSuchMethodResult() {
    return false;
  }

  public final boolean isIllegalAccessErrorResult(
      DexClass context, AppView<? extends AppInfoWithClassHierarchy> appView) {
    return isIllegalAccessErrorResult(context, appView, appView.appInfo());
  }

  public boolean isIllegalAccessErrorResult(
      DexClass context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
    return false;
  }

  public boolean isClassNotFoundResult() {
    return false;
  }

  public boolean isArrayCloneMethodResult() {
    return false;
  }

  public boolean isMultiMethodResolutionResult() {
    return false;
  }

  public final void forEachMethodResolutionResult(Consumer<MethodResolutionResult> resultConsumer) {
    visitMethodResolutionResults(resultConsumer, resultConsumer, resultConsumer, resultConsumer);
  }

  /** Returns non-null if isFailedResolution() is true, otherwise null. */
  public FailedResolutionResult asFailedResolution() {
    return null;
  }

  public NoSuchMethodResult asNoSuchMethodResult() {
    return null;
  }

  public DexClass getResolvedHolder() {
    return null;
  }

  public DexEncodedMethod getResolvedMethod() {
    return null;
  }

  /**
   * Short-hand to get the single resolution method if resolution finds it, null otherwise.
   *
   * @deprecated Use {@link #getResolvedMethod()}.
   */
  @Deprecated
  @SuppressWarnings("InlineMeSuggester")
  public final DexEncodedMethod getSingleTarget() {
    return isSingleResolution() ? asSingleResolution().getResolvedMethod() : null;
  }

  public DexClass getInitialResolutionHolder() {
    return null;
  }

  public ProgramMethod getResolvedProgramMethod() {
    return null;
  }

  @Override
  public DexClassAndMethod getResolutionPair() {
    return null;
  }

  public abstract OptionalBool isAccessibleForVirtualDispatchFrom(
      ProgramDefinition context, AppView<? extends AppInfoWithClassHierarchy> appView);

  public abstract boolean isVirtualTarget();

  /** Lookup the single target of an invoke-special on this resolution result if possible. */
  public abstract DexClassAndMethod lookupInvokeSpecialTarget(
      DexProgramClass context, AppView<? extends AppInfoWithClassHierarchy> appView);

  public final DexClassAndMethod lookupInvokeSuperTarget(
      DexProgramClass context, AppView<? extends AppInfoWithClassHierarchy> appView) {
    return lookupInvokeSuperTarget(context, appView, appView.appInfo());
  }

  /** Lookup the single target of an invoke-super on this resolution result if possible. */
  public abstract DexClassAndMethod lookupInvokeSuperTarget(
      DexProgramClass context, AppView<?> appView, AppInfoWithClassHierarchy appInfo);

  /** Lookup the single target of an invoke-direct on this resolution result if possible. */
  public final DexClassAndMethod lookupInvokeDirectTarget(
      DexProgramClass context, AppView<? extends AppInfoWithClassHierarchy> appView) {
    return lookupInvokeDirectTarget(context, appView, appView.appInfo());
  }

  public abstract DexClassAndMethod lookupInvokeDirectTarget(
      DexProgramClass context, AppView<?> appView, AppInfoWithClassHierarchy appInfo);

  /** Lookup the single target of an invoke-static on this resolution result if possible. */
  public final DexClassAndMethod lookupInvokeStaticTarget(
      DexProgramClass context, AppView<? extends AppInfoWithClassHierarchy> appView) {
    return lookupInvokeStaticTarget(context, appView, appView.appInfo());
  }

  public abstract DexClassAndMethod lookupInvokeStaticTarget(
      DexProgramClass context, AppView<?> appView, AppInfoWithClassHierarchy appInfo);

  public abstract LookupResult lookupVirtualDispatchTargets(
      DexProgramClass context,
      AppView<? extends AppInfoWithClassHierarchy> appView,
      InstantiatedSubTypeInfo instantiatedInfo,
      PinnedPredicate pinnedPredicate);

  public final LookupResult lookupVirtualDispatchTargets(
      DexProgramClass context, AppView<AppInfoWithLiveness> appView) {
    AppInfoWithLiveness appInfo = appView.appInfo();
    return lookupVirtualDispatchTargets(
        context, appView, appInfo, appInfo::isPinnedNotProgramOrLibraryOverride);
  }

  public abstract LookupResult lookupVirtualDispatchTargets(
      DexProgramClass context,
      AppView<AppInfoWithLiveness> appView,
      DexProgramClass refinedReceiverUpperBound,
      DexProgramClass refinedReceiverLowerBound);

  public abstract LookupTarget lookupVirtualDispatchTarget(
      InstantiatedObject instance, AppInfoWithClassHierarchy appInfo);

  public abstract LookupMethodTarget lookupVirtualDispatchTarget(
      DexClass dynamicInstance, AppInfoWithClassHierarchy appInfo);

  public abstract LookupTarget lookupVirtualDispatchTarget(
      LambdaDescriptor lambdaInstance,
      AppInfoWithClassHierarchy appInfo,
      Consumer<DexType> typeCausingFailureConsumer,
      Consumer<? super DexEncodedMethod> methodCausingFailureConsumer);

  public abstract void visitMethodResolutionResults(
      Consumer<? super SingleResolutionResult<? extends ProgramOrClasspathClass>>
          programOrClasspathConsumer,
      Consumer<? super SingleLibraryResolutionResult> libraryResultConsumer,
      Consumer<? super ArrayCloneMethodResult> cloneResultConsumer,
      Consumer<? super FailedResolutionResult> failedResolutionConsumer);

  public void visitMethodResolutionResults(
      Consumer<? super MethodResolutionResult> resultConsumer,
      Consumer<? super FailedResolutionResult> failedResolutionConsumer) {
    visitMethodResolutionResults(
        resultConsumer, resultConsumer, resultConsumer, failedResolutionConsumer);
  }

  public boolean hasProgramResult() {
    return false;
  }

  public SingleClasspathResolutionResult asSingleClasspathResolutionResult() {
    return null;
  }

  protected SingleProgramResolutionResult asSingleProgramResolutionResult() {
    return null;
  }

  public static SingleResolutionResult<?> createSingleResolutionResult(
      DexClass initialResolutionHolder, DexClass holder, DexEncodedMethod definition) {
    if (holder.isLibraryClass()) {
      return new SingleLibraryResolutionResult(
          initialResolutionHolder, holder.asLibraryClass(), definition);
    } else if (holder.isClasspathClass()) {
      return new SingleClasspathResolutionResult(
          initialResolutionHolder, holder.asClasspathClass(), definition);
    } else {
      assert holder.isProgramClass();
      return new SingleProgramResolutionResult(
          initialResolutionHolder, holder.asProgramClass(), definition);
    }
  }

  /** Result for a resolution that succeeds with a known declaration/definition. */
  public abstract static class SingleResolutionResult<T extends DexClass>
      extends MethodResolutionResult
      implements SuccessfulMemberResolutionResult<DexEncodedMethod, DexMethod> {
    private final DexClass initialResolutionHolder;
    private final T resolvedHolder;
    private final DexEncodedMethod resolvedMethod;

    @SuppressWarnings("ReferenceEquality")
    public SingleResolutionResult(
        DexClass initialResolutionHolder, T resolvedHolder, DexEncodedMethod resolvedMethod) {
      assert initialResolutionHolder != null;
      assert resolvedHolder != null;
      assert resolvedMethod != null;
      assert resolvedHolder.type == resolvedMethod.getHolderType();
      this.resolvedHolder = resolvedHolder;
      this.resolvedMethod = resolvedMethod;
      this.initialResolutionHolder = initialResolutionHolder;
      assert !resolvedMethod.isPrivateMethod()
          || initialResolutionHolder.type == resolvedMethod.getHolderType();
    }

    public abstract SingleResolutionResult<T> withInitialResolutionHolder(
        DexClass newInitialResolutionHolder);

    public MethodOptimizationInfo getOptimizationInfo(
        AppView<?> appView, InvokeMethod invoke, DexClassAndMethod singleTarget) {
      if (singleTarget != null) {
        return singleTarget.getOptimizationInfo();
      }
      if (invoke.isInvokeMethodWithDynamicDispatch() && resolvedMethod.belongsToVirtualPool()) {
        MethodResolutionOptimizationInfoCollection methodResolutionOptimizationInfoCollection =
            appView.getMethodResolutionOptimizationInfoCollection();
        return methodResolutionOptimizationInfoCollection.get(resolvedMethod, resolvedHolder);
      }
      return DefaultMethodOptimizationInfo.getInstance();
    }

    public DispatchTargetLookupResult lookupDispatchTarget(
        AppView<?> appView, InvokeMethod invoke, ProgramMethod context) {
      switch (invoke.getType()) {
        case DIRECT:
          return lookupDirectDispatchTarget(appView, invoke.asInvokeDirect(), context);
        case POLYMORPHIC:
          return new UnknownDispatchTargetLookupResult(this);
        case STATIC:
          return lookupStaticDispatchTarget(appView, invoke.asInvokeStatic(), context);
        case SUPER:
          return lookupSuperDispatchTarget(appView, invoke.asInvokeSuper(), context);
        default:
          break;
      }
      assert invoke.isInvokeInterface() || invoke.isInvokeVirtual();
      InvokeMethodWithReceiver invokeMethodWithReceiver = invoke.asInvokeMethodWithReceiver();
      DynamicType dynamicReceiverType =
          appView.hasLiveness()
              ? invokeMethodWithReceiver.getReceiver().getDynamicType(appView.withLiveness())
              : DynamicType.unknown();
      return lookupVirtualDispatchTarget(
          appView, invokeMethodWithReceiver, dynamicReceiverType, context);
    }

    private DispatchTargetLookupResult lookupDirectDispatchTarget(
        AppView<?> appView, InvokeDirect invoke, ProgramMethod context) {
      DexMethod invokedMethod = invoke.getInvokedMethod();
      DexClassAndMethod result;
      if (appView.hasLiveness()) {
        AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
        AppInfoWithLiveness appInfo = appViewWithLiveness.appInfo();
        result = appInfo.lookupDirectTarget(invokedMethod, context, appViewWithLiveness);
        assert invoke.verifyD8LookupResult(
            result, appView.appInfo().lookupDirectTargetOnItself(invokedMethod, context));
      } else {
        // In D8, we can treat invoke-direct instructions as having a single target if the invoke is
        // targeting a method in the enclosing class.
        result = appView.appInfo().lookupDirectTargetOnItself(invokedMethod, context);
      }
      return DispatchTargetLookupResult.create(result, this);
    }

    private DispatchTargetLookupResult lookupStaticDispatchTarget(
        AppView<?> appView, InvokeStatic invoke, ProgramMethod context) {
      DexMethod invokedMethod = invoke.getInvokedMethod();
      DexClassAndMethod result;
      if (appView.appInfo().hasLiveness()) {
        AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
        AppInfoWithLiveness appInfo = appViewWithLiveness.appInfo();
        result = appInfo.lookupStaticTarget(invokedMethod, context, appViewWithLiveness);
        assert invoke.verifyD8LookupResult(
            result, appInfo.lookupStaticTargetOnItself(invokedMethod, context));
      } else {
        // Allow optimizing static library invokes in D8.
        DexClass clazz = appView.definitionForHolder(invokedMethod);
        if (clazz != null
            && (clazz.isLibraryClass() || appView.libraryMethodOptimizer().isModeled(clazz.type))) {
          result = clazz.lookupClassMethod(invokedMethod);
        } else {
          // In D8, we can treat invoke-static instructions as having a single target if the invoke
          // is targeting a method in the enclosing class.
          result = appView.appInfo().lookupStaticTargetOnItself(invokedMethod, context);
        }
      }
      return DispatchTargetLookupResult.create(result, this);
    }

    private DispatchTargetLookupResult lookupSuperDispatchTarget(
        AppView<?> appView, InvokeSuper invoke, ProgramMethod context) {
      DexClassAndMethod result = null;
      if (appView.appInfo().hasLiveness() && context != null) {
        AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
        AppInfoWithLiveness appInfo = appViewWithLiveness.appInfo();
        DexMethod invokedMethod = invoke.getInvokedMethod();
        if (appInfo.isSubtype(context.getHolderType(), invokedMethod.getHolderType())) {
          result = appInfo.lookupSuperTarget(invokedMethod, context, appViewWithLiveness);
        }
      }
      return DispatchTargetLookupResult.create(result, this);
    }

    public DispatchTargetLookupResult lookupVirtualDispatchTarget(
        AppView<?> appView,
        InvokeMethodWithReceiver invoke,
        DynamicType dynamicReceiverType,
        ProgramMethod context) {
      DexMethod invokedMethod = invoke.getInvokedMethod();
      DexClassAndMethod result = null;
      if (appView.appInfo().hasLiveness()) {
        AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
        result =
            appViewWithLiveness
                .appInfo()
                .lookupSingleVirtualTarget(
                    appViewWithLiveness,
                    invokedMethod,
                    this,
                    context,
                    invoke.getInterfaceBit(),
                    appView,
                    dynamicReceiverType);
      } else {
        // In D8, allow lookupSingleTarget() to be used for finding final library methods. This is
        // used for library modeling.
        DexType holder = invokedMethod.getHolderType();
        if (holder.isClassType()) {
          DexClass clazz = appView.definitionFor(holder);
          if (clazz != null
              && (clazz.isLibraryClass()
                  || appView.libraryMethodOptimizer().isModeled(clazz.getType()))) {
            DexClassAndMethod singleTargetCandidate = clazz.lookupClassMethod(invokedMethod);
            if (singleTargetCandidate != null
                && (clazz.isFinal() || singleTargetCandidate.getAccessFlags().isFinal())) {
              result = singleTargetCandidate;
            }
          }
        }
      }
      return DispatchTargetLookupResult.create(result, this);
    }

    @Override
    public DexClass getInitialResolutionHolder() {
      return initialResolutionHolder;
    }

    @Override
    public T getResolvedHolder() {
      return resolvedHolder;
    }

    @Override
    public DexEncodedMethod getResolvedMember() {
      return resolvedMethod;
    }

    @Override
    public DexEncodedMethod getResolvedMethod() {
      return resolvedMethod;
    }

    @Override
    public ProgramMethod getResolvedProgramMethod() {
      return resolvedHolder.isProgramClass()
          ? new ProgramMethod(resolvedHolder.asProgramClass(), resolvedMethod)
          : null;
    }

    @Override
    public DexClassAndMethod getResolutionPair() {
      return DexClassAndMethod.create(resolvedHolder, resolvedMethod);
    }

    @Override
    public boolean isSingleResolution() {
      return true;
    }

    @Override
    public SingleResolutionResult<?> asSingleResolution() {
      return this;
    }

    @Override
    public boolean isSuccessfulMemberResolutionResult() {
      return true;
    }

    @Override
    public SuccessfulMemberResolutionResult<DexEncodedMethod, DexMethod>
        asSuccessfulMemberResolutionResult() {
      return this;
    }

    @Override
    public OptionalBool isAccessibleFrom(
        ProgramDefinition context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      return AccessControl.isMemberAccessible(this, context, appView, appInfo);
    }

    @Override
    public OptionalBool isAccessibleForVirtualDispatchFrom(
        ProgramDefinition context, AppView<? extends AppInfoWithClassHierarchy> appView) {
      if (resolvedMethod.isVirtualMethod()) {
        return isAccessibleFrom(context, appView, appView.appInfo());
      }
      return OptionalBool.FALSE;
    }

    @Override
    public boolean isVirtualTarget() {
      return resolvedMethod.isVirtualMethod();
    }

    /**
     * This is intended to model the actual behavior of invoke-special on a JVM.
     *
     * <p>See https://docs.oracle.com/javase/specs/jvms/se11/html/jvms-6.html#jvms-6.5.invokespecial
     * and comments below for deviations due to diverging behavior on actual JVMs.
     */
    @Override
    public DexClassAndMethod lookupInvokeSpecialTarget(
        DexProgramClass context, AppView<? extends AppInfoWithClassHierarchy> appView) {
      // If the resolution is non-accessible then no target exists.
      AppInfoWithClassHierarchy appInfo = appView.appInfo();
      if (isAccessibleFrom(context, appView).isPossiblyTrue()) {
        return internalInvokeSpecialOrSuper(
            context, appInfo, (sup, sub) -> isSuperclass(sup, sub, appInfo));
      }
      return null;
    }

    private static DexClass definitionForHelper(AppInfoWithClassHierarchy appInfo, DexType type) {
      if (type == null) {
        return null;
      }
      ClassResolutionResult resolutionResult =
          appInfo.contextIndependentDefinitionForWithResolutionResult(type);
      return resolutionResult.toSingleClassWithProgramOverLibrary();
    }

    /**
     * Lookup the target of an invoke-super.
     *
     * <p>This will return the target iff the resolution succeeded and the target is valid (i.e.,
     * non-static and non-initializer) and accessible from {@code context}.
     *
     * <p>Additionally, this will also verify that the invoke-super is valid, i.e., it is on the a
     * super type of the current context. Any invoke-special targeting the same type should have
     * been mapped to an invoke-direct, but could change due to merging so we need to still allow
     * the context to be equal to the targeted (symbolically referenced) type.
     *
     * @param context Class the invoke is contained in, i.e., the holder of the caller.
     * @param appInfo Application info.
     * @return The actual target for the invoke-super or {@code null} if no valid target is found.
     */
    @Override
    public DexClassAndMethod lookupInvokeSuperTarget(
        DexProgramClass context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      if (resolvedMethod.isInstanceInitializer()
          || (initialResolutionHolder != context
              && !isSuperclass(initialResolutionHolder, context, appInfo))) {
        // If the target is <init> or not on a super class then the call is invalid.
        return null;
      }
      if (isAccessibleFrom(context, appView, appInfo).isPossiblyTrue()) {
        return internalInvokeSpecialOrSuper(context, appInfo, (sup, sub) -> true);
      }
      return null;
    }

    /**
     * Lookup the target of an invoke-static.
     *
     * <p>This method will resolve the method on the holder and only return a non-null value if the
     * result of resolution was a static, non-abstract method.
     *
     * @param context Class the invoke is contained in, i.e., the holder of the caller.
     * @param appInfo Application info.
     * @return The actual target or {@code null} if none found.
     */
    @Override
    public DexClassAndMethod lookupInvokeStaticTarget(
        DexProgramClass context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      if (isAccessibleFrom(context, appView, appInfo).isFalse()) {
        return null;
      }
      if (resolvedMethod.isStatic()) {
        return getResolutionPair();
      }
      return null;
    }

    /**
     * Lookup direct method following the super chain from the holder of {@code method}.
     *
     * <p>This method will lookup private and constructor methods.
     *
     * @param context Class the invoke is contained in, i.e., the holder of the caller. * @param
     *     appInfo Application info.
     * @return The actual target or {@code null} if none found.
     */
    @Override
    public DexClassAndMethod lookupInvokeDirectTarget(
        DexProgramClass context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      if (isAccessibleFrom(context, appView, appInfo).isFalse()) {
        return null;
      }
      if (resolvedMethod.isDirectMethod()) {
        return getResolutionPair();
      }
      return null;
    }

    @SuppressWarnings("ReferenceEquality")
    private DexClassAndMethod internalInvokeSpecialOrSuper(
        DexProgramClass context,
        AppInfoWithClassHierarchy appInfo,
        BiPredicate<DexClass, DexClass> isSuperclass) {

      // Statics cannot be targeted by invoke-special/super.
      if (getResolvedMethod().isStatic()) {
        return null;
      }

      if (getResolvedHolder().isInterface() && getResolvedMethod().isPrivate()) {
        return getResolutionPair();
      }

      // The symbolic reference is the holder type that resolution was initiated at.
      DexClass symbolicReference = initialResolutionHolder;

      // First part of the spec is to determine the starting point for lookup for invoke special.
      // Notice that the specification indicates that the immediate super type should
      // be used when three items hold, the second being:
      //   is-class(sym-ref) => is-super(sym-ref, context)
      // in the case of an interface that is trivially satisfied, which would lead the initial type
      // to be java.lang.Object. However in practice the lookup appears to start at the symbolic
      // reference in the case of interfaces, so the second condition should likely be interpreted:
      //   is-class(sym-ref) *and* is-super(sym-ref, context).
      final DexClass initialType;
      if (!resolvedMethod.isInstanceInitializer()
          && !symbolicReference.isInterface()
          && isSuperclass.test(symbolicReference, context)) {
        // If reference is a super type of the context then search starts at the immediate super.
        initialType = definitionForHelper(appInfo, context.superType);
      } else {
        // Otherwise it starts at the reference itself.
        initialType = symbolicReference;
      }
      // Abort if for some reason the starting point could not be found.
      if (initialType == null) {
        return null;
      }
      // 1-3. Search the initial class and its supers in order for a matching instance method.
      DexMethod method = getResolvedMethod().getReference();
      DexClassAndMethod target = null;
      DexClass current = initialType;
      while (current != null) {
        target = current.lookupClassMethod(method);
        if (target != null) {
          break;
        }
        current = definitionForHelper(appInfo, current.superType);
      }
      // 4. Otherwise, it is the single maximally specific method:
      if (target == null) {
        target = appInfo.lookupMaximallySpecificMethod(initialType, method);
      }
      if (target == null) {
        return null;
      }
      // Linking exceptions:
      // A non-instance method throws IncompatibleClassChangeError.
      if (target.getAccessFlags().isStatic()) {
        return null;
      }
      // An instance initializer that is not to the symbolic reference throws NoSuchMethodError.
      // It appears as if this check is also in place for non-initializer methods too.
      // See NestInvokeSpecialMethodAccessWithIntermediateTest.
      if ((target.getDefinition().isInstanceInitializer() || target.getAccessFlags().isPrivate())
          && target.getHolderType() != symbolicReference.type) {
        return null;
      }
      // Runtime exceptions:
      // An abstract method throws AbstractMethodError.
      if (target.getAccessFlags().isAbstract()) {
        return null;
      }
      return target;
    }

    private static boolean isSuperclass(
        DexClass sup, DexClass sub, AppInfoWithClassHierarchy appInfo) {
      return appInfo.isStrictSubtypeOf(sub.type, sup.type);
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public LookupResult lookupVirtualDispatchTargets(
        DexProgramClass context,
        AppView<? extends AppInfoWithClassHierarchy> appView,
        InstantiatedSubTypeInfo instantiatedInfo,
        PinnedPredicate pinnedPredicate) {
      // Check that the initial resolution holder is accessible from the context.
      AppInfoWithClassHierarchy appInfo = appView.appInfo();
      assert appInfo.isSubtype(initialResolutionHolder.type, resolvedHolder.type)
          : initialResolutionHolder.type + " is not a subtype of " + resolvedHolder.type;
      if (context != null && isAccessibleFrom(context, appView).isFalse()) {
        return LookupResult.createFailedResult();
      }
      if (resolvedMethod.isPrivateMethod()) {
        // If the resolved reference is private there is no dispatch.
        // This is assuming that the method is accessible, which implies self/nest access.
        // Only include if the target has code or is native.
        boolean isIncomplete =
            pinnedPredicate.isPinned(resolvedHolder) && pinnedPredicate.isPinned(resolvedMethod);
        DexClassAndMethod resolutionPair = getResolutionPair();
        return LookupResult.createResult(
            Collections.singletonMap(resolutionPair.getReference(), resolutionPair),
            Collections.emptyList(),
            Collections.emptyList(),
            isIncomplete
                ? LookupResultCollectionState.Incomplete
                : LookupResultCollectionState.Complete);
      }
      assert resolvedMethod.isNonPrivateVirtualMethod();
      LookupResultSuccess.Builder resultBuilder = LookupResultSuccess.builder();
      LookupCompletenessHelper incompleteness = new LookupCompletenessHelper(pinnedPredicate);
      instantiatedInfo.forEachInstantiatedSubType(
          initialResolutionHolder.type,
          subClass -> {
            incompleteness.checkClass(subClass);
            LookupMethodTarget lookupTarget =
                lookupVirtualDispatchTarget(
                    subClass,
                    appInfo,
                    resolvedHolder.type,
                    resultBuilder::addTypeCausingFailure,
                    resultBuilder::addMethodCausingFailure);
            if (lookupTarget != null) {
              incompleteness.checkDexClassAndMethod(lookupTarget);
              addVirtualDispatchTarget(lookupTarget, resolvedHolder.isInterface(), resultBuilder);
            }
          },
          lambda -> {
            assert resolvedHolder.isInterface()
                || resolvedHolder.type == appView.dexItemFactory().objectType;
            LookupTarget target =
                lookupVirtualDispatchTarget(
                    lambda,
                    appInfo,
                    resultBuilder::addTypeCausingFailure,
                    resultBuilder::addMethodCausingFailure);
            if (target != null) {
              if (target.isLambdaTarget()) {
                resultBuilder.addLambdaTarget(target.asLambdaTarget());
              } else {
                addVirtualDispatchTarget(
                    target.asMethodTarget(), resolvedHolder.isInterface(), resultBuilder);
              }
            }
          });
      return resultBuilder
          .setState(incompleteness.computeCollectionState(resolvedMethod.getReference(), appInfo))
          .build();
    }

    @Override
    public LookupResult lookupVirtualDispatchTargets(
        DexProgramClass context,
        AppView<AppInfoWithLiveness> appView,
        DexProgramClass refinedReceiverUpperBound,
        DexProgramClass refinedReceiverLowerBound) {
      AppInfoWithLiveness appInfo = appView.appInfo();
      assert refinedReceiverUpperBound != null;
      assert appInfo.isSubtype(refinedReceiverUpperBound.type, initialResolutionHolder.type);
      assert refinedReceiverLowerBound == null
          || appInfo.isSubtype(refinedReceiverLowerBound.type, refinedReceiverUpperBound.type);
      // TODO(b/148769279): Remove the check for hasInstantiatedLambdas.
      Box<Boolean> hasInstantiatedLambdas = new Box<>(false);
      InstantiatedSubTypeInfo instantiatedSubTypeInfo =
          instantiatedSubTypeInfoForInstantiatedType(
              appInfo,
              refinedReceiverUpperBound,
              refinedReceiverLowerBound,
              hasInstantiatedLambdas);
      LookupResult lookupResult =
          lookupVirtualDispatchTargets(
              context,
              appView,
              instantiatedSubTypeInfo,
              appInfo::isPinnedNotProgramOrLibraryOverride);
      if (hasInstantiatedLambdas.get() && lookupResult.isLookupResultSuccess()) {
        lookupResult.asLookupResultSuccess().setIncomplete();
      }
      return lookupResult;
    }

    private InstantiatedSubTypeInfo instantiatedSubTypeInfoForInstantiatedType(
        AppInfoWithLiveness appInfo,
        DexProgramClass refinedReceiverUpperBound,
        DexProgramClass refinedReceiverLowerBound,
        Box<Boolean> hasInstantiatedLambdas) {
      return (ignored, subTypeConsumer, callSiteConsumer) -> {
        Consumer<DexProgramClass> lambdaInstantiatedConsumer =
            subType -> {
              subTypeConsumer.accept(subType);
              if (appInfo.isInstantiatedInterface(subType)) {
                hasInstantiatedLambdas.set(true);
              }
            };
        if (refinedReceiverLowerBound == null) {
          appInfo.forEachInstantiatedSubType(
              refinedReceiverUpperBound.type, lambdaInstantiatedConsumer, callSiteConsumer);
        } else {
          appInfo.forEachInstantiatedSubTypeInChain(
              refinedReceiverUpperBound,
              refinedReceiverLowerBound,
              lambdaInstantiatedConsumer,
              callSiteConsumer);
        }
      };
    }

    private static void addVirtualDispatchTarget(
        LookupMethodTarget target,
        boolean holderIsInterface,
        LookupResultSuccess.Builder resultBuilder) {
      assert target.isMethodTarget();
      DexEncodedMethod targetMethod = target.asMethodTarget().getDefinition();
      assert !targetMethod.isPrivateMethod();
      if (holderIsInterface) {
        // Add default interface methods to the list of targets.
        //
        // This helps to make sure we take into account synthesized lambda classes
        // that we are not aware of. Like in the following example, we know that all
        // classes, XX in this case, override B::bar(), but there are also synthesized
        // classes for lambda which don't, so we still need default method to be live.
        //
        //   public static void main(String[] args) {
        //     X x = () -> {};
        //     x.bar();
        //   }
        //
        //   interface X {
        //     void foo();
        //     default void bar() { }
        //   }
        //
        //   class XX implements X {
        //     public void foo() { }
        //     public void bar() { }
        //   }
        //
        if (targetMethod.isDefaultMethod()) {
          resultBuilder.addMethodTarget(target);
        }
        // Default methods are looked up when looking at a specific subtype that does not override
        // them. Otherwise, we would look up default methods that are actually never used.
        // However, we have to add bridge methods, otherwise we can remove a bridge that will be
        // used.
        if (!targetMethod.accessFlags.isAbstract() && targetMethod.accessFlags.isBridge()) {
          resultBuilder.addMethodTarget(target);
        }
      } else {
        resultBuilder.addMethodTarget(target);
      }
    }

    /**
     * This implements the logic for the actual method selection for a virtual target, according to
     * https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-6.html#jvms-6.5.invokevirtual where
     * we have an object ref on the stack.
     */
    @Override
    public LookupTarget lookupVirtualDispatchTarget(
        InstantiatedObject instance, AppInfoWithClassHierarchy appInfo) {
      return instance.isClass()
          ? lookupVirtualDispatchTarget(instance.asClass(), appInfo)
          : lookupVirtualDispatchTarget(
              instance.asLambda(), appInfo, emptyConsumer(), emptyConsumer());
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public LookupMethodTarget lookupVirtualDispatchTarget(
        DexClass dynamicInstance, AppInfoWithClassHierarchy appInfo) {
      return lookupVirtualDispatchTarget(
          dynamicInstance, appInfo, initialResolutionHolder.type, emptyConsumer(), emptyConsumer());
    }

    @Override
    public LookupTarget lookupVirtualDispatchTarget(
        LambdaDescriptor lambdaInstance,
        AppInfoWithClassHierarchy appInfo,
        Consumer<DexType> typeCausingFailureConsumer,
        Consumer<? super DexEncodedMethod> methodCausingFailureConsumer) {
      if (lambdaInstance.getMainMethod().match(resolvedMethod)) {
        DexMethod methodReference = lambdaInstance.implHandle.asMethod();
        DexClass holder = definitionForHelper(appInfo, methodReference.getHolderType());
        DexClassAndMethod method = methodReference.lookupMemberOnClass(holder);
        if (method == null) {
          // The targeted method might not exist, eg, Throwable.addSuppressed in an old library.
          return null;
        }
        return new LookupLambdaTarget(lambdaInstance, method);
      }
      return lookupMaximallySpecificDispatchTarget(
          lambdaInstance, appInfo, typeCausingFailureConsumer, methodCausingFailureConsumer);
    }

    @SuppressWarnings("ReferenceEquality")
    private LookupMethodTarget lookupVirtualDispatchTarget(
        DexClass dynamicInstance,
        AppInfoWithClassHierarchy appInfo,
        DexType resolutionHolder,
        Consumer<DexType> typeCausingFailureConsumer,
        Consumer<? super DexEncodedMethod> methodCausingFailureConsumer) {
      assert appInfo.isSubtype(dynamicInstance.type, resolutionHolder)
          : dynamicInstance.type + " is not a subtype of " + resolutionHolder;
      // TODO(b/148591377): Enable this assertion.
      // The dynamic type cannot be an interface.
      // assert !dynamicInstance.isInterface();
      DexClassAndMethod initialResolutionPair = getResolutionPair();
      if (resolvedMethod.isPrivateMethod()) {
        // If the resolved reference is private there is no dispatch.
        // This is assuming that the method is accessible, which implies self/nest access.
        return initialResolutionPair;
      }
      boolean allowPackageBlocked = resolvedMethod.accessFlags.isPackagePrivate();
      DexClass current = dynamicInstance;
      DexClassAndMethod overrideTarget = initialResolutionPair;
      while (current != null) {
        DexEncodedMethod candidate =
            lookupOverrideCandidate(overrideTarget.getDefinition(), current);
        if (candidate == DexEncodedMethod.SENTINEL && allowPackageBlocked) {
          overrideTarget = findWideningOverride(initialResolutionPair, current, appInfo);
          allowPackageBlocked = false;
          continue;
        }
        if (candidate == null || candidate == DexEncodedMethod.SENTINEL) {
          // We cannot find a target above the resolved method.
          if (current.type == overrideTarget.getHolderType()) {
            return null;
          }
          current = definitionForHelper(appInfo, current.superType);
          continue;
        }
        DexClassAndMethod target = DexClassAndMethod.create(current, candidate);
        return overrideTarget != initialResolutionPair
            ? new LookupMethodTargetWithAccessOverride(target, overrideTarget)
            : target;
      }
      // If we have not found a candidate and the holder is not an interface it must be because the
      // class is missing.
      if (!resolvedHolder.isInterface()) {
        return null;
      }
      return lookupMaximallySpecificDispatchTarget(
          dynamicInstance, appInfo, typeCausingFailureConsumer, methodCausingFailureConsumer);
    }

    private DexClassAndMethod lookupMaximallySpecificDispatchTarget(
        DexClass dynamicInstance,
        AppInfoWithClassHierarchy appInfo,
        Consumer<DexType> typeCausingFailureConsumer,
        Consumer<? super DexEncodedMethod> methodCausingFailureConsumer) {
      MethodResolutionResult maximallySpecificResolutionResult =
          appInfo.resolveMaximallySpecificTarget(dynamicInstance, resolvedMethod.getReference());
      if (maximallySpecificResolutionResult.isSingleResolution()) {
        return maximallySpecificResolutionResult.getResolutionPair();
      }
      if (maximallySpecificResolutionResult.isFailedResolution()) {
        maximallySpecificResolutionResult
            .asFailedResolution()
            .forEachFailureDependency(typeCausingFailureConsumer, methodCausingFailureConsumer);
        return null;
      }
      assert maximallySpecificResolutionResult.isArrayCloneMethodResult();
      return null;
    }

    private DexClassAndMethod lookupMaximallySpecificDispatchTarget(
        LambdaDescriptor lambdaDescriptor,
        AppInfoWithClassHierarchy appInfo,
        Consumer<DexType> typeCausingFailureConsumer,
        Consumer<? super DexEncodedMethod> methodCausingFailureConsumer) {
      MethodResolutionResult maximallySpecificResolutionResult =
          appInfo.resolveMaximallySpecificTarget(lambdaDescriptor, resolvedMethod.getReference());
      if (maximallySpecificResolutionResult.isSingleResolution()) {
        return maximallySpecificResolutionResult.getResolutionPair();
      }
      if (maximallySpecificResolutionResult.isFailedResolution()) {
        maximallySpecificResolutionResult
            .asFailedResolution()
            .forEachFailureDependency(typeCausingFailureConsumer, methodCausingFailureConsumer);
        return null;
      }
      assert maximallySpecificResolutionResult.isArrayCloneMethodResult();
      return null;
    }

    /**
     * C contains a declaration for an instance method m that overrides (§5.4.5) the resolved
     * method, then m is the method to be invoked. If the candidate is not a valid override, we
     * return sentinel to indicate that we have to search for a method that is widening access
     * inside the package.
     */
    private static DexEncodedMethod lookupOverrideCandidate(
        DexEncodedMethod method, DexClass clazz) {
      DexEncodedMethod candidate = clazz.lookupVirtualMethod(method.getReference());
      assert candidate == null || !candidate.isPrivateMethod();
      if (candidate != null) {
        return isOverriding(method, candidate) ? candidate : DexEncodedMethod.SENTINEL;
      }
      return null;
    }

    @SuppressWarnings("ReferenceEquality")
    private static DexClassAndMethod findWideningOverride(
        DexClassAndMethod resolvedMethod, DexClass clazz, AppInfoWithClassHierarchy appInfo) {
      // Otherwise, lookup to first override that is distinct from resolvedMethod.
      assert resolvedMethod.getDefinition().getAccessFlags().isPackagePrivate();
      while (clazz.hasSuperType()) {
        clazz = definitionForHelper(appInfo, clazz.getSuperType());
        if (clazz == null) {
          return resolvedMethod;
        }
        DexClassAndMethod otherOverride =
            clazz.lookupVirtualClassMethod(resolvedMethod.getReference());
        if (otherOverride != null
            && isOverriding(resolvedMethod, otherOverride)
            && (otherOverride.getAccessFlags().isPublic()
                || otherOverride.getAccessFlags().isProtected())) {
          assert resolvedMethod.getDefinition() != otherOverride.getDefinition();
          return otherOverride;
        }
      }
      return resolvedMethod;
    }

    public static boolean isOverriding(
        DexClassAndMethod resolvedMethod, DexClassAndMethod candidate) {
      return isOverriding(resolvedMethod.getDefinition(), candidate.getDefinition());
    }

    /**
     * Implementation of method overriding according to the jvm specification
     * https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.5
     *
     * <p>The implementation assumes that the holder of the candidate is a subtype of the holder of
     * the resolved method. It also assumes that resolvedMethod is the actual method to find a
     * lookup for (that is, it is either mA or m').
     */
    public static boolean isOverriding(
        DexEncodedMethod resolvedMethod, DexEncodedMethod candidate) {
      assert resolvedMethod.getReference().match(candidate.getReference());
      assert !candidate.isPrivateMethod();
      if (resolvedMethod.accessFlags.isPublic() || resolvedMethod.accessFlags.isProtected()) {
        return true;
      }
      // For package private methods, a valid override has to be inside the package.
      assert resolvedMethod.accessFlags.isPackagePrivate();
      return resolvedMethod.getHolderType().isSamePackage(candidate.getHolderType());
    }
  }

  public static class SingleProgramResolutionResult
      extends SingleResolutionResult<DexProgramClass> {

    public SingleProgramResolutionResult(
        DexClass initialResolutionHolder,
        DexProgramClass resolvedHolder,
        DexEncodedMethod resolvedMethod) {
      super(initialResolutionHolder, resolvedHolder, resolvedMethod);
    }

    @Override
    public SingleResolutionResult<DexProgramClass> withInitialResolutionHolder(
        DexClass newInitialResolutionHolder) {
      return newInitialResolutionHolder != getInitialResolutionHolder()
          ? new SingleProgramResolutionResult(
              newInitialResolutionHolder, getResolvedHolder(), getResolvedMethod())
          : this;
    }

    @Override
    public void visitMethodResolutionResults(
        Consumer<? super SingleResolutionResult<? extends ProgramOrClasspathClass>>
            programOrClasspathConsumer,
        Consumer<? super SingleLibraryResolutionResult> libraryResultConsumer,
        Consumer<? super ArrayCloneMethodResult> cloneResultConsumer,
        Consumer<? super FailedResolutionResult> failedResolutionConsumer) {
      programOrClasspathConsumer.accept(this);
    }

    @Override
    public boolean hasProgramResult() {
      return true;
    }

    @Override
    protected SingleProgramResolutionResult asSingleProgramResolutionResult() {
      return this;
    }
  }

  public static class SingleClasspathResolutionResult
      extends SingleResolutionResult<DexClasspathClass> {

    public SingleClasspathResolutionResult(
        DexClass initialResolutionHolder,
        DexClasspathClass resolvedHolder,
        DexEncodedMethod resolvedMethod) {
      super(initialResolutionHolder, resolvedHolder, resolvedMethod);
    }

    @Override
    public SingleClasspathResolutionResult withInitialResolutionHolder(
        DexClass newInitialResolutionHolder) {
      return newInitialResolutionHolder != getInitialResolutionHolder()
          ? new SingleClasspathResolutionResult(
              newInitialResolutionHolder, getResolvedHolder(), getResolvedMethod())
          : this;
    }

    @Override
    public void visitMethodResolutionResults(
        Consumer<? super SingleResolutionResult<? extends ProgramOrClasspathClass>>
            programOrClasspathConsumer,
        Consumer<? super SingleLibraryResolutionResult> libraryResultConsumer,
        Consumer<? super ArrayCloneMethodResult> cloneResultConsumer,
        Consumer<? super FailedResolutionResult> failedResolutionConsumer) {
      programOrClasspathConsumer.accept(this);
    }

    @Override
    public SingleClasspathResolutionResult asSingleClasspathResolutionResult() {
      return this;
    }
  }

  public static class SingleLibraryResolutionResult
      extends SingleResolutionResult<DexLibraryClass> {

    public SingleLibraryResolutionResult(
        DexClass initialResolutionHolder,
        DexLibraryClass resolvedHolder,
        DexEncodedMethod resolvedMethod) {
      super(initialResolutionHolder, resolvedHolder, resolvedMethod);
    }

    @Override
    public SingleLibraryResolutionResult withInitialResolutionHolder(
        DexClass newInitialResolutionHolder) {
      return newInitialResolutionHolder != getInitialResolutionHolder()
          ? new SingleLibraryResolutionResult(
              newInitialResolutionHolder, getResolvedHolder(), getResolvedMethod())
          : this;
    }

    @Override
    public void visitMethodResolutionResults(
        Consumer<? super SingleResolutionResult<? extends ProgramOrClasspathClass>>
            programOrClasspathConsumer,
        Consumer<? super SingleLibraryResolutionResult> libraryResultConsumer,
        Consumer<? super ArrayCloneMethodResult> cloneResultConsumer,
        Consumer<? super FailedResolutionResult> failedResolutionConsumer) {
      libraryResultConsumer.accept(this);
    }
  }

  abstract static class EmptyResult extends MethodResolutionResult {

    @Override
    public final DexClassAndMethod lookupInvokeSpecialTarget(
        DexProgramClass context, AppView<? extends AppInfoWithClassHierarchy> appView) {
      return null;
    }

    @Override
    public DexClassAndMethod lookupInvokeSuperTarget(
        DexProgramClass context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      return null;
    }

    @Override
    public DexClassAndMethod lookupInvokeStaticTarget(
        DexProgramClass context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      return null;
    }

    @Override
    public DexClassAndMethod lookupInvokeDirectTarget(
        DexProgramClass context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      return null;
    }

    @Override
    public LookupResult lookupVirtualDispatchTargets(
        DexProgramClass context,
        AppView<? extends AppInfoWithClassHierarchy> appView,
        InstantiatedSubTypeInfo instantiatedInfo,
        PinnedPredicate pinnedPredicate) {
      return LookupResult.getIncompleteEmptyResult();
    }

    @Override
    public LookupResult lookupVirtualDispatchTargets(
        DexProgramClass context,
        AppView<AppInfoWithLiveness> appView,
        DexProgramClass refinedReceiverUpperBound,
        DexProgramClass refinedReceiverLowerBound) {
      return LookupResult.getIncompleteEmptyResult();
    }

    @Override
    public LookupTarget lookupVirtualDispatchTarget(
        InstantiatedObject instance, AppInfoWithClassHierarchy appInfo) {
      return null;
    }

    @Override
    public LookupMethodTarget lookupVirtualDispatchTarget(
        DexClass dynamicInstance, AppInfoWithClassHierarchy appInfo) {
      return null;
    }

    @Override
    public LookupTarget lookupVirtualDispatchTarget(
        LambdaDescriptor lambdaInstance,
        AppInfoWithClassHierarchy appInfo,
        Consumer<DexType> typeCausingFailureConsumer,
        Consumer<? super DexEncodedMethod> methodCausingFailureConsumer) {
      return null;
    }
  }

  /** Singleton result for the special case resolving the array clone() method. */
  public static class ArrayCloneMethodResult extends EmptyResult {

    static final ArrayCloneMethodResult INSTANCE = new ArrayCloneMethodResult();

    private ArrayCloneMethodResult() {
      // Intentionally left empty.
    }

    @Override
    public OptionalBool isAccessibleFrom(
        ProgramDefinition context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      return OptionalBool.TRUE;
    }

    @Override
    public OptionalBool isAccessibleForVirtualDispatchFrom(
        ProgramDefinition context, AppView<? extends AppInfoWithClassHierarchy> appView) {
      return OptionalBool.TRUE;
    }

    @Override
    public boolean isVirtualTarget() {
      return true;
    }

    @Override
    public void visitMethodResolutionResults(
        Consumer<? super SingleResolutionResult<? extends ProgramOrClasspathClass>>
            programOrClasspathConsumer,
        Consumer<? super SingleLibraryResolutionResult> libraryResultConsumer,
        Consumer<? super ArrayCloneMethodResult> cloneResultConsumer,
        Consumer<? super FailedResolutionResult> failedResolutionConsumer) {
      cloneResultConsumer.accept(this);
    }

    @Override
    public boolean isArrayCloneMethodResult() {
      return true;
    }
  }

  /** Base class for all types of failed resolutions. */
  public abstract static class FailedResolutionResult extends EmptyResult {

    protected final Collection<DexType> typesCausingError;

    private FailedResolutionResult(Collection<DexType> typesCausingError) {
      this.typesCausingError = typesCausingError;
    }

    @Override
    public boolean isFailedResolution() {
      return true;
    }

    @Override
    public FailedResolutionResult asFailedResolution() {
      return this;
    }

    public void forEachFailureDependency(
        Consumer<DexType> typesCausingFailureConsumer,
        Consumer<? super DexEncodedMethod> methodCausingFailureConsumer) {
      if (typesCausingError != null) {
        typesCausingError.forEach(typesCausingFailureConsumer);
      }
    }

    @Override
    public OptionalBool isAccessibleFrom(
        ProgramDefinition context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      return OptionalBool.FALSE;
    }

    @Override
    public OptionalBool isAccessibleForVirtualDispatchFrom(
        ProgramDefinition context, AppView<? extends AppInfoWithClassHierarchy> appView) {
      return OptionalBool.FALSE;
    }

    @Override
    public boolean isVirtualTarget() {
      return false;
    }

    public boolean hasMethodsCausingError() {
      return false;
    }

    @Override
    public void visitMethodResolutionResults(
        Consumer<? super SingleResolutionResult<? extends ProgramOrClasspathClass>>
            programOrClasspathConsumer,
        Consumer<? super SingleLibraryResolutionResult> libraryResultConsumer,
        Consumer<? super ArrayCloneMethodResult> cloneResultConsumer,
        Consumer<? super FailedResolutionResult> failedResolutionConsumer) {
      failedResolutionConsumer.accept(this);
    }

    public boolean hasTypesOrMethodsCausingError() {
      return (typesCausingError != null && !typesCausingError.isEmpty())
          || hasMethodsCausingError();
    }
  }

  public static class ClassNotFoundResult extends FailedResolutionResult {
    static final ClassNotFoundResult INSTANCE = new ClassNotFoundResult();

    private ClassNotFoundResult() {
      super(null);
    }

    @Override
    public boolean isClassNotFoundResult() {
      return true;
    }
  }

  public abstract static class FailedResolutionWithCausingMethods extends FailedResolutionResult {

    private final Collection<DexEncodedMethod> methodsCausingError;

    private FailedResolutionWithCausingMethods(Collection<DexEncodedMethod> methodsCausingError) {
      super(ListUtils.map(methodsCausingError, DexEncodedMember::getHolderType));
      this.methodsCausingError = methodsCausingError;
    }

    @Override
    public void forEachFailureDependency(
        Consumer<DexType> typesCausingFailureConsumer,
        Consumer<? super DexEncodedMethod> methodCausingFailureConsumer) {
      super.forEachFailureDependency(typesCausingFailureConsumer, methodCausingFailureConsumer);
      this.methodsCausingError.forEach(methodCausingFailureConsumer);
    }

    @Override
    public boolean hasMethodsCausingError() {
      return methodsCausingError.size() > 0;
    }
  }

  public static class IncompatibleClassResult extends FailedResolutionWithCausingMethods {
    static final IncompatibleClassResult INSTANCE =
        new IncompatibleClassResult(Collections.emptyList());

    private IncompatibleClassResult(Collection<DexEncodedMethod> methodsCausingError) {
      super(methodsCausingError);
    }

    static IncompatibleClassResult create(Collection<DexEncodedMethod> methodsCausingError) {
      return methodsCausingError.isEmpty()
          ? INSTANCE
          : new IncompatibleClassResult(methodsCausingError);
    }

    @Override
    public boolean isIncompatibleClassChangeErrorResult() {
      return true;
    }
  }

  public static class NoSuchMethodResult extends FailedResolutionResult {

    static final NoSuchMethodResult INSTANCE = new NoSuchMethodResult();

    private NoSuchMethodResult() {
      super(null);
    }

    protected NoSuchMethodResult(Collection<DexType> typesCausingError) {
      super(typesCausingError);
    }

    public static NoSuchMethodResult getEmptyNoSuchMethodResult() {
      return INSTANCE;
    }

    @Override
    public boolean isNoSuchMethodErrorResult(
        DexClass context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      return true;
    }

    @Override
    public boolean internalIsInstanceOfNoSuchMethodResult() {
      return true;
    }

    @Override
    public NoSuchMethodResult asNoSuchMethodResult() {
      return this;
    }
  }

  public static class NoSuchMethodResultDueToMultipleClassDefinitions extends NoSuchMethodResult {

    public NoSuchMethodResultDueToMultipleClassDefinitions(Collection<DexType> typesCausingError) {
      super(typesCausingError);
    }

    @Override
    public boolean isNoSuchMethodResultDueToMultipleClassDefinitions() {
      return true;
    }
  }

  static class IllegalAccessOrNoSuchMethodResult extends FailedResolutionWithCausingMethods {

    private final DexClass initialResolutionHolder;

    public IllegalAccessOrNoSuchMethodResult(
        DexClass initialResolutionHolder, Collection<DexEncodedMethod> methodsCausingError) {
      super(methodsCausingError);
      this.initialResolutionHolder = initialResolutionHolder;
    }

    public IllegalAccessOrNoSuchMethodResult(
        DexClass initialResolutionHolder, DexEncodedMethod methodCausingError) {
      this(initialResolutionHolder, Collections.singletonList(methodCausingError));
      assert methodCausingError != null;
    }

    @Override
    public boolean isIllegalAccessErrorResult(
        DexClass context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      if (!hasMethodsCausingError()) {
        return false;
      }
      BooleanBox seenNoAccess = new BooleanBox(false);
      forEachFailureDependency(
          type ->
              appView
                  .contextIndependentDefinitionForWithResolutionResult(type)
                  .forEachClassResolutionResult(
                      clazz ->
                          seenNoAccess.or(
                              AccessControl.isClassAccessible(
                                      clazz,
                                      context,
                                      appInfo.getClassToFeatureSplitMap(),
                                      appView.options(),
                                      appView.getStartupProfile(),
                                      appView.getSyntheticItems())
                                  .isPossiblyFalse())),
          method -> {
            DexClass holder = appView.definitionFor(method.getHolderType());
            DexClassAndMethod classAndMethod = DexClassAndMethod.create(holder, method);
            seenNoAccess.or(
                AccessControl.isMemberAccessible(
                        classAndMethod, initialResolutionHolder, context, appView, appInfo)
                    .isPossiblyFalse());
          });
      return seenNoAccess.get();
    }

    @Override
    public boolean isNoSuchMethodErrorResult(
        DexClass context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      if (!hasMethodsCausingError()) {
        return true;
      }
      if (isIllegalAccessErrorResult(context, appView, appInfo)) {
        return false;
      }
      // At this point we know we have methods causing errors but we have access to them. To be
      // certain that this is the case where we have nest access but we are invoking a method with
      // an incorrect symbolic reference, we directly test for it by having an assert.
      assert verifyInvalidSymbolicReference();
      return true;
    }

    @SuppressWarnings("ReferenceEquality")
    private boolean verifyInvalidSymbolicReference() {
      BooleanBox invalidSymbolicReference = new BooleanBox(true);
      forEachFailureDependency(
          type -> {
            // Intentionally empty
          },
          method -> {
            invalidSymbolicReference.and(
                method.getHolderType() != initialResolutionHolder.getType());
          });
      return invalidSymbolicReference.get();
    }
  }

  public abstract static class MultipleMethodResolutionResult<
          C extends DexClass & ProgramOrClasspathClass, T extends SingleResolutionResult<C>>
      extends MethodResolutionResult {

    protected final T programOrClasspathResult;
    protected final List<SingleResolutionResult<? extends ProgramOrClasspathClass>>
        otherProgramOrClasspathResults;
    protected final List<SingleLibraryResolutionResult> libraryResolutionResults;
    protected final List<FailedResolutionResult> failedResolutionResults;

    protected MultipleMethodResolutionResult(
        T programOrClasspathResult,
        List<SingleResolutionResult<? extends ProgramOrClasspathClass>> programOrClasspathResults,
        List<SingleLibraryResolutionResult> libraryResolutionResults,
        List<FailedResolutionResult> failedResolutionResults) {
      this.programOrClasspathResult = programOrClasspathResult;
      this.otherProgramOrClasspathResults = programOrClasspathResults;
      this.libraryResolutionResults = libraryResolutionResults;
      this.failedResolutionResults = failedResolutionResults;
    }

    @Override
    public OptionalBool isAccessibleFrom(
        ProgramDefinition context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      throw new Unreachable("Should not be called on MultipleFieldResolutionResult");
    }

    @Override
    public OptionalBool isAccessibleForVirtualDispatchFrom(
        ProgramDefinition context, AppView<? extends AppInfoWithClassHierarchy> appView) {
      throw new Unreachable("Should not be called on MultipleFieldResolutionResult");
    }

    @Override
    public boolean isVirtualTarget() {
      throw new Unreachable("Should not be called on MultipleFieldResolutionResult");
    }

    @Override
    public DexClassAndMethod lookupInvokeSpecialTarget(
        DexProgramClass context, AppView<? extends AppInfoWithClassHierarchy> appView) {
      throw new Unreachable("Should not be called on MultipleFieldResolutionResult");
    }

    @Override
    public DexClassAndMethod lookupInvokeSuperTarget(
        DexProgramClass context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      throw new Unreachable("Should not be called on MultipleFieldResolutionResult");
    }

    @Override
    public DexClassAndMethod lookupInvokeDirectTarget(
        DexProgramClass context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      throw new Unreachable("Should not be called on MultipleFieldResolutionResult");
    }

    @Override
    public DexClassAndMethod lookupInvokeStaticTarget(
        DexProgramClass context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      throw new Unreachable("Should not be called on MultipleFieldResolutionResult");
    }

    @Override
    public LookupResult lookupVirtualDispatchTargets(
        DexProgramClass context,
        AppView<? extends AppInfoWithClassHierarchy> appView,
        InstantiatedSubTypeInfo instantiatedInfo,
        PinnedPredicate pinnedPredicate) {
      throw new Unreachable("Should not be called on MultipleFieldResolutionResult");
    }

    @Override
    public LookupResult lookupVirtualDispatchTargets(
        DexProgramClass context,
        AppView<AppInfoWithLiveness> appView,
        DexProgramClass refinedReceiverUpperBound,
        DexProgramClass refinedReceiverLowerBound) {
      throw new Unreachable("Should not be called on MultipleFieldResolutionResult");
    }

    @Override
    public LookupTarget lookupVirtualDispatchTarget(
        InstantiatedObject instance, AppInfoWithClassHierarchy appInfo) {
      throw new Unreachable("Should not be called on MultipleFieldResolutionResult");
    }

    @Override
    public DexClassAndMethod lookupVirtualDispatchTarget(
        DexClass dynamicInstance, AppInfoWithClassHierarchy appInfo) {
      throw new Unreachable("Should not be called on MultipleFieldResolutionResult");
    }

    @Override
    public LookupTarget lookupVirtualDispatchTarget(
        LambdaDescriptor lambdaInstance,
        AppInfoWithClassHierarchy appInfo,
        Consumer<DexType> typeCausingFailureConsumer,
        Consumer<? super DexEncodedMethod> methodCausingFailureConsumer) {
      throw new Unreachable("Should not be called on MultipleFieldResolutionResult");
    }

    @Override
    public void visitMethodResolutionResults(
        Consumer<? super SingleResolutionResult<? extends ProgramOrClasspathClass>>
            programOrClasspathConsumer,
        Consumer<? super SingleLibraryResolutionResult> libraryResultConsumer,
        Consumer<? super ArrayCloneMethodResult> cloneResultConsumer,
        Consumer<? super FailedResolutionResult> failedResolutionConsumer) {
      if (programOrClasspathResult != null) {
        programOrClasspathConsumer.accept(programOrClasspathResult);
      }
      if (otherProgramOrClasspathResults != null) {
        otherProgramOrClasspathResults.forEach(programOrClasspathConsumer);
      }
      libraryResolutionResults.forEach(libraryResultConsumer);
      failedResolutionResults.forEach(failedResolutionConsumer);
    }

    @Override
    public boolean isMultiMethodResolutionResult() {
      return true;
    }
  }

  public static class MultipleProgramWithLibraryResolutionResult
      extends MultipleMethodResolutionResult<DexProgramClass, SingleProgramResolutionResult> {

    public MultipleProgramWithLibraryResolutionResult(
        SingleProgramResolutionResult programOrClasspathResult,
        List<SingleLibraryResolutionResult> libraryResolutionResults,
        List<FailedResolutionResult> failedOrUnknownResolutionResults) {
      super(
          programOrClasspathResult,
          null,
          libraryResolutionResults,
          failedOrUnknownResolutionResults);
    }
  }

  public static class MultipleClasspathWithLibraryResolutionResult
      extends MultipleMethodResolutionResult<DexClasspathClass, SingleClasspathResolutionResult> {

    public MultipleClasspathWithLibraryResolutionResult(
        SingleClasspathResolutionResult programOrClasspathResult,
        List<SingleLibraryResolutionResult> libraryResolutionResults,
        List<FailedResolutionResult> failedOrUnknownResolutionResults) {
      super(
          programOrClasspathResult,
          null,
          libraryResolutionResults,
          failedOrUnknownResolutionResults);
    }
  }

  public static class MultipleLibraryMethodResolutionResult
      extends MultipleMethodResolutionResult<DexProgramClass, SingleProgramResolutionResult> {

    public MultipleLibraryMethodResolutionResult(
        List<SingleLibraryResolutionResult> libraryResolutionResults,
        List<FailedResolutionResult> failedOrUnknownResolutionResults) {
      super(null, null, libraryResolutionResults, failedOrUnknownResolutionResults);
    }
  }

  public static class MultipleMaximallySpecificResolutionResult
      extends MultipleMethodResolutionResult<DexProgramClass, SingleProgramResolutionResult> {

    public MultipleMaximallySpecificResolutionResult(
        List<SingleResolutionResult<? extends ProgramOrClasspathClass>> programOrClasspathResult,
        List<SingleLibraryResolutionResult> libraryResolutionResults,
        List<FailedResolutionResult> failedResolutionResults) {
      super(null, programOrClasspathResult, libraryResolutionResults, failedResolutionResults);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private MethodResolutionResult possiblySingleResult = null;
    private List<MethodResolutionResult> allResults = null;
    private boolean allowMultipleProgramResults = false;

    private Builder() {}

    public void addResolutionResult(MethodResolutionResult result) {
      if (possiblySingleResult == null) {
        possiblySingleResult = result;
        return;
      }
      if (allResults == null) {
        allResults = new ArrayList<>();
        allResults.add(possiblySingleResult);
      }
      allResults.add(result);
    }

    public Builder allowMultipleProgramResults() {
      allowMultipleProgramResults = true;
      return this;
    }

    public MethodResolutionResult buildOrIfEmpty(
        MethodResolutionResult emptyResult, DexType responsibleTypeForNoSuchMethodResult) {
      return buildOrIfEmpty(
          emptyResult, Collections.singletonList(responsibleTypeForNoSuchMethodResult));
    }

    public MethodResolutionResult buildOrIfEmpty(
        MethodResolutionResult emptyResult,
        Collection<DexType> responsibleTypesForNoSuchMethodResult) {
      if (possiblySingleResult == null) {
        return emptyResult;
      } else if (allResults == null) {
        return possiblySingleResult;
      }
      List<SingleResolutionResult<? extends ProgramOrClasspathClass>> programOrClasspathResults =
          new ArrayList<>();
      List<SingleLibraryResolutionResult> libraryResults = new ArrayList<>();
      List<FailedResolutionResult> failedResults = new ArrayList<>();
      Set<NoSuchMethodResult> noSuchMethodResults = Sets.newIdentityHashSet();
      allResults.forEach(
          otherResult -> {
            otherResult.visitMethodResolutionResults(
                otherProgramOrClasspathResult -> {
                  if (!programOrClasspathResults.isEmpty() && !allowMultipleProgramResults) {
                    assert false : "Unexpected multiple results between program and classpath";
                  }
                  programOrClasspathResults.add(otherProgramOrClasspathResult);
                },
                newLibraryResult -> {
                  if (!Iterables.any(
                      libraryResults,
                      existing ->
                          existing.getResolvedHolder() == newLibraryResult.getResolvedHolder())) {
                    libraryResults.add(newLibraryResult);
                  }
                },
                ConsumerUtils.emptyConsumer(),
                newFailedResult -> {
                  if (newFailedResult.internalIsInstanceOfNoSuchMethodResult()) {
                    noSuchMethodResults.add(newFailedResult.asNoSuchMethodResult());
                  }
                  if (!Iterables.any(failedResults, existing -> existing == newFailedResult)) {
                    failedResults.add(newFailedResult);
                  }
                });
          });
      // If we have seen a NoSuchMethod and also a successful result it must be because we have
      // multiple definitions of a type. Here we compute a single NoSuchMethodResult with root types
      // that must be preserved to still observe the NoSuchMethodError.
      if (!noSuchMethodResults.isEmpty()) {
        if (!libraryResults.isEmpty() || !programOrClasspathResults.isEmpty()) {
          failedResults.add(
              mergeNoSuchMethodErrors(noSuchMethodResults, responsibleTypesForNoSuchMethodResult));
        } else {
          failedResults.add(NoSuchMethodResult.INSTANCE);
        }
      }
      if (programOrClasspathResults.isEmpty()) {
        if (libraryResults.size() == 1 && failedResults.isEmpty()) {
          return libraryResults.get(0);
        } else if (libraryResults.isEmpty() && failedResults.size() == 1) {
          return failedResults.get(0);
        } else {
          return new MultipleLibraryMethodResolutionResult(libraryResults, failedResults);
        }
      } else if (libraryResults.isEmpty()
          && failedResults.isEmpty()
          && programOrClasspathResults.size() == 1) {
        return programOrClasspathResults.get(0);
      } else if (programOrClasspathResults.size() == 1) {
        SingleResolutionResult<?> singleResult = programOrClasspathResults.get(0);
        if (singleResult.hasProgramResult()) {
          return new MultipleProgramWithLibraryResolutionResult(
              singleResult.asSingleProgramResolutionResult(), libraryResults, failedResults);
        } else {
          SingleClasspathResolutionResult classpathResult =
              singleResult.asSingleClasspathResolutionResult();
          assert classpathResult != null;
          return new MultipleClasspathWithLibraryResolutionResult(
              classpathResult, libraryResults, failedResults);
        }
      } else {
        // This must be a maximally specific result since we have multiple program or classpath
        // values.
        return new MultipleMaximallySpecificResolutionResult(
            programOrClasspathResults, libraryResults, failedResults);
      }
    }

    private NoSuchMethodResult mergeNoSuchMethodErrors(
        Set<NoSuchMethodResult> noSuchMethodErrors, Collection<DexType> typesCausingErrorsHere) {
      Set<DexType> typesCausingError = SetUtils.newIdentityHashSet(typesCausingErrorsHere);
      noSuchMethodErrors.forEach(
          failedResolutionResult -> {
            assert failedResolutionResult == NoSuchMethodResult.INSTANCE
                || failedResolutionResult.isNoSuchMethodResultDueToMultipleClassDefinitions();
            if (failedResolutionResult.typesCausingError != null) {
              typesCausingError.addAll(failedResolutionResult.typesCausingError);
            }
          });
      return typesCausingError.isEmpty()
          ? NoSuchMethodResult.INSTANCE
          : new NoSuchMethodResultDueToMultipleClassDefinitions(typesCausingError);
    }
  }

  public static class UnknownMethodResolutionResult extends MethodResolutionResult {

    private static final UnknownMethodResolutionResult INSTANCE =
        new UnknownMethodResolutionResult();

    private UnknownMethodResolutionResult() {}

    static UnknownMethodResolutionResult get() {
      return INSTANCE;
    }

    @Override
    public OptionalBool isAccessibleFrom(
        ProgramDefinition context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      return OptionalBool.unknown();
    }

    @Override
    public OptionalBool isAccessibleForVirtualDispatchFrom(
        ProgramDefinition context, AppView<? extends AppInfoWithClassHierarchy> appView) {
      return OptionalBool.unknown();
    }

    @Override
    public boolean isVirtualTarget() {
      throw new Unreachable();
    }

    @Override
    public DexClassAndMethod lookupInvokeSpecialTarget(
        DexProgramClass context, AppView<? extends AppInfoWithClassHierarchy> appView) {
      throw new Unreachable();
    }

    @Override
    public DexClassAndMethod lookupInvokeSuperTarget(
        DexProgramClass context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      throw new Unreachable();
    }

    @Override
    public DexClassAndMethod lookupInvokeDirectTarget(
        DexProgramClass context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      throw new Unreachable();
    }

    @Override
    public DexClassAndMethod lookupInvokeStaticTarget(
        DexProgramClass context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      throw new Unreachable();
    }

    @Override
    public LookupResult lookupVirtualDispatchTargets(
        DexProgramClass context,
        AppView<? extends AppInfoWithClassHierarchy> appView,
        InstantiatedSubTypeInfo instantiatedInfo,
        PinnedPredicate pinnedPredicate) {
      throw new Unreachable();
    }

    @Override
    public LookupResult lookupVirtualDispatchTargets(
        DexProgramClass context,
        AppView<AppInfoWithLiveness> appView,
        DexProgramClass refinedReceiverUpperBound,
        DexProgramClass refinedReceiverLowerBound) {
      throw new Unreachable();
    }

    @Override
    public LookupTarget lookupVirtualDispatchTarget(
        InstantiatedObject instance, AppInfoWithClassHierarchy appInfo) {
      throw new Unreachable();
    }

    @Override
    public LookupMethodTarget lookupVirtualDispatchTarget(
        DexClass dynamicInstance, AppInfoWithClassHierarchy appInfo) {
      throw new Unreachable();
    }

    @Override
    public LookupTarget lookupVirtualDispatchTarget(
        LambdaDescriptor lambdaInstance,
        AppInfoWithClassHierarchy appInfo,
        Consumer<DexType> typeCausingFailureConsumer,
        Consumer<? super DexEncodedMethod> methodCausingFailureConsumer) {
      throw new Unreachable();
    }

    @Override
    public void visitMethodResolutionResults(
        Consumer<? super SingleResolutionResult<? extends ProgramOrClasspathClass>>
            programOrClasspathConsumer,
        Consumer<? super SingleLibraryResolutionResult> libraryResultConsumer,
        Consumer<? super ArrayCloneMethodResult> cloneResultConsumer,
        Consumer<? super FailedResolutionResult> failedResolutionConsumer) {
      throw new Unreachable();
    }
  }
}
