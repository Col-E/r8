// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.propagation;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.DynamicTypeWithUpperBound;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePolymorphicMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionBySignature;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.StateCloner;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.UnknownMethodState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class VirtualDispatchMethodArgumentPropagator extends MethodArgumentPropagator {

  class PropagationState {

    // Argument information for virtual methods that must be propagated to all overrides (i.e., this
    // information does not have a lower bound).
    final MethodStateCollectionBySignature active = MethodStateCollectionBySignature.create();

    // Argument information for virtual methods that must be propagated to all overrides that are
    // above the given lower bound.
    final Map<DexType, MethodStateCollectionBySignature> activeUntilLowerBound =
        new IdentityHashMap<>();

    // Argument information for virtual methods that is currently inactive, but should be propagated
    // to all overrides below a given upper bound.
    final Map<DynamicTypeWithUpperBound, MethodStateCollectionBySignature> inactiveUntilUpperBound =
        new HashMap<>();

    PropagationState(DexProgramClass clazz) {
      // Join the argument information from each of the super types.
      immediateSubtypingInfo.forEachImmediateProgramSuperClass(
          clazz, superclass -> addParentState(clazz, superclass));
    }

    @SuppressWarnings("ReferenceEquality")
    // TODO(b/190154391): This currently copies the state of the superclass into its immediate
    //  given subclass. Instead of copying the state, consider linking the states. This would reduce
    //  memory usage, but would require visiting all transitive (program) super classes for each
    //  subclass.
    private void addParentState(DexProgramClass clazz, DexProgramClass superclass) {
      PropagationState parentState = propagationStates.get(superclass.asProgramClass());
      assert parentState != null;

      // Add the argument information that must be propagated to all method overrides.
      active.addMethodStates(appView, parentState.active);

      // Add the argument information that is active until a given lower bound.
      parentState.activeUntilLowerBound.forEach(
          (lowerBound, activeMethodState) -> {
            if (lowerBound != superclass.getType()) {
              // TODO(b/190154391): Verify that the lower bound is a subtype of the current.
              //  Otherwise we carry this information to all subtypes although there is no need to.
              activeUntilLowerBound
                  .computeIfAbsent(lowerBound, ignoreKey(MethodStateCollectionBySignature::create))
                  .addMethodStates(appView, activeMethodState);
            } else {
              // No longer active.
            }
          });

      // Add the argument information that is inactive until a given upper bound.
      parentState.inactiveUntilUpperBound.forEach(
          (bounds, inactiveMethodStates) -> {
            ClassTypeElement upperBound = bounds.getDynamicUpperBoundType().asClassType();
            if (shouldActivateMethodStateGuardedByBounds(upperBound, clazz, superclass)) {
              // The upper bound is the current class, thus this inactive information now becomes
              // active.
              if (bounds.hasDynamicLowerBoundType()) {
                activeUntilLowerBound
                    .computeIfAbsent(
                        bounds.getDynamicLowerBoundType().getClassType(),
                        ignoreKey(MethodStateCollectionBySignature::create))
                    .addMethodStates(appView, inactiveMethodStates);
              } else {
                active.addMethodStates(appView, inactiveMethodStates);
              }

              inactiveMethodStates.forEach(
                  (signature, methodState) -> {
                    SingleResolutionResult<?> resolutionResult =
                        appView
                            .appInfo()
                            .resolveMethodOnLegacy(clazz, signature)
                            .asSingleResolution();

                    // Find the first virtual method in the super class hierarchy.
                    while (resolutionResult != null
                        && resolutionResult.getResolvedMethod().belongsToDirectPool()) {
                      resolutionResult =
                          appView
                              .appInfo()
                              .resolveMethodOnClassLegacy(
                                  resolutionResult.getResolvedHolder().getSuperType(), signature)
                              .asSingleResolution();
                    }

                    // Propagate the argument information to the method on the super class.
                    if (resolutionResult != null
                        && resolutionResult.getResolvedHolder().isProgramClass()
                        && resolutionResult.getResolvedHolder() != clazz
                        && resolutionResult.getResolvedMethod().hasCode()) {
                      DexProgramClass resolvedHolder =
                          resolutionResult.getResolvedHolder().asProgramClass();
                      propagationStates
                          .get(resolvedHolder)
                          .activeUntilLowerBound
                          .computeIfAbsent(
                              resolvedHolder.getType(),
                              ignoreKey(MethodStateCollectionBySignature::create))
                          .addMethodState(
                              appView, resolutionResult.getResolvedProgramMethod(), methodState);
                    }
                  });
            } else {
              // Still inactive.
              // TODO(b/190154391): Only carry this information downwards if the upper bound is a
              //  subtype of this class. Otherwise we carry this information to all subtypes,
              //  although clearly the information will never become active.
              inactiveUntilUpperBound
                  .computeIfAbsent(bounds, ignoreKey(MethodStateCollectionBySignature::create))
                  .addMethodStates(appView, inactiveMethodStates);
            }
          });
    }

    private MethodState computeMethodStateForPolymorhicMethod(ProgramMethod method) {
      assert method.getDefinition().isNonPrivateVirtualMethod();
      MethodState methodState = active.get(method).mutableCopy();
      if (!activeUntilLowerBound.isEmpty()) {
        DexMethodSignature methodSignature = method.getMethodSignature();
        for (MethodStateCollectionBySignature methodStates : activeUntilLowerBound.values()) {
          methodState =
              methodState.mutableJoin(
                  appView, methodSignature, methodStates.get(method), StateCloner.getCloner());
        }
      }
      return methodState;
    }

    @SuppressWarnings("ReferenceEquality")
    private boolean shouldActivateMethodStateGuardedByBounds(
        ClassTypeElement upperBound, DexProgramClass currentClass, DexProgramClass superClass) {
      ClassTypeElement classType =
          TypeElement.fromDexType(currentClass.getType(), maybeNull(), appView).asClassType();
      // When propagating argument information for interface methods downwards from an interface to
      // a non-interface we need to account for the parent classes of the current class.
      if (superClass.isInterface()
          && !currentClass.isInterface()
          && currentClass.getSuperType() != appView.dexItemFactory().objectType) {
        return classType.lessThanOrEqualUpToNullability(upperBound, appView);
      }
      // If the upper bound does not have any interfaces we simply activate the method state when
      // meeting the upper bound class type in the downwards traversal over the class hierarchy.
      if (classType.getInterfaces().isEmpty()) {
        return classType.equalUpToNullability(upperBound);
      }
      // If the upper bound has interfaces, we check if the current class is a subtype of *both* the
      // upper bound class type and the upper bound interface types.
      return classType.lessThanOrEqualUpToNullability(upperBound, appView);
    }
  }

  // For each class, stores the argument information for each virtual method on this class and all
  // direct and indirect super classes.
  //
  // This data structure is populated during a top-down traversal over the class hierarchy, such
  // that entries in the map can be removed when the top-down traversal has visited all subtypes of
  // a given node.
  final Map<DexProgramClass, PropagationState> propagationStates = new IdentityHashMap<>();

  public VirtualDispatchMethodArgumentPropagator(
      AppView<AppInfoWithLiveness> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      MethodStateCollectionByReference methodStates) {
    super(appView, immediateSubtypingInfo, methodStates);
  }

  @Override
  public void run(Collection<DexProgramClass> stronglyConnectedComponent) {
    super.run(stronglyConnectedComponent);
    assert verifyAllClassesFinished(stronglyConnectedComponent);
    assert verifyStatePruned();
  }

  @Override
  public void visit(DexProgramClass clazz) {
    assert !propagationStates.containsKey(clazz);
    computePropagationState(clazz);
  }

  private void computePropagationState(DexProgramClass clazz) {
    PropagationState propagationState = new PropagationState(clazz);

    // Join the argument information from the methods of the current class.
    clazz.forEachProgramVirtualMethod(
        method -> {
          MethodState methodState = methodStates.get(method);
          if (methodState.isBottom()) {
            return;
          }

          // TODO(b/190154391): Add an unknown polymorphic method state, such that we can
          //  distinguish monomorphic unknown method states from polymorphic unknown method states.
          //  We only need to propagate polymorphic unknown method states here.
          if (methodState.isUnknown()) {
            propagationState.active.set(method, UnknownMethodState.get());
            return;
          }

          ConcreteMethodState concreteMethodState = methodState.asConcrete();
          if (concreteMethodState.isMonomorphic()) {
            // No need to propagate information for methods that do not override other methods and
            // are not themselves overridden.
            return;
          }

          ConcretePolymorphicMethodState polymorphicMethodState =
              concreteMethodState.asPolymorphic();
          polymorphicMethodState.forEach(
              (bounds, methodStateForBounds) -> {
                if (bounds.isUnknown()) {
                  propagationState.active.addMethodState(appView, method, methodStateForBounds);
                } else {
                  // TODO(b/190154391): Verify that the bounds are not trivial according to the
                  //  static receiver type.
                  ClassTypeElement upperBound = bounds.getDynamicUpperBoundType().asClassType();
                  if (isUpperBoundSatisfied(upperBound, clazz)) {
                    if (bounds.hasDynamicLowerBoundType()) {
                      // TODO(b/190154391): Verify that the lower bound is a subtype of the current
                      //  class.
                      propagationState
                          .activeUntilLowerBound
                          .computeIfAbsent(
                              bounds.getDynamicLowerBoundType().getClassType(),
                              ignoreKey(MethodStateCollectionBySignature::create))
                          .addMethodState(appView, method, methodStateForBounds);
                    } else {
                      propagationState.active.addMethodState(appView, method, methodStateForBounds);
                    }
                  } else {
                    assert !clazz
                        .getType()
                        .toTypeElement(appView)
                        .lessThanOrEqualUpToNullability(upperBound, appView);
                    propagationState
                        .inactiveUntilUpperBound
                        .computeIfAbsent(
                            bounds, ignoreKey(MethodStateCollectionBySignature::create))
                        .addMethodState(appView, method, methodStateForBounds);
                  }
                }
              });
        });

    propagationStates.put(clazz, propagationState);
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean isUpperBoundSatisfied(ClassTypeElement upperBound, DexProgramClass currentClass) {
    DexType upperBoundType =
        upperBound.getClassType() == appView.dexItemFactory().objectType
                && upperBound.getInterfaces().hasSingleKnownInterface()
            ? upperBound.getInterfaces().getSingleKnownInterface()
            : upperBound.getClassType();
    DexProgramClass upperBoundClass = asProgramClassOrNull(appView.definitionFor(upperBoundType));
    if (upperBoundClass == null) {
      // We should generally never have a dynamic receiver upper bound for a program method which is
      // not a program class. However, since the program may not type change or there could be
      // missing classes, we still need to cover this case. In the rare cases where this happens, we
      // conservatively consider the upper bound to be satisfied.
      return true;
    }
    return upperBoundClass == currentClass;
  }

  private void computeFinalMethodStates(DexProgramClass clazz, PropagationState propagationState) {
    clazz.forEachProgramVirtualMethod(method -> computeFinalMethodState(method, propagationState));
  }

  private void computeFinalMethodState(ProgramMethod method, PropagationState propagationState) {
    if (!method.getDefinition().hasCode()) {
      methodStates.remove(method);
      return;
    }

    MethodState methodState = methodStates.get(method);
    if (methodState.isMonomorphic() || methodState.isUnknown()) {
      return;
    }

    assert methodState.isBottom() || methodState.isPolymorphic();

    // This is a polymorphic method and we need to compute the method state to account for dynamic
    // dispatch.
    methodState = propagationState.computeMethodStateForPolymorhicMethod(method);
    assert !methodState.isConcrete() || methodState.asConcrete().isMonomorphic();
    methodStates.set(method, methodState);
  }

  @Override
  public void prune(DexProgramClass clazz) {
    PropagationState propagationState = propagationStates.remove(clazz);
    computeFinalMethodStates(clazz, propagationState);
  }

  private boolean verifyAllClassesFinished(Collection<DexProgramClass> stronglyConnectedComponent) {
    for (DexProgramClass clazz : stronglyConnectedComponent) {
      assert isClassFinished(clazz);
    }
    return true;
  }

  private boolean verifyStatePruned() {
    assert propagationStates.isEmpty();
    return true;
  }
}
