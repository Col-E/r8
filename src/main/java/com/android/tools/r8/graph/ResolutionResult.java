// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public interface ResolutionResult {

  DexEncodedMethod asResultOfResolve();

  DexEncodedMethod asSingleTarget();

  boolean hasSingleTarget();

  List<DexEncodedMethod> asListOfTargets();

  void forEachTarget(Consumer<DexEncodedMethod> consumer);

  default Set<DexEncodedMethod> lookupVirtualTargets(AppInfoWithSubtyping appInfo) {
    // TODO(b/140016938): Don't allow this lookup on non-virtual resolutions.
    // First add the target for receiver type method.type.
    Set<DexEncodedMethod> result = Sets.newIdentityHashSet();
    forEachTarget(result::add);
    // Add all matching targets from the subclass hierarchy.
    DexEncodedMethod encodedMethod = asResultOfResolve();
    DexMethod method = encodedMethod.method;
    for (DexType type : appInfo.subtypes(method.holder)) {
      DexClass clazz = appInfo.definitionFor(type);
      if (!clazz.isInterface()) {
        ResolutionResult methods = appInfo.resolveMethodOnClass(type, method);
        methods.forEachTarget(
            target -> {
              if (target.isVirtualMethod()) {
                result.add(target);
              }
            });
      }
    }
    return result;
  }

  default Set<DexEncodedMethod> lookupInterfaceTargets(AppInfoWithSubtyping appInfo) {
    // TODO(b/140016938): Don't allow this lookup on non-virtual resolutions.
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
      DexEncodedMethod singleTarget = asSingleTarget();
      if (singleTarget.getCode() != null
          && appInfo.hasAnyInstantiatedLambdas(singleTarget.method.holder)) {
        result.add(singleTarget);
      }
    }

    DexEncodedMethod encodedMethod = asResultOfResolve();
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

    Set<DexType> set = appInfo.subtypes(method.holder);
    for (DexType type : set) {
      DexClass clazz = appInfo.definitionFor(type);
      if (clazz.isInterface()) {
        ResolutionResult targetMethods = appInfo.resolveMethodOnInterface(type, method);
        targetMethods.forEachTarget(addIfNotAbstractAndBridge);
      } else {
        ResolutionResult targetMethods = appInfo.resolveMethodOnClass(type, method);
        targetMethods.forEachTarget(addIfNotAbstract);
      }
    }
    return result;
  }

  class MultiResult implements ResolutionResult {

    private final ImmutableList<DexEncodedMethod> methods;

    MultiResult(ImmutableList<DexEncodedMethod> results) {
      assert results.size() > 1;
      this.methods = results;
    }

    @Override
    public DexEncodedMethod asResultOfResolve() {
      // Resolution may return any of the targets that were found.
      return methods.get(0);
    }

    @Override
    public DexEncodedMethod asSingleTarget() {
      // There is no single target that is guaranteed to be called.
      return null;
    }

    @Override
    public boolean hasSingleTarget() {
      return false;
    }

    @Override
    public List<DexEncodedMethod> asListOfTargets() {
      return methods;
    }

    @Override
    public void forEachTarget(Consumer<DexEncodedMethod> consumer) {
      methods.forEach(consumer);
    }
  }

  class EmptyResult implements ResolutionResult {

    private static final EmptyResult SINGLETON = new EmptyResult();

    private EmptyResult() {
      // Intentionally left empty.
    }

    static EmptyResult get() {
      return SINGLETON;
    }

    @Override
    public DexEncodedMethod asResultOfResolve() {
      return null;
    }

    @Override
    public DexEncodedMethod asSingleTarget() {
      return null;
    }

    @Override
    public boolean hasSingleTarget() {
      return false;
    }

    @Override
    public List<DexEncodedMethod> asListOfTargets() {
      return Collections.emptyList();
    }

    @Override
    public void forEachTarget(Consumer<DexEncodedMethod> consumer) {
      // Intentionally left empty.
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
}
