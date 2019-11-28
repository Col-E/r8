// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;

public abstract class ResolutionResult {

  public boolean isFailedResolution() {
    return false;
  }

  public FailedResolutionResult asFailedResolution() {
    return null;
  }

  public abstract DexEncodedMethod getSingleTarget();

  public abstract boolean hasSingleTarget();

  public abstract boolean isValidVirtualTarget(InternalOptions options);

  public abstract boolean isValidVirtualTargetForDynamicDispatch();

  public Set<DexEncodedMethod> lookupVirtualDispatchTargets(
      boolean isInterface, AppInfoWithSubtyping appInfo) {
    return isInterface ? lookupInterfaceTargets(appInfo) : lookupVirtualTargets(appInfo);
  }

  // TODO(b/140204899): Leverage refined receiver type if available.
  public Set<DexEncodedMethod> lookupVirtualTargets(AppInfoWithSubtyping appInfo) {
    assert isValidVirtualTarget(appInfo.app().options);
    // First add the target for receiver type method.type.
    DexEncodedMethod encodedMethod = getSingleTarget();
    Set<DexEncodedMethod> result = SetUtils.newIdentityHashSet(encodedMethod);
    // Add all matching targets from the subclass hierarchy.
    DexMethod method = encodedMethod.method;
    // TODO(b/140204899): Instead of subtypes of holder, we could iterate subtypes of refined
    //   receiver type if available.
    for (DexType type : appInfo.subtypes(method.holder)) {
      DexClass clazz = appInfo.definitionFor(type);
      if (!clazz.isInterface()) {
        ResolutionResult methods = appInfo.resolveMethodOnClass(clazz, method);
        DexEncodedMethod target = methods.getSingleTarget();
        if (target != null && target.isVirtualMethod()) {
          result.add(target);
        }
      }
    }
    return result;
  }

  // TODO(b/140204899): Leverage refined receiver type if available.
  public Set<DexEncodedMethod> lookupInterfaceTargets(AppInfoWithSubtyping appInfo) {
    assert isValidVirtualTarget(appInfo.app().options);
    Set<DexEncodedMethod> result = Sets.newIdentityHashSet();
    if (hasSingleTarget()) {
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
      DexEncodedMethod singleTarget = getSingleTarget();
      if (singleTarget.hasCode()) {
        DexProgramClass holder =
            asProgramClassOrNull(appInfo.definitionFor(singleTarget.method.holder));
        if (appInfo.hasAnyInstantiatedLambdas(holder)) {
          result.add(singleTarget);
        }
      }
    }

    DexEncodedMethod encodedMethod = getSingleTarget();
    DexMethod method = encodedMethod.method;
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
        if (targetMethods.hasSingleTarget()) {
          addIfNotAbstractAndBridge.accept(targetMethods.getSingleTarget());
        }
      } else {
        ResolutionResult targetMethods = appInfo.resolveMethodOnClass(clazz, method);
        if (targetMethods.hasSingleTarget()) {
          addIfNotAbstract.accept(targetMethods.getSingleTarget());
        }
      }
    }
    return result;
  }

  public static class SingleResolutionResult extends ResolutionResult {
    final DexEncodedMethod resolutionTarget;

    public static boolean isValidVirtualTarget(InternalOptions options, DexEncodedMethod target) {
      return options.canUseNestBasedAccess()
          ? (!target.accessFlags.isStatic() && !target.accessFlags.isConstructor())
          : target.isVirtualMethod();
    }

    public SingleResolutionResult(DexEncodedMethod resolutionTarget) {
      assert resolutionTarget != null;
      this.resolutionTarget = resolutionTarget;
    }

    @Override
    public boolean isValidVirtualTarget(InternalOptions options) {
      return isValidVirtualTarget(options, resolutionTarget);
    }

    @Override
    public boolean isValidVirtualTargetForDynamicDispatch() {
      return resolutionTarget.isVirtualMethod();
    }

    @Override
    public DexEncodedMethod getSingleTarget() {
      return resolutionTarget;
    }

    @Override
    public boolean hasSingleTarget() {
      return true;
    }
  }

  public abstract static class EmptyResult extends ResolutionResult {

    @Override
    public DexEncodedMethod getSingleTarget() {
      return null;
    }

    @Override
    public boolean hasSingleTarget() {
      return false;
    }

    @Override
    public Set<DexEncodedMethod> lookupVirtualTargets(AppInfoWithSubtyping appInfo) {
      return null;
    }

    @Override
    public Set<DexEncodedMethod> lookupInterfaceTargets(AppInfoWithSubtyping appInfo) {
      return null;
    }
  }

  public static class ArrayCloneMethodResult extends EmptyResult {

    static final ArrayCloneMethodResult INSTANCE = new ArrayCloneMethodResult();

    private ArrayCloneMethodResult() {
      // Intentionally left empty.
    }

    @Override
    public boolean isValidVirtualTarget(InternalOptions options) {
      return true;
    }

    @Override
    public boolean isValidVirtualTargetForDynamicDispatch() {
      return true;
    }
  }

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
    public boolean isValidVirtualTarget(InternalOptions options) {
      return false;
    }

    @Override
    public boolean isValidVirtualTargetForDynamicDispatch() {
      return false;
    }
  }

  public static class ClassNotFoundResult extends FailedResolutionResult {
    static final ClassNotFoundResult INSTANCE = new ClassNotFoundResult();

    private ClassNotFoundResult() {
      // Intentionally left empty.
    }
  }

  public static class IncompatibleClassResult extends FailedResolutionResult {
    static final IncompatibleClassResult INSTANCE =
        new IncompatibleClassResult(Collections.emptyList());

    private final Collection<DexEncodedMethod> methodsCausingError;

    private IncompatibleClassResult(Collection<DexEncodedMethod> methodsCausingError) {
      this.methodsCausingError = methodsCausingError;
    }

    static IncompatibleClassResult create(Collection<DexEncodedMethod> methodsCausingError) {
      return methodsCausingError.isEmpty()
          ? INSTANCE
          : new IncompatibleClassResult(methodsCausingError);
    }

    @Override
    public void forEachFailureDependency(Consumer<DexEncodedMethod> methodCausingFailureConsumer) {
      this.methodsCausingError.forEach(methodCausingFailureConsumer);
    }
  }

  public static class NoSuchMethodResult extends FailedResolutionResult {
    static final NoSuchMethodResult INSTANCE = new NoSuchMethodResult();

    private NoSuchMethodResult() {
      // Intentionally left empty.
    }
  }
}
