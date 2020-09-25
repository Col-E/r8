// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.graph.LookupResult.LookupResultSuccess.LookupResultCollectionState;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.InstantiatedObject;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.OptionalBool;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public abstract class ResolutionResult extends MemberResolutionResult<DexEncodedMethod, DexMethod> {

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
  public SingleResolutionResult asSingleResolution() {
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

  /**
   * Returns true if resolution failed.
   *
   * <p>Note the disclaimer in the doc of {@code isSingleResolution()}.
   */
  public boolean isFailedResolution() {
    return false;
  }

  public boolean isIncompatibleClassChangeErrorResult() {
    return false;
  }

  /** Returns non-null if isFailedResolution() is true, otherwise null. */
  public FailedResolutionResult asFailedResolution() {
    return null;
  }

  /** Short-hand to get the single resolution method if resolution finds it, null otherwise. */
  public final DexEncodedMethod getSingleTarget() {
    return isSingleResolution() ? asSingleResolution().getResolvedMethod() : null;
  }

  public DexClass getInitialResolutionHolder() {
    return null;
  }

  public abstract OptionalBool isAccessibleForVirtualDispatchFrom(
      ProgramDefinition context, AppInfoWithClassHierarchy appInfo);

  public abstract boolean isVirtualTarget();

  /** Lookup the single target of an invoke-special on this resolution result if possible. */
  public abstract DexEncodedMethod lookupInvokeSpecialTarget(
      DexProgramClass context, AppInfoWithClassHierarchy appInfo);

  /** Lookup the single target of an invoke-super on this resolution result if possible. */
  public abstract DexEncodedMethod lookupInvokeSuperTarget(
      DexProgramClass context, AppInfoWithClassHierarchy appInfo);

  /** Lookup the single target of an invoke-direct on this resolution result if possible. */
  public abstract DexEncodedMethod lookupInvokeDirectTarget(
      DexProgramClass context, AppInfoWithClassHierarchy appInfo);

  /** Lookup the single target of an invoke-static on this resolution result if possible. */
  public abstract DexEncodedMethod lookupInvokeStaticTarget(
      DexProgramClass context, AppInfoWithClassHierarchy appInfo);

  public abstract LookupResult lookupVirtualDispatchTargets(
      DexProgramClass context,
      AppInfoWithClassHierarchy appInfo,
      InstantiatedSubTypeInfo instantiatedInfo,
      PinnedPredicate pinnedPredicate);

  public final LookupResult lookupVirtualDispatchTargets(
      DexProgramClass context, AppInfoWithLiveness appInfo) {
    return lookupVirtualDispatchTargets(
        context, appInfo, appInfo, appInfo::isPinnedNotProgramOrLibraryOverride);
  }

  public abstract LookupResult lookupVirtualDispatchTargets(
      DexProgramClass context,
      AppInfoWithLiveness appInfo,
      DexProgramClass refinedReceiverUpperBound,
      DexProgramClass refinedReceiverLowerBound);

  public abstract LookupTarget lookupVirtualDispatchTarget(
      InstantiatedObject instance, AppInfoWithClassHierarchy appInfo);

  public abstract DexClassAndMethod lookupVirtualDispatchTarget(
      DexClass dynamicInstance, AppInfoWithClassHierarchy appInfo);

  public abstract LookupTarget lookupVirtualDispatchTarget(
      LambdaDescriptor lambdaInstance, AppInfoWithClassHierarchy appInfo);

  /** Result for a resolution that succeeds with a known declaration/definition. */
  public static class SingleResolutionResult extends ResolutionResult
      implements SuccessfulMemberResolutionResult<DexEncodedMethod, DexMethod> {
    private final DexClass initialResolutionHolder;
    private final DexClass resolvedHolder;
    private final DexEncodedMethod resolvedMethod;

    public SingleResolutionResult(
        DexClass initialResolutionHolder,
        DexClass resolvedHolder,
        DexEncodedMethod resolvedMethod) {
      assert initialResolutionHolder != null;
      assert resolvedHolder != null;
      assert resolvedMethod != null;
      assert resolvedHolder.type == resolvedMethod.holder();
      this.resolvedHolder = resolvedHolder;
      this.resolvedMethod = resolvedMethod;
      this.initialResolutionHolder = initialResolutionHolder;
      assert !resolvedMethod.isPrivateMethod()
          || initialResolutionHolder.type == resolvedMethod.holder();
    }

    @Override
    public DexClass getInitialResolutionHolder() {
      return initialResolutionHolder;
    }

    @Override
    public DexClass getResolvedHolder() {
      return resolvedHolder;
    }

    @Override
    public DexEncodedMethod getResolvedMember() {
      return resolvedMethod;
    }

    public DexEncodedMethod getResolvedMethod() {
      return resolvedMethod;
    }

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
    public SingleResolutionResult asSingleResolution() {
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
        ProgramDefinition context, AppInfoWithClassHierarchy appInfo) {
      return AccessControl.isMemberAccessible(this, context, appInfo);
    }

    @Override
    public OptionalBool isAccessibleForVirtualDispatchFrom(
        ProgramDefinition context, AppInfoWithClassHierarchy appInfo) {
      if (resolvedMethod.isVirtualMethod()) {
        return isAccessibleFrom(context, appInfo);
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
    public DexEncodedMethod lookupInvokeSpecialTarget(
        DexProgramClass context, AppInfoWithClassHierarchy appInfo) {
      // If the resolution is non-accessible then no target exists.
      if (isAccessibleFrom(context, appInfo).isPossiblyTrue()) {
        return internalInvokeSpecialOrSuper(
            context, appInfo, (sup, sub) -> isSuperclass(sup, sub, appInfo));
      }
      return null;
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
    public DexEncodedMethod lookupInvokeSuperTarget(
        DexProgramClass context, AppInfoWithClassHierarchy appInfo) {
      if (resolvedMethod.isInstanceInitializer()
          || (initialResolutionHolder != context
              && !isSuperclass(initialResolutionHolder, context, appInfo))) {
        // If the target is <init> or not on a super class then the call is invalid.
        return null;
      }
      if (isAccessibleFrom(context, appInfo).isPossiblyTrue()) {
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
    public DexEncodedMethod lookupInvokeStaticTarget(
        DexProgramClass context, AppInfoWithClassHierarchy appInfo) {
      if (isAccessibleFrom(context, appInfo).isFalse()) {
        return null;
      }
      if (resolvedMethod.isStatic()) {
        return resolvedMethod;
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
    public DexEncodedMethod lookupInvokeDirectTarget(
        DexProgramClass context, AppInfoWithClassHierarchy appInfo) {
      if (isAccessibleFrom(context, appInfo).isFalse()) {
        return null;
      }
      if (resolvedMethod.isDirectMethod()) {
        return resolvedMethod;
      }
      return null;
    }

    private DexEncodedMethod internalInvokeSpecialOrSuper(
        DexProgramClass context,
        AppInfoWithClassHierarchy appInfo,
        BiPredicate<DexClass, DexClass> isSuperclass) {

      // Statics cannot be targeted by invoke-special/super.
      if (getResolvedMethod().isStatic()) {
        return null;
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
        initialType = context.superType == null ? null : appInfo.definitionFor(context.superType);
      } else {
        // Otherwise it starts at the reference itself.
        initialType = symbolicReference;
      }
      // Abort if for some reason the starting point could not be found.
      if (initialType == null) {
        return null;
      }
      // 1-3. Search the initial class and its supers in order for a matching instance method.
      DexMethod method = getResolvedMethod().method;
      DexEncodedMethod target = null;
      DexClass current = initialType;
      while (current != null) {
        target = current.lookupMethod(method);
        if (target != null) {
          break;
        }
        current = current.superType == null ? null : appInfo.definitionFor(current.superType);
      }
      // 4. Otherwise, it is the single maximally specific method:
      if (target == null) {
        DexClassAndMethod result = appInfo.lookupMaximallySpecificMethod(initialType, method);
        if (result != null) {
          target = result.getDefinition();
        }
      }
      if (target == null) {
        return null;
      }
      // Linking exceptions:
      // A non-instance method throws IncompatibleClassChangeError.
      if (target.isStatic()) {
        return null;
      }
      // An instance initializer that is not to the symbolic reference throws NoSuchMethodError.
      // It appears as if this check is also in place for non-initializer methods too.
      // See NestInvokeSpecialMethodAccessWithIntermediateTest.
      if ((target.isInstanceInitializer() || target.isPrivateMethod())
          && target.holder() != symbolicReference.type) {
        return null;
      }
      // Runtime exceptions:
      // An abstract method throws AbstractMethodError.
      if (target.isAbstract()) {
        return null;
      }
      return target;
    }

    private static boolean isSuperclass(
        DexClass sup, DexClass sub, AppInfoWithClassHierarchy appInfo) {
      return appInfo.isStrictSubtypeOf(sub.type, sup.type);
    }

    @Override
    public LookupResult lookupVirtualDispatchTargets(
        DexProgramClass context,
        AppInfoWithClassHierarchy appInfo,
        InstantiatedSubTypeInfo instantiatedInfo,
        PinnedPredicate pinnedPredicate) {
      // Check that the initial resolution holder is accessible from the context.
      assert appInfo.isSubtype(initialResolutionHolder.type, resolvedHolder.type)
          : initialResolutionHolder.type + " is not a subtype of " + resolvedHolder.type;
      if (context != null && isAccessibleFrom(context, appInfo).isFalse()) {
        return LookupResult.createFailedResult();
      }
      if (resolvedMethod.isPrivateMethod()) {
        // If the resolved reference is private there is no dispatch.
        // This is assuming that the method is accessible, which implies self/nest access.
        // Only include if the target has code or is native.
        boolean isIncomplete =
            pinnedPredicate.isPinned(resolvedHolder) && pinnedPredicate.isPinned(resolvedMethod);
        return LookupResult.createResult(
            Collections.singletonMap(
                resolvedMethod, DexClassAndMethod.create(resolvedHolder, resolvedMethod)),
            Collections.emptyList(),
            isIncomplete
                ? LookupResultCollectionState.Incomplete
                : LookupResultCollectionState.Complete);
      }
      assert resolvedMethod.isNonPrivateVirtualMethod();
      Map<DexEncodedMethod, DexClassAndMethod> methodTargets = new IdentityHashMap<>();
      List<LookupLambdaTarget> lambdaTargets = new ArrayList<>();
      LookupCompletenessHelper incompleteness = new LookupCompletenessHelper(pinnedPredicate);
      instantiatedInfo.forEachInstantiatedSubType(
          initialResolutionHolder.type,
          subClass -> {
            incompleteness.checkClass(subClass);
            DexClassAndMethod dexClassAndMethod =
                lookupVirtualDispatchTarget(subClass, appInfo, resolvedHolder.type);
            if (dexClassAndMethod != null) {
              incompleteness.checkDexClassAndMethod(dexClassAndMethod);
              addVirtualDispatchTarget(
                  dexClassAndMethod, resolvedHolder.isInterface(), methodTargets);
            }
          },
          lambda -> {
            assert resolvedHolder.isInterface()
                || resolvedHolder.type == appInfo.dexItemFactory().objectType;
            LookupTarget target = lookupVirtualDispatchTarget(lambda, appInfo);
            if (target != null) {
              if (target.isLambdaTarget()) {
                lambdaTargets.add(target.asLambdaTarget());
              } else {
                addVirtualDispatchTarget(
                    target.asMethodTarget(), resolvedHolder.isInterface(), methodTargets);
              }
            }
          });
      return LookupResult.createResult(
          methodTargets,
          lambdaTargets,
          incompleteness.computeCollectionState(resolvedMethod.method, appInfo));
    }

    @Override
    public LookupResult lookupVirtualDispatchTargets(
        DexProgramClass context,
        AppInfoWithLiveness appInfo,
        DexProgramClass refinedReceiverUpperBound,
        DexProgramClass refinedReceiverLowerBound) {
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
              appInfo,
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
        DexClassAndMethod target,
        boolean holderIsInterface,
        Map<DexEncodedMethod, DexClassAndMethod> result) {
      DexEncodedMethod targetMethod = target.getDefinition();
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
          result.putIfAbsent(targetMethod, target);
        }
        // Default methods are looked up when looking at a specific subtype that does not override
        // them. Otherwise, we would look up default methods that are actually never used.
        // However, we have to add bridge methods, otherwise we can remove a bridge that will be
        // used.
        if (!targetMethod.accessFlags.isAbstract() && targetMethod.accessFlags.isBridge()) {
          result.putIfAbsent(targetMethod, target);
        }
      } else {
        result.putIfAbsent(targetMethod, target);
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
          : lookupVirtualDispatchTarget(instance.asLambda(), appInfo);
    }

    @Override
    public DexClassAndMethod lookupVirtualDispatchTarget(
        DexClass dynamicInstance, AppInfoWithClassHierarchy appInfo) {
      return lookupVirtualDispatchTarget(dynamicInstance, appInfo, initialResolutionHolder.type);
    }

    @Override
    public LookupTarget lookupVirtualDispatchTarget(
        LambdaDescriptor lambdaInstance, AppInfoWithClassHierarchy appInfo) {
      if (lambdaInstance.getMainMethod().match(resolvedMethod)) {
        DexMethod method = lambdaInstance.implHandle.asMethod();
        DexClass holder = appInfo.definitionForHolder(method);
        if (holder == null) {
          assert false;
          return null;
        }
        DexEncodedMethod definition = holder.lookupMethod(method);
        if (definition == null) {
          // The targeted method might not exist, eg, Throwable.addSuppressed in an old library.
          return null;
        }
        return new LookupLambdaTarget(lambdaInstance, DexClassAndMethod.create(holder, definition));
      }
      return lookupMaximallySpecificDispatchTarget(lambdaInstance, appInfo);
    }

    private DexClassAndMethod lookupVirtualDispatchTarget(
        DexClass dynamicInstance, AppInfoWithClassHierarchy appInfo, DexType resolutionHolder) {
      assert appInfo.isSubtype(dynamicInstance.type, resolutionHolder)
          : dynamicInstance.type + " is not a subtype of " + resolutionHolder;
      // TODO(b/148591377): Enable this assertion.
      // The dynamic type cannot be an interface.
      // assert !dynamicInstance.isInterface();
      if (resolvedMethod.isPrivateMethod()) {
        // If the resolved reference is private there is no dispatch.
        // This is assuming that the method is accessible, which implies self/nest access.
        return DexClassAndMethod.create(resolvedHolder, resolvedMethod);
      }
      boolean allowPackageBlocked = resolvedMethod.accessFlags.isPackagePrivate();
      DexClass current = dynamicInstance;
      DexEncodedMethod overrideTarget = resolvedMethod;
      while (current != null) {
        DexEncodedMethod candidate = lookupOverrideCandidate(overrideTarget, current);
        if (candidate == DexEncodedMethod.SENTINEL && allowPackageBlocked) {
          overrideTarget = findWideningOverride(resolvedMethod, current, appInfo);
          allowPackageBlocked = false;
          continue;
        }
        if (candidate == null || candidate == DexEncodedMethod.SENTINEL) {
          // We cannot find a target above the resolved method.
          if (current.type == overrideTarget.holder()) {
            return null;
          }
          current = current.superType == null ? null : appInfo.definitionFor(current.superType);
          continue;
        }
        return DexClassAndMethod.create(current, candidate);
      }
      // If we have not found a candidate and the holder is not an interface it must be because the
      // class is missing.
      if (!resolvedHolder.isInterface()) {
        return null;
      }
      return lookupMaximallySpecificDispatchTarget(dynamicInstance, appInfo);
    }

    private DexClassAndMethod lookupMaximallySpecificDispatchTarget(
        DexClass dynamicInstance, AppInfoWithClassHierarchy appInfo) {
      return appInfo.lookupMaximallySpecificMethod(dynamicInstance, resolvedMethod.method);
    }

    private DexClassAndMethod lookupMaximallySpecificDispatchTarget(
        LambdaDescriptor lambdaDescriptor, AppInfoWithClassHierarchy appInfo) {
      return appInfo.lookupMaximallySpecificMethod(lambdaDescriptor, resolvedMethod.method);
    }

    /**
     * C contains a declaration for an instance method m that overrides (§5.4.5) the resolved
     * method, then m is the method to be invoked. If the candidate is not a valid override, we
     * return sentinel to indicate that we have to search for a method that is widening access
     * inside the package.
     */
    private static DexEncodedMethod lookupOverrideCandidate(
        DexEncodedMethod method, DexClass clazz) {
      DexEncodedMethod candidate = clazz.lookupVirtualMethod(method.method);
      assert candidate == null || !candidate.isPrivateMethod();
      if (candidate != null) {
        return isOverriding(method, candidate) ? candidate : DexEncodedMethod.SENTINEL;
      }
      return null;
    }

    private static DexEncodedMethod findWideningOverride(
        DexEncodedMethod resolvedMethod, DexClass clazz, AppInfoWithClassHierarchy appView) {
      // Otherwise, lookup to first override that is distinct from resolvedMethod.
      assert resolvedMethod.accessFlags.isPackagePrivate();
      while (clazz.superType != null) {
        clazz = appView.definitionFor(clazz.superType);
        if (clazz == null) {
          return resolvedMethod;
        }
        DexEncodedMethod otherOverride = clazz.lookupVirtualMethod(resolvedMethod.method);
        if (otherOverride != null
            && isOverriding(resolvedMethod, otherOverride)
            && (otherOverride.accessFlags.isPublic() || otherOverride.accessFlags.isProtected())) {
          assert resolvedMethod != otherOverride;
          return otherOverride;
        }
      }
      return resolvedMethod;
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
      assert resolvedMethod.method.match(candidate.method);
      assert !candidate.isPrivateMethod();
      if (resolvedMethod.accessFlags.isPublic() || resolvedMethod.accessFlags.isProtected()) {
        return true;
      }
      // For package private methods, a valid override has to be inside the package.
      assert resolvedMethod.accessFlags.isPackagePrivate();
      return resolvedMethod.holder().isSamePackage(candidate.holder());
    }
  }

  abstract static class EmptyResult extends ResolutionResult {

    @Override
    public final DexEncodedMethod lookupInvokeSpecialTarget(
        DexProgramClass context, AppInfoWithClassHierarchy appInfo) {
      return null;
    }

    @Override
    public DexEncodedMethod lookupInvokeSuperTarget(
        DexProgramClass context, AppInfoWithClassHierarchy appInfo) {
      return null;
    }

    @Override
    public DexEncodedMethod lookupInvokeStaticTarget(
        DexProgramClass context, AppInfoWithClassHierarchy appInfo) {
      return null;
    }

    @Override
    public DexEncodedMethod lookupInvokeDirectTarget(
        DexProgramClass context, AppInfoWithClassHierarchy appInfo) {
      return null;
    }

    @Override
    public LookupResult lookupVirtualDispatchTargets(
        DexProgramClass context,
        AppInfoWithClassHierarchy appInfo,
        InstantiatedSubTypeInfo instantiatedInfo,
        PinnedPredicate pinnedPredicate) {
      return LookupResult.getIncompleteEmptyResult();
    }

    @Override
    public LookupResult lookupVirtualDispatchTargets(
        DexProgramClass context,
        AppInfoWithLiveness appInfo,
        DexProgramClass refinedReceiverUpperBound,
        DexProgramClass refinedReceiverLowerBound) {
      return LookupResult.getIncompleteEmptyResult();
    }

    @Override
    public DexClassAndMethod lookupVirtualDispatchTarget(
        InstantiatedObject instance, AppInfoWithClassHierarchy appInfo) {
      return null;
    }

    @Override
    public DexClassAndMethod lookupVirtualDispatchTarget(
        DexClass dynamicInstance, AppInfoWithClassHierarchy appInfo) {
      return null;
    }

    @Override
    public DexClassAndMethod lookupVirtualDispatchTarget(
        LambdaDescriptor lambdaInstance, AppInfoWithClassHierarchy appInfo) {
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
        ProgramDefinition context, AppInfoWithClassHierarchy appInfo) {
      return OptionalBool.TRUE;
    }

    @Override
    public OptionalBool isAccessibleForVirtualDispatchFrom(
        ProgramDefinition context, AppInfoWithClassHierarchy appInfo) {
      return OptionalBool.TRUE;
    }

    @Override
    public boolean isVirtualTarget() {
      return true;
    }
  }

  /** Base class for all types of failed resolutions. */
  public abstract static class FailedResolutionResult extends EmptyResult {

    @Override
    public boolean isFailedResolution() {
      return true;
    }

    @Override
    public FailedResolutionResult asFailedResolution() {
      return this;
    }

    public void forEachFailureDependency(Consumer<DexEncodedMethod> methodCausingFailureConsumer) {
      // Default failure has no dependencies.
    }

    @Override
    public OptionalBool isAccessibleFrom(
        ProgramDefinition context, AppInfoWithClassHierarchy appInfo) {
      return OptionalBool.FALSE;
    }

    @Override
    public OptionalBool isAccessibleForVirtualDispatchFrom(
        ProgramDefinition context, AppInfoWithClassHierarchy appInfo) {
      return OptionalBool.FALSE;
    }

    @Override
    public boolean isVirtualTarget() {
      return false;
    }
  }

  public static class ClassNotFoundResult extends FailedResolutionResult {
    static final ClassNotFoundResult INSTANCE = new ClassNotFoundResult();

    private ClassNotFoundResult() {
      // Intentionally left empty.
    }
  }

  abstract static class FailedResolutionWithCausingMethods extends FailedResolutionResult {

    private final Collection<DexEncodedMethod> methodsCausingError;

    private FailedResolutionWithCausingMethods(Collection<DexEncodedMethod> methodsCausingError) {
      this.methodsCausingError = methodsCausingError;
    }

    @Override
    public void forEachFailureDependency(Consumer<DexEncodedMethod> methodCausingFailureConsumer) {
      this.methodsCausingError.forEach(methodCausingFailureConsumer);
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
  }

  public static class IllegalAccessOrNoSuchMethodResult extends FailedResolutionWithCausingMethods {

    public IllegalAccessOrNoSuchMethodResult(DexEncodedMethod methodCausingError) {
      super(Collections.singletonList(methodCausingError));
      assert methodCausingError != null;
    }
  }
}
