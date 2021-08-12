// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.propagation;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Propagates the argument information for each interface method I.m() to the possible dispatch
 * targets that are declared on (non-interface) classes that are not a subtype of I.
 *
 * <p>Example: The argument information stored for I.m() is propagated to the method A.m().
 *
 * <pre>
 *   interface I { void m(int x); }
 *   class A { public void m(int x) { ... } }
 *   class B extends A implements I {}
 * </pre>
 */
public class InterfaceMethodArgumentPropagator {

  // The state of a given class in the top-down traversal.
  private enum TraversalState {
    // Represents that a given class and all of its direct and indirect supertypes have been
    // visited by the top-down traversal, but all of the direct and indirect subtypes are still
    // not visited.
    SEEN,
    // Represents that a given class and all of its direct and indirect subtypes have been visited.
    // Such nodes will never be seen again in the top-down traversal, and any state stored for
    // such nodes can be pruned.
    FINISHED
  }

  private final AppView<AppInfoWithLiveness> appView;
  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;
  private final MethodStateCollection methodStates;

  // Contains the argument information for each interface method (including inherited interface
  // methods) on the seen but not finished interfaces.
  private final Map<DexProgramClass, MethodStateCollection> methodStatesToPropagate =
      new IdentityHashMap<>();

  // Contains the traversal state for each interface. If a given interface is not in the map the
  // interface is not yet seen.
  private final Map<DexProgramClass, TraversalState> states = new IdentityHashMap<>();

  // The interface hierarchy is not a tree, thus for completeness we need to process all parent
  // interfaces for a given interface before continuing the top-down traversal from that
  // interface. When the top-down traversal for a given root interface returns, this means that
  // there may be interfaces that are seen but not finished. These interfaces are added to this
  // collection such that we can prioritize them over interfaces that are yet not seen. This leads
  // to more efficient state pruning, since the state for these interfaces can be pruned when they
  // transition to being finished.
  //
  // See also prioritizeNewlySeenButNotFinishedRoots().
  private final List<DexProgramClass> newlySeenButNotFinishedRoots = new ArrayList<>();

  public InterfaceMethodArgumentPropagator(
      AppView<AppInfoWithLiveness> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      MethodStateCollection methodStates) {
    this.appView = appView;
    this.immediateSubtypingInfo = immediateSubtypingInfo;
    this.methodStates = methodStates;
  }

  public void propagateArgumentsForInterfaceMethods(
      Set<DexProgramClass> stronglyConnectedComponent) {
    // Perform a top-down traversal from each root interface in the strongly connected component.
    Deque<DexProgramClass> roots =
        computeRootsForInterfaceMethodPropagation(stronglyConnectedComponent);
    while (!roots.isEmpty()) {
      DexProgramClass root = roots.removeLast();
      traverse(root);
      prioritizeNewlySeenButNotFinishedRoots(roots);
    }
    assert verifyAllInterfacesFinished(stronglyConnectedComponent);
  }

  private Deque<DexProgramClass> computeRootsForInterfaceMethodPropagation(
      Set<DexProgramClass> stronglyConnectedComponent) {
    Deque<DexProgramClass> roots = new ArrayDeque<>();
    for (DexProgramClass clazz : stronglyConnectedComponent) {
      if (clazz.isInterface() && isInterfaceRoot(clazz)) {
        roots.add(clazz);
      }
    }
    return roots;
  }

  private boolean isInterfaceRoot(DexProgramClass interfaceDefinition) {
    assert interfaceDefinition.isInterface();
    for (DexType implementedType : interfaceDefinition.getInterfaces()) {
      DexClass implementedDefinition = appView.definitionFor(implementedType);
      if (implementedDefinition != null && implementedDefinition.isProgramClass()) {
        return false;
      }
    }
    return true;
  }

  private void prioritizeNewlySeenButNotFinishedRoots(Deque<DexProgramClass> roots) {
    assert newlySeenButNotFinishedRoots.stream()
        .allMatch(
            newlySeenButNotFinishedRoot -> {
              assert newlySeenButNotFinishedRoot.isInterface();
              assert isInterfaceRoot(newlySeenButNotFinishedRoot);
              assert isInterfaceSeenButNotFinished(newlySeenButNotFinishedRoot);
              return true;
            });
    // Prioritize this interface over other not yet seen interfaces. This leads to more efficient
    // state pruning.
    roots.addAll(newlySeenButNotFinishedRoots);
    newlySeenButNotFinishedRoots.clear();
  }

  private void traverse(DexProgramClass interfaceDefinition) {
    // Check it the interface and all of its subtypes are already processed.
    if (isInterfaceFinished(interfaceDefinition)) {
      return;
    }

    // Before continuing the top-down traversal, ensure that all super interfaces are processed,
    // but without visiting the entire subtree of each super interface.
    if (!isInterfaceSeenButNotFinished(interfaceDefinition)) {
      processImplementedInterfaces(interfaceDefinition);
      processInterface(interfaceDefinition);
    }

    processImplementingInterfaces(interfaceDefinition);
    markFinished(interfaceDefinition);
  }

  private void processImplementedInterfaces(DexProgramClass interfaceDefinition) {
    assert !isInterfaceSeenButNotFinished(interfaceDefinition);
    assert !isInterfaceFinished(interfaceDefinition);
    for (DexType implementedType : interfaceDefinition.getInterfaces()) {
      DexProgramClass implementedDefinition =
          asProgramClassOrNull(appView.definitionFor(implementedType));
      if (implementedDefinition == null || isInterfaceSeenButNotFinished(implementedDefinition)) {
        continue;
      }
      assert isInterfaceUnseen(implementedDefinition);
      processImplementedInterfaces(implementedDefinition);
      processInterface(implementedDefinition);

      // If this is a root, then record that this root is seen but not finished.
      if (isInterfaceRoot(implementedDefinition)) {
        newlySeenButNotFinishedRoots.add(implementedDefinition);
      }
    }
  }

  private void processImplementingInterfaces(DexProgramClass interfaceDefinition) {
    for (DexProgramClass implementor : immediateSubtypingInfo.getSubclasses(interfaceDefinition)) {
      if (implementor.isInterface()) {
        traverse(implementor);
      }
    }
  }

  private void processInterface(DexProgramClass interfaceDefinition) {
    assert !isInterfaceSeenButNotFinished(interfaceDefinition);
    assert !isInterfaceFinished(interfaceDefinition);
    assert !methodStatesToPropagate.containsKey(interfaceDefinition);
    MethodStateCollection interfaceState = computeInterfaceState(interfaceDefinition);
    propagateInterfaceStateToClassHierarchy(interfaceDefinition, interfaceState);
    markSeenButNotFinished(interfaceDefinition);
  }

  private MethodStateCollection computeInterfaceState(DexProgramClass interfaceDefinition) {
    // Join the state for all parent interfaces into a fresh state created for this interface.
    MethodStateCollection interfaceState = MethodStateCollection.create();
    immediateSubtypingInfo.forEachImmediateSuperClassMatching(
        interfaceDefinition,
        (supertype, superclass) -> superclass != null && superclass.isProgramClass(),
        (supertype, superclass) -> {
          MethodStateCollection implementedInterfaceState =
              methodStatesToPropagate.get(superclass.asProgramClass());
          assert implementedInterfaceState != null;
          interfaceState.addMethodStates(appView, implementedInterfaceState);
        });

    // Add any argument information for virtual methods on the current interface to the state.
    interfaceDefinition.forEachProgramVirtualMethod(
        method -> {
          MethodState methodState = methodStates.get(method);
          if (methodState == null) {
            return;
          }

          // TODO(b/190154391): We should always have an unknown or polymorphic state, but it would
          //  be better to use a monomorphic state when the interface method is a default method
          //  with no overrides (CF backend only). In this case, there is no need to add methodState
          //  to interfaceState.
          assert methodState.isUnknown() || methodState.asConcrete().isPolymorphic();
          interfaceState.addMethodState(appView, method.getReference(), () -> methodState);
        });

    methodStatesToPropagate.put(interfaceDefinition, interfaceState);
    return interfaceState;
  }

  private void propagateInterfaceStateToClassHierarchy(
      DexProgramClass interfaceDefinition, MethodStateCollection interfaceState) {
    // Propagate the argument information for the interface's non-private virtual methods to the
    // the possible dispatch targets declared on classes that are not a subtype of the interface.
    immediateSubtypingInfo.forEachImmediateSubClassMatching(
        interfaceDefinition,
        subclass -> !subclass.isInterface(),
        subclass ->
            interfaceState.forEach(
                (interfaceMethod, interfaceMethodState) -> {
                  SingleResolutionResult resolutionResult =
                      appView
                          .appInfo()
                          .resolveMethodOnClass(interfaceMethod, subclass)
                          .asSingleResolution();
                  if (resolutionResult == null) {
                    assert false;
                    return;
                  }

                  ProgramMethod resolvedMethod = resolutionResult.getResolvedProgramMethod();
                  if (resolvedMethod == null
                      || resolvedMethod.getHolder().isInterface()
                      || resolvedMethod.getHolder() == subclass) {
                    return;
                  }

                  methodStates.addMethodState(
                      appView, resolvedMethod.getReference(), () -> interfaceMethodState);
                }));
  }

  private boolean isInterfaceUnseen(DexProgramClass interfaceDefinition) {
    return !states.containsKey(interfaceDefinition);
  }

  private boolean isInterfaceSeenButNotFinished(DexProgramClass interfaceDefinition) {
    return states.get(interfaceDefinition) == TraversalState.SEEN;
  }

  private boolean isInterfaceFinished(DexProgramClass interfaceDefinition) {
    return states.get(interfaceDefinition) == TraversalState.FINISHED;
  }

  private void markSeenButNotFinished(DexProgramClass interfaceDefinition) {
    assert isInterfaceUnseen(interfaceDefinition);
    states.put(interfaceDefinition, TraversalState.SEEN);
  }

  private void markFinished(DexProgramClass interfaceDefinition) {
    assert isInterfaceSeenButNotFinished(interfaceDefinition);
    states.put(interfaceDefinition, TraversalState.FINISHED);
    methodStatesToPropagate.remove(interfaceDefinition);
  }

  private boolean verifyAllInterfacesFinished(Set<DexProgramClass> stronglyConnectedComponent) {
    assert stronglyConnectedComponent.stream()
        .filter(DexClass::isInterface)
        .allMatch(this::isInterfaceFinished);
    return true;
  }
}
