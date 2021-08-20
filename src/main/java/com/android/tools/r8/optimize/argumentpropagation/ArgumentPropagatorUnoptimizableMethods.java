// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.UnknownMethodState;
import com.android.tools.r8.optimize.argumentpropagation.utils.DepthFirstTopDownClassHierarchyTraversal;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class ArgumentPropagatorUnoptimizableMethods {

  private static final MethodSignatureEquivalence equivalence = MethodSignatureEquivalence.get();

  private final AppView<AppInfoWithLiveness> appView;
  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;
  private final MethodStateCollectionByReference methodStates;

  public ArgumentPropagatorUnoptimizableMethods(
      AppView<AppInfoWithLiveness> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      MethodStateCollectionByReference methodStates) {
    this.appView = appView;
    this.immediateSubtypingInfo = immediateSubtypingInfo;
    this.methodStates = methodStates;
  }

  // TODO(b/190154391): Consider if we should bail out for classes that inherit from a missing
  //  class.
  public void disableArgumentPropagationForUnoptimizableMethods(
      Collection<DexProgramClass> stronglyConnectedComponent) {
    ProgramMethodSet unoptimizableClassRootMethods = ProgramMethodSet.create();
    ProgramMethodSet unoptimizableInterfaceRootMethods = ProgramMethodSet.create();
    forEachUnoptimizableMethod(
        stronglyConnectedComponent,
        method -> {
          if (method.getDefinition().belongsToVirtualPool()
              && !method.getHolder().isFinal()
              && !method.getAccessFlags().isFinal()) {
            if (method.getHolder().isInterface()) {
              unoptimizableInterfaceRootMethods.add(method);
            } else {
              unoptimizableClassRootMethods.add(method);
            }
          } else {
            disableArgumentPropagationForMethod(method);
          }
        });

    // Disable argument propagation for all overrides of the root methods. Since interface methods
    // may be implemented by classes that are not a subtype of the interface that declares the
    // interface method, we first mark the interface method overrides on such classes as ineligible
    // for argument propagation.
    if (!unoptimizableInterfaceRootMethods.isEmpty()) {
      new UnoptimizableInterfaceMethodPropagator(
              unoptimizableClassRootMethods, unoptimizableInterfaceRootMethods)
          .run(stronglyConnectedComponent);
    }

    // At this point we can mark all overrides by a simple top-down traversal over the class
    // hierarchy.
    new UnoptimizableClassMethodPropagator(
            unoptimizableClassRootMethods, unoptimizableInterfaceRootMethods)
        .run(stronglyConnectedComponent);
  }

  private void disableArgumentPropagationForMethod(ProgramMethod method) {
    methodStates.set(method, UnknownMethodState.get());
  }

  private void forEachUnoptimizableMethod(
      Collection<DexProgramClass> stronglyConnectedComponent, Consumer<ProgramMethod> consumer) {
    AppInfoWithLiveness appInfo = appView.appInfo();
    InternalOptions options = appView.options();
    for (DexProgramClass clazz : stronglyConnectedComponent) {
      clazz.forEachProgramMethod(
          method -> {
            assert !method.getDefinition().isLibraryMethodOverride().isUnknown();
            if (method.getDefinition().isLibraryMethodOverride().isPossiblyTrue()
                || appInfo.isMethodTargetedByInvokeDynamic(method)
                || !appInfo
                    .getKeepInfo()
                    .getMethodInfo(method)
                    .isArgumentPropagationAllowed(options)) {
              consumer.accept(method);
            }
          });
    }
  }

  private class UnoptimizableInterfaceMethodPropagator
      extends DepthFirstTopDownClassHierarchyTraversal {

    private final ProgramMethodSet unoptimizableClassRootMethods;
    private final Map<DexProgramClass, Set<Wrapper<DexMethod>>> unoptimizableInterfaceMethods =
        new IdentityHashMap<>();

    UnoptimizableInterfaceMethodPropagator(
        ProgramMethodSet unoptimizableClassRootMethods,
        ProgramMethodSet unoptimizableInterfaceRootMethods) {
      super(
          ArgumentPropagatorUnoptimizableMethods.this.appView,
          ArgumentPropagatorUnoptimizableMethods.this.immediateSubtypingInfo);
      this.unoptimizableClassRootMethods = unoptimizableClassRootMethods;
      unoptimizableInterfaceRootMethods.forEach(this::addUnoptimizableRootMethod);
    }

    private void addUnoptimizableRootMethod(ProgramMethod method) {
      unoptimizableInterfaceMethods
          .computeIfAbsent(method.getHolder(), ignoreKey(Sets::newIdentityHashSet))
          .add(equivalence.wrap(method.getReference()));
    }

    @Override
    public void visit(DexProgramClass clazz) {
      Set<Wrapper<DexMethod>> unoptimizableInterfaceMethodsForClass =
          unoptimizableInterfaceMethods.computeIfAbsent(clazz, ignoreKey(Sets::newIdentityHashSet));

      // Add the unoptimizable interface methods from the parent interfaces.
      immediateSubtypingInfo.forEachImmediateProgramSuperClass(
          clazz,
          superclass ->
              unoptimizableInterfaceMethodsForClass.addAll(
                  unoptimizableInterfaceMethods.get(superclass)));

      // Propagate the unoptimizable interface methods of this interface to all immediate
      // (non-interface) subclasses.
      for (DexProgramClass implementer : immediateSubtypingInfo.getSubclasses(clazz)) {
        if (implementer.isInterface()) {
          continue;
        }

        for (Wrapper<DexMethod> unoptimizableInterfaceMethod :
            unoptimizableInterfaceMethodsForClass) {
          SingleResolutionResult resolutionResult =
              appView
                  .appInfo()
                  .resolveMethodOnClass(unoptimizableInterfaceMethod.get(), implementer)
                  .asSingleResolution();
          if (resolutionResult == null || !resolutionResult.getResolvedHolder().isProgramClass()) {
            continue;
          }

          ProgramMethod resolvedMethod = resolutionResult.getResolvedProgramMethod();
          if (resolvedMethod.getHolder().isInterface()
              || resolvedMethod.getHolder() == implementer) {
            continue;
          }

          unoptimizableClassRootMethods.add(resolvedMethod);
        }
      }
    }

    @Override
    public void prune(DexProgramClass clazz) {
      unoptimizableInterfaceMethods.remove(clazz);
    }

    @Override
    public boolean isRoot(DexProgramClass clazz) {
      return clazz.isInterface() && super.isRoot(clazz);
    }

    @Override
    public void forEachSubClass(DexProgramClass clazz, Consumer<DexProgramClass> consumer) {
      for (DexProgramClass subclass : immediateSubtypingInfo.getSubclasses(clazz)) {
        if (subclass.isInterface()) {
          consumer.accept(subclass);
        }
      }
    }
  }

  private class UnoptimizableClassMethodPropagator
      extends DepthFirstTopDownClassHierarchyTraversal {

    private final Map<DexProgramClass, DexMethodSignatureSet> unoptimizableMethods =
        new IdentityHashMap<>();

    UnoptimizableClassMethodPropagator(
        ProgramMethodSet unoptimizableClassRootMethods,
        ProgramMethodSet unoptimizableInterfaceRootMethods) {
      super(
          ArgumentPropagatorUnoptimizableMethods.this.appView,
          ArgumentPropagatorUnoptimizableMethods.this.immediateSubtypingInfo);
      unoptimizableClassRootMethods.forEach(this::addUnoptimizableRootMethod);
      unoptimizableInterfaceRootMethods.forEach(this::addUnoptimizableRootMethod);
    }

    private void addUnoptimizableRootMethod(ProgramMethod method) {
      unoptimizableMethods
          .computeIfAbsent(method.getHolder(), ignoreKey(DexMethodSignatureSet::create))
          .add(method);
    }

    @Override
    public void visit(DexProgramClass clazz) {
      DexMethodSignatureSet unoptimizableMethodsForClass =
          unoptimizableMethods.computeIfAbsent(clazz, ignoreKey(DexMethodSignatureSet::create));

      // Add the unoptimizable methods from the parent classes.
      immediateSubtypingInfo.forEachImmediateProgramSuperClass(
          clazz,
          superclass -> unoptimizableMethodsForClass.addAll(unoptimizableMethods.get(superclass)));

      // Disable argument propagation for the unoptimizable methods of this class.
      clazz.forEachProgramVirtualMethod(
          method -> {
            if (unoptimizableMethodsForClass.contains(method)) {
              disableArgumentPropagationForMethod(method);
            }
          });
    }

    @Override
    public void prune(DexProgramClass clazz) {
      unoptimizableMethods.remove(clazz);
    }
  }
}
