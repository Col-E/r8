// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.propagation;

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
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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
public class InterfaceMethodArgumentPropagator extends MethodArgumentPropagator {

  // Contains the argument information for each interface method (including inherited interface
  // methods) on the seen but not finished interfaces.
  final Map<DexProgramClass, MethodStateCollection> methodStatesToPropagate =
      new IdentityHashMap<>();

  public InterfaceMethodArgumentPropagator(
      AppView<AppInfoWithLiveness> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      MethodStateCollection methodStates) {
    super(appView, immediateSubtypingInfo, methodStates);
  }

  @Override
  public void run(Set<DexProgramClass> stronglyConnectedComponent) {
    super.run(stronglyConnectedComponent);
    assert verifyAllInterfacesFinished(stronglyConnectedComponent);
  }

  @Override
  public void forEachSubClass(DexProgramClass clazz, Consumer<DexProgramClass> consumer) {
    for (DexProgramClass subclass : immediateSubtypingInfo.getSubclasses(clazz)) {
      if (subclass.isInterface()) {
        consumer.accept(subclass);
      }
    }
  }

  @Override
  public boolean isRoot(DexProgramClass clazz) {
    if (!clazz.isInterface()) {
      return false;
    }
    for (DexType implementedType : clazz.getInterfaces()) {
      DexClass implementedDefinition = appView.definitionFor(implementedType);
      if (implementedDefinition != null && implementedDefinition.isProgramClass()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void visit(DexProgramClass clazz) {
    assert !methodStatesToPropagate.containsKey(clazz);
    MethodStateCollection interfaceState = computeInterfaceState(clazz);
    propagateInterfaceStateToClassHierarchy(clazz, interfaceState);
  }

  @Override
  public void prune(DexProgramClass clazz) {
    methodStatesToPropagate.remove(clazz);
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
          if (methodState.isBottom()) {
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

  private boolean verifyAllInterfacesFinished(Set<DexProgramClass> stronglyConnectedComponent) {
    assert stronglyConnectedComponent.stream()
        .filter(DexClass::isInterface)
        .allMatch(this::isClassFinished);
    return true;
  }
}
