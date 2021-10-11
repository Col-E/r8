// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.classhierarchy;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.argumentpropagation.utils.DepthFirstTopDownClassHierarchyTraversal;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Given a predicate, finds all methods that satisfies their methods including their overrides and
 * siblings.
 */
public class MethodOverridesCollector {

  public static ProgramMethodSet findAllMethodsAndOverridesThatMatches(
      AppView<AppInfoWithLiveness> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      Collection<DexProgramClass> stronglyConnectedComponent,
      Predicate<ProgramMethod> predicate) {
    ProgramMethodSet classRootMethods = ProgramMethodSet.create();
    ProgramMethodSet interfaceRootMethods = ProgramMethodSet.create();

    for (DexProgramClass clazz : stronglyConnectedComponent) {
      clazz.forEachProgramMethod(
          method -> {
            if (predicate.test(method)) {
              if (clazz.isInterface()) {
                interfaceRootMethods.add(method);
              } else {
                classRootMethods.add(method);
              }
            }
          });
    }

    // Since interface methods may be implemented by classes that are not a subtype of the interface
    // that declares the interface method, we first add the interface method overrides on such
    // classes to the classRootMethods set.
    if (!interfaceRootMethods.isEmpty()) {
      new InterfaceMethodToClassSiblingPropagator(
              appView, immediateSubtypingInfo, classRootMethods, interfaceRootMethods)
          .run(stronglyConnectedComponent);
    }

    // Mark all overrides by a simple top-down traversal over the class hierarchy.
    TopDownClassHierarchyPropagator topDownClassHierarchyPropagator =
        new TopDownClassHierarchyPropagator(
            appView, immediateSubtypingInfo, classRootMethods, interfaceRootMethods);
    topDownClassHierarchyPropagator.run(stronglyConnectedComponent);
    return topDownClassHierarchyPropagator.getResult();
  }

  private static class InterfaceMethodToClassSiblingPropagator
      extends DepthFirstTopDownClassHierarchyTraversal {

    private final ProgramMethodSet classRootMethods;
    private final Map<DexProgramClass, DexMethodSignatureSet> interfaceMethodsOfInterest =
        new IdentityHashMap<>();

    InterfaceMethodToClassSiblingPropagator(
        AppView<AppInfoWithLiveness> appView,
        ImmediateProgramSubtypingInfo immediateSubtypingInfo,
        ProgramMethodSet classRootMethods,
        ProgramMethodSet interfaceRootMethods) {
      super(appView, immediateSubtypingInfo);
      this.classRootMethods = classRootMethods;
      for (ProgramMethod method : interfaceRootMethods) {
        interfaceMethodsOfInterest
            .computeIfAbsent(method.getHolder(), ignoreKey(DexMethodSignatureSet::create))
            .add(method);
      }
    }

    @Override
    public void visit(DexProgramClass clazz) {
      DexMethodSignatureSet interfaceMethodsOfInterestForClass =
          interfaceMethodsOfInterest.computeIfAbsent(
              clazz, ignoreKey(DexMethodSignatureSet::create));

      // Add the interface methods from the parent interfaces that satisfies the given predicate.
      immediateSubtypingInfo.forEachImmediateProgramSuperClass(
          clazz,
          superclass ->
              interfaceMethodsOfInterestForClass.addAll(
                  interfaceMethodsOfInterest.get(superclass)));

      // Propagate the interface methods of interest from this interface to all immediate
      // (non-interface) subclasses.
      for (DexProgramClass implementer : immediateSubtypingInfo.getSubclasses(clazz)) {
        if (implementer.isInterface()) {
          continue;
        }

        for (DexMethodSignature interfaceMethod : interfaceMethodsOfInterestForClass) {
          SingleResolutionResult resolutionResult =
              appView
                  .appInfo()
                  .resolveMethodOnClass(interfaceMethod, implementer)
                  .asSingleResolution();
          if (resolutionResult == null || !resolutionResult.getResolvedHolder().isProgramClass()) {
            continue;
          }

          ProgramMethod resolvedMethod = resolutionResult.getResolvedProgramMethod();
          if (resolvedMethod.getHolder().isInterface()
              || resolvedMethod.getHolder() == implementer) {
            continue;
          }

          classRootMethods.add(resolvedMethod);
        }
      }
    }

    @Override
    public void prune(DexProgramClass clazz) {
      interfaceMethodsOfInterest.remove(clazz);
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

  private static class TopDownClassHierarchyPropagator
      extends DepthFirstTopDownClassHierarchyTraversal {

    private final Map<DexProgramClass, DexMethodSignatureSet> methodsOfInterest =
        new IdentityHashMap<>();

    private final ProgramMethodSet result = ProgramMethodSet.create();

    TopDownClassHierarchyPropagator(
        AppView<AppInfoWithLiveness> appView,
        ImmediateProgramSubtypingInfo immediateSubtypingInfo,
        ProgramMethodSet classRootMethods,
        ProgramMethodSet interfaceRootMethods) {
      super(appView, immediateSubtypingInfo);
      classRootMethods.forEach(this::addRootMethod);
      interfaceRootMethods.forEach(this::addRootMethod);
    }

    private void addRootMethod(ProgramMethod method) {
      methodsOfInterest
          .computeIfAbsent(method.getHolder(), ignoreKey(DexMethodSignatureSet::create))
          .add(method);
    }

    ProgramMethodSet getResult() {
      return result;
    }

    @Override
    public void visit(DexProgramClass clazz) {
      DexMethodSignatureSet methodsOfInterestForClass =
          methodsOfInterest.computeIfAbsent(clazz, ignoreKey(DexMethodSignatureSet::create));

      // Add the methods of interest from the parent classes.
      immediateSubtypingInfo.forEachImmediateProgramSuperClass(
          clazz, superclass -> methodsOfInterestForClass.addAll(methodsOfInterest.get(superclass)));

      // For each method on the current class that is classified as a method of interest, add the
      // method to the result.
      clazz.forEachProgramMethod(
          method -> {
            if (methodsOfInterestForClass.contains(method)) {
              result.add(method);
            }
          });
    }

    @Override
    public void prune(DexProgramClass clazz) {
      methodsOfInterest.remove(clazz);
    }
  }
}
