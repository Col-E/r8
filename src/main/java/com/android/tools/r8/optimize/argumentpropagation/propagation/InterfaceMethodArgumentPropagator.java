// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.propagation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePolymorphicMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionBySignature;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.StateCloner;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
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
  final Map<DexProgramClass, MethodStateCollectionBySignature> methodStatesToPropagate =
      new IdentityHashMap<>();
  final Consumer<DexMethodSignature> interfaceDispatchOutsideProgram;

  public InterfaceMethodArgumentPropagator(
      AppView<AppInfoWithLiveness> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      MethodStateCollectionByReference methodStates,
      Consumer<DexMethodSignature> interfaceDispatchOutsideProgram) {
    super(appView, immediateSubtypingInfo, methodStates);
    this.interfaceDispatchOutsideProgram = interfaceDispatchOutsideProgram;
  }

  @Override
  public void run(Collection<DexProgramClass> stronglyConnectedComponent) {
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
    return clazz.isInterface() && super.isRoot(clazz);
  }

  @Override
  public void visit(DexProgramClass clazz) {
    assert !methodStatesToPropagate.containsKey(clazz);
    MethodStateCollectionBySignature interfaceState = computeInterfaceState(clazz);
    propagateInterfaceStateToClassHierarchy(clazz, interfaceState);
  }

  @Override
  public void prune(DexProgramClass clazz) {
    methodStatesToPropagate.remove(clazz);
  }

  private MethodStateCollectionBySignature computeInterfaceState(
      DexProgramClass interfaceDefinition) {
    // Join the state for all parent interfaces into a fresh state created for this interface.
    MethodStateCollectionBySignature interfaceState = MethodStateCollectionBySignature.create();
    immediateSubtypingInfo.forEachImmediateProgramSuperClass(
        interfaceDefinition,
        superclass -> {
          MethodStateCollectionBySignature implementedInterfaceState =
              methodStatesToPropagate.get(superclass);
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
          interfaceState.addMethodState(appView, method, methodState);
        });

    methodStatesToPropagate.put(interfaceDefinition, interfaceState);
    return interfaceState;
  }

  private void propagateInterfaceStateToClassHierarchy(
      DexProgramClass interfaceDefinition, MethodStateCollectionBySignature interfaceState) {
    // Propagate the argument information for the interface's non-private virtual methods to the
    // the possible dispatch targets declared on classes that are not a subtype of the interface.
    immediateSubtypingInfo.forEachImmediateSubClassMatching(
        interfaceDefinition,
        subclass -> !subclass.isInterface(),
        subclass ->
            interfaceState.forEach(
                (interfaceMethod, interfaceMethodState) -> {
                  MethodResolutionResult resolutionResult =
                      appView.appInfo().resolveMethodOnClassLegacy(subclass, interfaceMethod);
                  if (resolutionResult.isFailedResolution()) {
                    // TODO(b/190154391): Do we need to propagate argument information to the first
                    //  virtual method above the inaccessible method in the class hierarchy?
                    assert resolutionResult.asFailedResolution().hasMethodsCausingError();
                    return;
                  }

                  assert resolutionResult.isSingleResolution();
                  if (!resolutionResult.getResolutionPair().isProgramMethod()) {
                    interfaceDispatchOutsideProgram.accept(interfaceMethod);
                    return;
                  }

                  ProgramMethod resolvedMethod = resolutionResult.getResolvedProgramMethod();
                  if (resolvedMethod == null || resolvedMethod.getHolder() == subclass) {
                    return;
                  }

                  MethodState transformedInterfaceMethodState =
                      transformInterfaceMethodStateForClassMethod(
                          subclass, resolvedMethod, interfaceMethodState);
                  if (!transformedInterfaceMethodState.isBottom()) {
                    methodStates.addMethodState(
                        appView, resolvedMethod, transformedInterfaceMethodState);
                  }
                }));
  }

  private MethodState transformInterfaceMethodStateForClassMethod(
      DexProgramClass clazz, ProgramMethod resolvedMethod, MethodState methodState) {
    if (!methodState.isPolymorphic()) {
      return methodState.mutableCopy();
    }

    // Rewrite the bounds of the polymorphic method state. If a given piece of argument information
    // should be propagated to the resolved method, we replace the type bounds by the holder of the
    // resolved method.
    ConcretePolymorphicMethodState polymorphicMethodState = methodState.asPolymorphic();
    MethodState rewrittenMethodState =
        polymorphicMethodState.mutableCopyWithRewrittenBounds(
            appView,
            bounds -> {
              boolean shouldPropagateMethodStateForBounds;
              if (bounds.isUnknown()) {
                shouldPropagateMethodStateForBounds = true;
              } else {
                ClassTypeElement upperBound = bounds.getDynamicUpperBoundType().asClassType();
                shouldPropagateMethodStateForBounds =
                    upperBound
                        .getInterfaces()
                        .anyMatch(
                            (interfaceType, isKnown) ->
                                appView.appInfo().isSubtype(clazz.getType(), interfaceType));
              }
              if (shouldPropagateMethodStateForBounds) {
                return DynamicType.createExact(
                    TypeElement.fromDexType(
                            resolvedMethod.getHolderType(), Nullability.maybeNull(), appView)
                        .asClassType());
              }
              return null;
            },
            resolvedMethod.getMethodSignature(),
            StateCloner.getCloner());

    // If the resolved method is a virtual method that does not override any methods and are not
    // overridden by any methods, then we use a monomorphic method state for it. Therefore, we
    // transform this polymorphic method state into a monomorphic method state, before joining it
    // into the method's state.
    if (methodStates.get(resolvedMethod).isMonomorphic() && rewrittenMethodState.isPolymorphic()) {
      ConcretePolymorphicMethodState rewrittenPolymorphicMethodState =
          rewrittenMethodState.asPolymorphic();
      assert rewrittenPolymorphicMethodState.values().size() == 1;
      return rewrittenPolymorphicMethodState.values().iterator().next();
    }

    return rewrittenMethodState;
  }

  private boolean verifyAllInterfacesFinished(
      Collection<DexProgramClass> stronglyConnectedComponent) {
    assert stronglyConnectedComponent.stream()
        .filter(DexClass::isInterface)
        .allMatch(this::isClassFinished);
    return true;
  }
}
