// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.info.ConcreteCallSiteOptimizationInfo;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMonomorphicMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ParameterState;
import com.android.tools.r8.optimize.argumentpropagation.propagation.InParameterFlowPropagator;
import com.android.tools.r8.optimize.argumentpropagation.propagation.InterfaceMethodArgumentPropagator;
import com.android.tools.r8.optimize.argumentpropagation.propagation.VirtualDispatchMethodArgumentPropagator;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Propagates the argument flow information collected by the {@link ArgumentPropagatorCodeScanner}.
 * This is needed to propagate argument information from call sites to all possible dispatch
 * targets.
 */
public class ArgumentPropagatorOptimizationInfoPopulator {

  private final AppView<AppInfoWithLiveness> appView;
  private final MethodStateCollectionByReference methodStates;

  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;
  private final List<Set<DexProgramClass>> stronglyConnectedComponents;

  ArgumentPropagatorOptimizationInfoPopulator(
      AppView<AppInfoWithLiveness> appView, MethodStateCollectionByReference methodStates) {
    this.appView = appView;
    this.methodStates = methodStates;

    ImmediateProgramSubtypingInfo immediateSubtypingInfo =
        ImmediateProgramSubtypingInfo.create(appView);
    this.immediateSubtypingInfo = immediateSubtypingInfo;
    this.stronglyConnectedComponents =
        computeStronglyConnectedComponents(appView, immediateSubtypingInfo);
  }

  /**
   * Computes the strongly connected components in the program class hierarchy (where extends and
   * implements edges are treated as bidirectional).
   *
   * <p>All strongly connected components can be processed in parallel.
   */
  private static List<Set<DexProgramClass>> computeStronglyConnectedComponents(
      AppView<AppInfoWithLiveness> appView, ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    Set<DexProgramClass> seen = Sets.newIdentityHashSet();
    List<Set<DexProgramClass>> stronglyConnectedComponents = new ArrayList<>();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (seen.contains(clazz)) {
        continue;
      }
      Set<DexProgramClass> stronglyConnectedComponent =
          computeStronglyConnectedComponent(clazz, immediateSubtypingInfo);
      stronglyConnectedComponents.add(stronglyConnectedComponent);
      seen.addAll(stronglyConnectedComponent);
    }
    return stronglyConnectedComponents;
  }

  private static Set<DexProgramClass> computeStronglyConnectedComponent(
      DexProgramClass clazz, ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    WorkList<DexProgramClass> worklist = WorkList.newIdentityWorkList(clazz);
    while (worklist.hasNext()) {
      DexProgramClass current = worklist.next();
      immediateSubtypingInfo.forEachImmediateProgramSuperClass(current, worklist::addIfNotSeen);
      worklist.addIfNotSeen(immediateSubtypingInfo.getSubclasses(current));
    }
    return worklist.getSeenSet();
  }

  /**
   * Computes an over-approximation of each parameter's value and type and stores the result in
   * {@link com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo}.
   */
  void populateOptimizationInfo(ExecutorService executorService) throws ExecutionException {
    // TODO(b/190154391): Propagate argument information to handle virtual dispatch.
    // TODO(b/190154391): To deal with arguments that are themselves passed as arguments to invoke
    //  instructions, build a flow graph where nodes are parameters and there is an edge from a
    //  parameter p1 to p2 if the value of p2 is at least the value of p1. Then propagate the
    //  collected argument information throughout the flow graph.
    // TODO(b/190154391): If we learn that parameter p1 is constant, and that the enclosing method
    //  returns p1 according to the optimization info, then update the optimization info to describe
    //  that the method returns the constant.
    ThreadUtils.processItems(
        stronglyConnectedComponents, this::processStronglyConnectedComponent, executorService);

    // Solve the parameter flow constraints.
    new InParameterFlowPropagator(appView, methodStates).run(executorService);

    // The information stored on each method is now sound, and can be used as optimization info.
    setOptimizationInfo(executorService);

    assert methodStates.isEmpty();
  }

  private void processStronglyConnectedComponent(Set<DexProgramClass> stronglyConnectedComponent) {
    // Invoke instructions that target interface methods may dispatch to methods that are not
    // defined on a subclass of the interface method holder.
    //
    // Example: Calling I.m() will dispatch to A.m(), but A is not a subtype of I.
    //
    //   class A { public void m() {} }
    //   interface I { void m(); }
    //   class B extends A implements I {}
    //
    // To handle this we first propagate any argument information stored for I.m() to A.m() by doing
    // a top-down traversal over the interfaces in the strongly connected component.
    new InterfaceMethodArgumentPropagator(appView, immediateSubtypingInfo, methodStates)
        .run(stronglyConnectedComponent);

    // Now all the argument information for a given method is guaranteed to be stored on a supertype
    // of the method's holder. All that remains is to propagate the information downwards in the
    // class hierarchy to propagate the argument information for a non-private virtual method to its
    // overrides.
    new VirtualDispatchMethodArgumentPropagator(appView, immediateSubtypingInfo, methodStates)
        .run(stronglyConnectedComponent);
  }

  private void setOptimizationInfo(ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(
        appView.appInfo().classes(), this::setOptimizationInfo, executorService);
  }

  private void setOptimizationInfo(DexProgramClass clazz) {
    clazz.forEachProgramMethod(this::setOptimizationInfo);
  }

  private void setOptimizationInfo(ProgramMethod method) {
    MethodState methodState = methodStates.remove(method);
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

    ConcreteMonomorphicMethodState monomorphicMethodState = concreteMethodState.asMonomorphic();
    assert monomorphicMethodState.getParameterStates().stream()
        .filter(ParameterState::isConcrete)
        .map(ParameterState::asConcrete)
        .noneMatch(ConcreteParameterState::hasInParameters);

    method
        .getDefinition()
        .joinCallSiteOptimizationInfo(
            ConcreteCallSiteOptimizationInfo.fromMethodState(
                appView, method, monomorphicMethodState),
            appView);
  }
}
