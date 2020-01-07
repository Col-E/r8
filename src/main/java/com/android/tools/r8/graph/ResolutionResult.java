// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.utils.SetUtils;
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

  public abstract boolean isAccessibleFrom(DexProgramClass context, AppInfoWithSubtyping appInfo);

  public abstract boolean isAccessibleForVirtualDispatchFrom(
      DexProgramClass context, AppInfoWithSubtyping appInfo);

  // TODO(b/145187573): Remove this and use proper access checks.
  @Deprecated
  public abstract boolean isVirtualTarget();

  /** Lookup the single target of an invoke-special on this resolution result if possible. */
  public abstract DexEncodedMethod lookupInvokeSpecialTarget(
      DexProgramClass context, AppInfoWithSubtyping appInfo);

  /** Lookup the single target of an invoke-super on this resolution result if possible. */
  public abstract DexEncodedMethod lookupInvokeSuperTarget(
      DexProgramClass context, AppInfoWithSubtyping appInfo);

  @Deprecated
  public abstract DexEncodedMethod lookupInvokeSuperTarget(DexClass context, AppInfo appInfo);

  public final Set<DexEncodedMethod> lookupVirtualDispatchTargets(
      boolean isInterface, AppInfoWithSubtyping appInfo) {
    return isInterface ? lookupInterfaceTargets(appInfo) : lookupVirtualTargets(appInfo);
  }

  public abstract Set<DexEncodedMethod> lookupVirtualTargets(AppInfoWithSubtyping appInfo);

  public abstract Set<DexEncodedMethod> lookupInterfaceTargets(AppInfoWithSubtyping appInfo);

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
    public boolean isAccessibleFrom(DexProgramClass context, AppInfoWithSubtyping appInfo) {
      return AccessControl.isMethodAccessible(
          resolvedMethod, initialResolutionHolder, context, appInfo);
    }

    @Override
    public boolean isAccessibleForVirtualDispatchFrom(
        DexProgramClass context, AppInfoWithSubtyping appInfo) {
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
        DexProgramClass context, AppInfoWithSubtyping appInfo) {
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
        DexProgramClass context, AppInfoWithSubtyping appInfo) {
      if (!isAccessibleFrom(context, appInfo)) {
        return null;
      }
      DexEncodedMethod target = lookupInvokeSuperTarget(context.asDexClass(), appInfo);
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

    @Override
    public DexEncodedMethod lookupInvokeSuperTarget(DexClass context, AppInfo appInfo) {
      assert context != null;
      if (resolvedMethod.isInstanceInitializer()
          || (appInfo.hasSubtyping()
              && initialResolutionHolder != context
              && !isSuperclass(initialResolutionHolder, context, appInfo.withSubtyping()))) {
        throw new CompilationError(
            "Illegal invoke-super to " + resolvedMethod.toSourceString(), context.getOrigin());
      }
      return internalInvokeSpecialOrSuper(context, appInfo, (sup, sub) -> true);
    }

    private DexEncodedMethod internalInvokeSpecialOrSuper(
        DexClass context, AppInfo appInfo, BiPredicate<DexClass, DexClass> isSuperclass) {

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

    private static boolean isSuperclass(DexClass sup, DexClass sub, AppInfoWithSubtyping appInfo) {
      return sup != sub && appInfo.isSubtype(sub.type, sup.type);
    }

    @Override
    // TODO(b/140204899): Leverage refined receiver type if available.
    public Set<DexEncodedMethod> lookupVirtualTargets(AppInfoWithSubtyping appInfo) {
      if (resolvedMethod.isPrivateMethod()) {
        // If the resolved reference is private there is no dispatch.
        // This is assuming that the method is accessible, which implies self/nest access.
        return Collections.singleton(resolvedMethod);
      }
      assert resolvedMethod.isNonPrivateVirtualMethod();
      // First add the target for receiver type method.type.
      Set<DexEncodedMethod> result = SetUtils.newIdentityHashSet(resolvedMethod);
      // Add all matching targets from the subclass hierarchy.
      DexMethod method = resolvedMethod.method;
      // TODO(b/140204899): Instead of subtypes of holder, we could iterate subtypes of refined
      //   receiver type if available.
      for (DexType type : appInfo.subtypes(method.holder)) {
        DexClass clazz = appInfo.definitionFor(type);
        if (!clazz.isInterface()) {
          ResolutionResult methods = appInfo.resolveMethodOnClass(clazz, method);
          DexEncodedMethod target = methods.getSingleTarget();
          if (target != null && target.isNonPrivateVirtualMethod()) {
            result.add(target);
          }
        }
      }
      return result;
    }

    @Override
    // TODO(b/140204899): Leverage refined receiver type if available.
    public Set<DexEncodedMethod> lookupInterfaceTargets(AppInfoWithSubtyping appInfo) {
      if (resolvedMethod.isPrivateMethod()) {
        // If the resolved reference is private there is no dispatch.
        // This is assuming that the method is accessible, which implies self/nest access.
        assert resolvedMethod.hasCode();
        return Collections.singleton(resolvedMethod);
      }
      assert resolvedMethod.isNonPrivateVirtualMethod();
      Set<DexEncodedMethod> result = Sets.newIdentityHashSet();
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
      if (resolvedMethod.hasCode()) {
        DexProgramClass holder = resolvedHolder.asProgramClass();
        if (appInfo.hasAnyInstantiatedLambdas(holder)) {
          result.add(resolvedMethod);
        }
      }

      DexMethod method = resolvedMethod.method;
      Consumer<DexEncodedMethod> addIfNotAbstract =
          m -> {
            if (!m.accessFlags.isAbstract()) {
              result.add(m);
            }
          };
      // Default methods are looked up when looking at a specific subtype that does not override
      // them.
      // Otherwise, we would look up default methods that are actually never used. However, we have
      // to
      // add bridge methods, otherwise we can remove a bridge that will be used.
      Consumer<DexEncodedMethod> addIfNotAbstractAndBridge =
          m -> {
            if (!m.accessFlags.isAbstract() && m.accessFlags.isBridge()) {
              result.add(m);
            }
          };

      // TODO(b/140204899): Instead of subtypes of holder, we could iterate subtypes of refined
      //   receiver type if available.
      for (DexType type : appInfo.subtypes(method.holder)) {
        DexClass clazz = appInfo.definitionFor(type);
        if (clazz.isInterface()) {
          ResolutionResult targetMethods = appInfo.resolveMethodOnInterface(clazz, method);
          if (targetMethods.isSingleResolution()) {
            addIfNotAbstractAndBridge.accept(targetMethods.getSingleTarget());
          }
        } else {
          ResolutionResult targetMethods = appInfo.resolveMethodOnClass(clazz, method);
          if (targetMethods.isSingleResolution()) {
            addIfNotAbstract.accept(targetMethods.getSingleTarget());
          }
        }
      }
      return result;
    }
  }

  abstract static class EmptyResult extends ResolutionResult {

    @Override
    public final DexEncodedMethod lookupInvokeSpecialTarget(
        DexProgramClass context, AppInfoWithSubtyping appInfo) {
      return null;
    }

    @Override
    public DexEncodedMethod lookupInvokeSuperTarget(
        DexProgramClass context, AppInfoWithSubtyping appInfo) {
      return null;
    }

    @Override
    public final DexEncodedMethod lookupInvokeSuperTarget(DexClass context, AppInfo appInfo) {
      return null;
    }

    @Override
    public final Set<DexEncodedMethod> lookupVirtualTargets(AppInfoWithSubtyping appInfo) {
      return null;
    }

    @Override
    public final Set<DexEncodedMethod> lookupInterfaceTargets(AppInfoWithSubtyping appInfo) {
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
    public boolean isAccessibleFrom(DexProgramClass context, AppInfoWithSubtyping appInfo) {
      return true;
    }

    @Override
    public boolean isAccessibleForVirtualDispatchFrom(
        DexProgramClass context, AppInfoWithSubtyping appInfo) {
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
    public boolean isAccessibleFrom(DexProgramClass context, AppInfoWithSubtyping appInfo) {
      return false;
    }

    @Override
    public boolean isAccessibleForVirtualDispatchFrom(
        DexProgramClass context, AppInfoWithSubtyping appInfo) {
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
