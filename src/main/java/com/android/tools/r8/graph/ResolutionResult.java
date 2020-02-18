// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.LookupResult.LookupResultSuccess.LookupResultCollectionState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public abstract class ResolutionResult {

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

  /**
   * Returns true if resolution failed.
   *
   * <p>Note the disclaimer in the doc of {@code isSingleResolution()}.
   */
  public boolean isFailedResolution() {
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

  public abstract boolean isAccessibleFrom(
      DexProgramClass context, AppInfoWithClassHierarchy appInfo);

  public abstract boolean isAccessibleForVirtualDispatchFrom(
      DexProgramClass context, AppInfoWithClassHierarchy appInfo);

  // TODO(b/145187573): Remove this and use proper access checks.
  @Deprecated
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
      AppView<? extends AppInfoWithClassHierarchy> appView,
      InstantiatedSubTypeInfo instantiatedInfo);

  public final LookupResult lookupVirtualDispatchTargets(
      DexProgramClass context, AppView<AppInfoWithLiveness> appView) {
    return lookupVirtualDispatchTargets(context, appView, appView.appInfo());
  }

  public abstract DexClassAndMethod lookupVirtualDispatchTarget(
      DexProgramClass dynamicInstance, AppView<? extends AppInfoWithClassHierarchy> appView);

  /** Result for a resolution that succeeds with a known declaration/definition. */
  public static class SingleResolutionResult extends ResolutionResult {
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
      assert resolvedHolder.type == resolvedMethod.method.holder;
      this.resolvedHolder = resolvedHolder;
      this.resolvedMethod = resolvedMethod;
      this.initialResolutionHolder = initialResolutionHolder;
      assert !resolvedMethod.isPrivateMethod()
          || initialResolutionHolder.type == resolvedMethod.method.holder;
    }

    public DexClass getResolvedHolder() {
      return resolvedHolder;
    }

    public DexEncodedMethod getResolvedMethod() {
      return resolvedMethod;
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
    public boolean isAccessibleFrom(DexProgramClass context, AppInfoWithClassHierarchy appInfo) {
      return AccessControl.isMethodAccessible(
          resolvedMethod, initialResolutionHolder, context, appInfo);
    }

    @Override
    public boolean isAccessibleForVirtualDispatchFrom(
        DexProgramClass context, AppInfoWithClassHierarchy appInfo) {
      return resolvedMethod.isVirtualMethod() && isAccessibleFrom(context, appInfo);
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
      if (!isAccessibleFrom(context, appInfo)) {
        return null;
      }
      DexEncodedMethod target =
          internalInvokeSpecialOrSuper(
              context, appInfo, (sup, sub) -> isSuperclass(sup, sub, appInfo));
      if (target == null) {
        return null;
      }
      // Should we check access control again?
      DexClass holder = appInfo.definitionFor(target.method.holder);
      if (!AccessControl.isMethodAccessible(target, holder, context, appInfo)) {
        return null;
      }
      return target;
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
      // TODO(b/147848950): Investigate and remove the Compilation error. It could compile to
      // throw IAE.
      if (resolvedMethod.isInstanceInitializer()
          || (appInfo.hasSubtyping()
              && initialResolutionHolder != context
              && !isSuperclass(initialResolutionHolder, context, appInfo.withSubtyping()))) {
        throw new CompilationError(
            "Illegal invoke-super to " + resolvedMethod.toSourceString(), context.getOrigin());
      }
      if (!isAccessibleFrom(context, appInfo)) {
        return null;
      }
      DexEncodedMethod target = internalInvokeSpecialOrSuper(context, appInfo, (sup, sub) -> true);
      if (target == null) {
        return null;
      }
      // Should we check access control again?
      DexClass holder = appInfo.definitionFor(target.method.holder);
      if (!AccessControl.isMethodAccessible(target, holder, context, appInfo)) {
        return null;
      }
      return target;
    }

    /**
     * Lookup the target of an invoke-static.
     *
     * <p>This method will resolve the method on the holder and only return a non-null value if the
     * result of resolution was a static, non-abstract method.
     *
     * @param context Class the invoke is contained in, i.e., the holder of the caller.
     *      * @param appInfo Application info.
     * @return The actual target or {@code null} if none found.
     */
    @Override
    public DexEncodedMethod lookupInvokeStaticTarget(DexProgramClass context,
        AppInfoWithClassHierarchy appInfo) {
      if (!isAccessibleFrom(context, appInfo)) {
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
      if (!isAccessibleFrom(context, appInfo)) {
        return null;
      }
      if (resolvedMethod.isDirectMethod()) {
        return resolvedMethod;
      }
      return null;
    }

    private DexEncodedMethod internalInvokeSpecialOrSuper(
        DexClass context,
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
        target = appInfo.resolveMaximallySpecificMethods(initialType, method).getSingleTarget();
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
          && target.method.holder != symbolicReference.type) {
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
      return sup != sub && appInfo.isSubtype(sub.type, sup.type);
    }

    @Override
    public LookupResult lookupVirtualDispatchTargets(
        DexProgramClass context,
        AppView<? extends AppInfoWithClassHierarchy> appView,
        InstantiatedSubTypeInfo instantiatedInfo) {
      // Check that the initial resolution holder is accessible from the context.
      if (context != null && !isAccessibleFrom(context, appView.appInfo())) {
        return LookupResult.createFailedResult();
      }
      if (resolvedMethod.isPrivateMethod()) {
        // If the resolved reference is private there is no dispatch.
        // This is assuming that the method is accessible, which implies self/nest access.
        // Only include if the target has code or is native.
        return LookupResult.createResult(
            Collections.singleton(resolvedMethod), LookupResultCollectionState.Complete);
      }
      assert resolvedMethod.isNonPrivateVirtualMethod();
      Set<DexEncodedMethod> result = Sets.newIdentityHashSet();
      instantiatedInfo.forEachInstantiatedSubType(
          resolvedHolder.type,
          subClass -> {
            DexClassAndMethod dexClassAndMethod = lookupVirtualDispatchTarget(subClass, appView);
            if (dexClassAndMethod != null) {
              addVirtualDispatchTarget(
                  dexClassAndMethod.getMethod(), resolvedHolder.isInterface(), result);
            }
          },
          dexCallSite -> {
            // TODO(b/148769279): We need to look at the call site to see if it overrides
            //   the resolved method or not.
          });
      return LookupResult.createResult(result, LookupResultCollectionState.Complete);
    }

    private static void addVirtualDispatchTarget(
        DexEncodedMethod target, boolean holderIsInterface, Set<DexEncodedMethod> result) {
      assert !target.isPrivateMethod();
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
        if (target.isDefaultMethod()) {
          result.add(target);
        }
        // Default methods are looked up when looking at a specific subtype that does not override
        // them. Otherwise, we would look up default methods that are actually never used.
        // However, we have to add bridge methods, otherwise we can remove a bridge that will be
        // used.
        if (!target.accessFlags.isAbstract() && target.accessFlags.isBridge()) {
          result.add(target);
        }
      } else {
        result.add(target);
      }
    }

    /**
     * This implements the logic for the actual method selection for a virtual target, according to
     * https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-6.html#jvms-6.5.invokevirtual where
     * we have an object ref on the stack.
     */
    @Override
    public DexClassAndMethod lookupVirtualDispatchTarget(
        DexProgramClass dynamicInstance, AppView<? extends AppInfoWithClassHierarchy> appView) {
      // TODO(b/148591377): Enable this assertion.
      // The dynamic type cannot be an interface.
      // assert !dynamicInstance.isInterface();
      boolean allowPackageBlocked = resolvedMethod.accessFlags.isPackagePrivate();
      DexClass current = dynamicInstance;
      DexEncodedMethod overrideTarget = resolvedMethod;
      while (current != null) {
        DexEncodedMethod candidate = lookupOverrideCandidate(overrideTarget, current);
        if (candidate == DexEncodedMethod.SENTINEL && allowPackageBlocked) {
          overrideTarget = findWideningOverride(resolvedMethod, current, appView);
          allowPackageBlocked = false;
          continue;
        }
        if (candidate == null || candidate == DexEncodedMethod.SENTINEL) {
          // We cannot find a target above the resolved method.
          if (current.type == overrideTarget.method.holder) {
            return null;
          }
          current = current.superType == null ? null : appView.definitionFor(current.superType);
          continue;
        }
        return DexClassAndMethod.create(current, candidate);
      }
      // TODO(b/149557233): Enable assertion.
      // assert resolvedHolder.isInterface();
      DexEncodedMethod maximalSpecific =
          lookupMaximallySpecificDispatchTarget(dynamicInstance, appView);
      return maximalSpecific == null
          ? null
          : DexClassAndMethod.create(
              appView.definitionFor(maximalSpecific.method.holder), maximalSpecific);
    }

    private DexEncodedMethod lookupMaximallySpecificDispatchTarget(
        DexProgramClass dynamicInstance, AppView<? extends AppInfoWithClassHierarchy> appView) {
      ResolutionResult maximallySpecificResult =
          appView.appInfo().resolveMaximallySpecificMethods(dynamicInstance, resolvedMethod.method);
      if (maximallySpecificResult.isSingleResolution()) {
        return maximallySpecificResult.asSingleResolution().resolvedMethod;
      }
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
      DexEncodedMethod candidate = clazz.lookupVirtualMethod(method.method);
      assert candidate == null || !candidate.isPrivateMethod();
      if (candidate != null) {
        return isOverriding(method, candidate) ? candidate : DexEncodedMethod.SENTINEL;
      }
      return null;
    }

    private static DexEncodedMethod findWideningOverride(
        DexEncodedMethod resolvedMethod,
        DexClass clazz,
        AppView<? extends AppInfoWithClassHierarchy> appView) {
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
    private static boolean isOverriding(
        DexEncodedMethod resolvedMethod, DexEncodedMethod candidate) {
      assert resolvedMethod.method.match(candidate.method);
      assert !candidate.isPrivateMethod();
      if (resolvedMethod.accessFlags.isPublic() || resolvedMethod.accessFlags.isProtected()) {
        return true;
      }
      // For package private methods, a valid override has to be inside the package.
      assert resolvedMethod.accessFlags.isPackagePrivate();
      return resolvedMethod.method.holder.isSamePackage(candidate.method.holder);
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
    public DexEncodedMethod lookupInvokeStaticTarget(DexProgramClass context,
        AppInfoWithClassHierarchy appInfo) {
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
        AppView<? extends AppInfoWithClassHierarchy> appView,
        InstantiatedSubTypeInfo instantiatedInfo) {
      return LookupResult.getIncompleteEmptyResult();
    }

    @Override
    public DexClassAndMethod lookupVirtualDispatchTarget(
        DexProgramClass dynamicInstance, AppView<? extends AppInfoWithClassHierarchy> appView) {
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
    public boolean isAccessibleFrom(DexProgramClass context, AppInfoWithClassHierarchy appInfo) {
      return true;
    }

    @Override
    public boolean isAccessibleForVirtualDispatchFrom(
        DexProgramClass context, AppInfoWithClassHierarchy appInfo) {
      return true;
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
    public boolean isAccessibleFrom(DexProgramClass context, AppInfoWithClassHierarchy appInfo) {
      return false;
    }

    @Override
    public boolean isAccessibleForVirtualDispatchFrom(
        DexProgramClass context, AppInfoWithClassHierarchy appInfo) {
      return false;
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
