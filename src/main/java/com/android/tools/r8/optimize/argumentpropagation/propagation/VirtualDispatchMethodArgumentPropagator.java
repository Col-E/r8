// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.propagation;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.optimize.info.ConcreteCallSiteOptimizationInfo;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePolymorphicMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class VirtualDispatchMethodArgumentPropagator extends MethodArgumentPropagator {

  class PropagationState {

    // Argument information for virtual methods that must be propagated to all overrides (i.e., this
    // information does not have a lower bound).
    final MethodStateCollection active = MethodStateCollection.create();

    // Argument information for virtual methods that must be propagated to all overrides that are
    // above the given lower bound.
    final Map<DexType, MethodStateCollection> activeUntilLowerBound = new IdentityHashMap<>();

    // Argument information for virtual methods that is currently inactive, but should be propagated
    // to all overrides below a given upper bound.
    final Map<DynamicType, MethodStateCollection> inactiveUntilUpperBound = new HashMap<>();

    PropagationState(DexProgramClass clazz) {
      // Join the argument information from each of the super types.
      immediateSubtypingInfo.forEachImmediateSuperClassMatching(
          clazz,
          (supertype, superclass) -> superclass != null && superclass.isProgramClass(),
          (supertype, superclass) -> addParentState(clazz, superclass.asProgramClass()));
    }

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
                  .computeIfAbsent(lowerBound, ignoreKey(MethodStateCollection::create))
                  .addMethodStates(appView, activeMethodState);
            } else {
              // No longer active.
            }
          });

      // Add the argument information that is inactive until a given upper bound.
      parentState.inactiveUntilUpperBound.forEach(
          (bounds, inactiveMethodState) -> {
            ClassTypeElement upperBound = bounds.getDynamicUpperBoundType().asClassType();
            if (upperBound.getClassType() == clazz.getType()) {
              // The upper bound is the current class, thus this inactive information now becomes
              // active.
              if (bounds.hasDynamicLowerBoundType()) {
                activeUntilLowerBound
                    .computeIfAbsent(
                        bounds.getDynamicLowerBoundType().getClassType(),
                        ignoreKey(MethodStateCollection::create))
                    .addMethodStates(appView, inactiveMethodState);
              } else {
                active.addMethodStates(appView, inactiveMethodState);
              }
            } else {
              // Still inactive.
              // TODO(b/190154391): Only carry this information downwards if the upper bound is a
              //  subtype of this class. Otherwise we carry this information to all subtypes,
              //  although clearly the information will never become active.
              inactiveUntilUpperBound
                  .computeIfAbsent(bounds, ignoreKey(MethodStateCollection::create))
                  .addMethodStates(appView, inactiveMethodState);
            }
          });
    }

    private MethodState computeMethodStateForPolymorhicMethod(ProgramMethod method) {
      assert method.getDefinition().isNonPrivateVirtualMethod();
      MethodState methodState = active.get(method);
      for (MethodStateCollection methodStates : activeUntilLowerBound.values()) {
        methodState = methodState.mutableJoin(appView, methodStates.get(method));
      }
      return methodState;
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
      MethodStateCollection methodStates) {
    super(appView, immediateSubtypingInfo, methodStates);
  }

  @Override
  public void run(Set<DexProgramClass> stronglyConnectedComponent) {
    super.run(stronglyConnectedComponent);
    assert verifyAllClassesFinished(stronglyConnectedComponent);
    assert verifyStatePruned(stronglyConnectedComponent);
  }

  @Override
  public void forEachSubClass(DexProgramClass clazz, Consumer<DexProgramClass> consumer) {
    immediateSubtypingInfo.getSubclasses(clazz).forEach(consumer);
  }

  @Override
  public boolean isRoot(DexProgramClass clazz) {
    DexProgramClass superclass = asProgramClassOrNull(appView.definitionFor(clazz.getSuperType()));
    if (superclass != null) {
      return false;
    }
    for (DexType implementedType : clazz.getInterfaces()) {
      DexProgramClass implementedClass =
          asProgramClassOrNull(appView.definitionFor(implementedType));
      if (implementedClass != null) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void visit(DexProgramClass clazz) {
    assert !propagationStates.containsKey(clazz);
    PropagationState propagationState = computePropagationState(clazz);
    setOptimizationInfo(clazz, propagationState);
  }

  private PropagationState computePropagationState(DexProgramClass clazz) {
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
            propagationState.active.addMethodState(appView, method.getReference(), methodState);
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
                  propagationState.active.addMethodState(
                      appView, method.getReference(), methodStateForBounds);
                } else {
                  // TODO(b/190154391): Verify that the bounds are not trivial according to the
                  //  static receiver type.
                  ClassTypeElement upperBound = bounds.getDynamicUpperBoundType().asClassType();
                  if (upperBound.getClassType() == clazz.getType()) {
                    if (bounds.hasDynamicLowerBoundType()) {
                      // TODO(b/190154391): Verify that the lower bound is a subtype of the current
                      //  class.
                      propagationState
                          .activeUntilLowerBound
                          .computeIfAbsent(
                              bounds.getDynamicLowerBoundType().getClassType(),
                              ignoreKey(MethodStateCollection::create))
                          .addMethodState(appView, method.getReference(), methodStateForBounds);
                    } else {
                      propagationState.active.addMethodState(
                          appView, method.getReference(), methodStateForBounds);
                    }
                  } else {
                    assert !appView.appInfo().isSubtype(clazz.getType(), upperBound.getClassType());
                    propagationState
                        .inactiveUntilUpperBound
                        .computeIfAbsent(bounds, ignoreKey(MethodStateCollection::create))
                        .addMethodState(appView, method.getReference(), methodStateForBounds);
                  }
                }
              });
        });

    propagationStates.put(clazz, propagationState);
    return propagationState;
  }

  private void setOptimizationInfo(DexProgramClass clazz, PropagationState propagationState) {
    clazz.forEachProgramMethod(method -> setOptimizationInfo(method, propagationState));
  }

  private void setOptimizationInfo(ProgramMethod method, PropagationState propagationState) {
    MethodState methodState = methodStates.remove(method);

    // If this is a polymorphic method, we need to compute the method state to account for dynamic
    // dispatch.
    if (methodState.isConcrete() && methodState.asConcrete().isPolymorphic()) {
      methodState = propagationState.computeMethodStateForPolymorhicMethod(method);
    }

    if (methodState.isBottom()) {
      // TODO(b/190154391): This should only happen if the method is never called. Consider removing
      //  the method in this case.
      return;
    }

    if (methodState.isUnknown()) {
      // Nothing is known about the arguments to this method.
      return;
    }

    ConcreteMethodState concreteMethodState = methodState.asConcrete();
    if (concreteMethodState.isPolymorphic()) {
      assert false;
      return;
    }

    // TODO(b/190154391): We need to resolve the flow constraints before this is guaranteed to be
    //  sound.
    method
        .getDefinition()
        .joinCallSiteOptimizationInfo(
            ConcreteCallSiteOptimizationInfo.fromMethodState(
                appView, method, concreteMethodState.asMonomorphic()),
            appView);
  }

  @Override
  public void prune(DexProgramClass clazz) {
    propagationStates.remove(clazz);
  }

  private boolean verifyAllClassesFinished(Set<DexProgramClass> stronglyConnectedComponent) {
    for (DexProgramClass clazz : stronglyConnectedComponent) {
      assert isClassFinished(clazz);
    }
    return true;
  }

  private boolean verifyStatePruned(Set<DexProgramClass> stronglyConnectedComponent) {
    Set<DexType> types =
        stronglyConnectedComponent.stream().map(DexClass::getType).collect(Collectors.toSet());
    methodStates.forEach(
        (method, methodState) -> {
          assert !types.contains(method.getHolderType());
        });
    assert propagationStates.isEmpty();
    return true;
  }
}
