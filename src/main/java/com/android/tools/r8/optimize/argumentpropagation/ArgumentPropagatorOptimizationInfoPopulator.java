// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.optimize.argumentpropagation.utils.StronglyConnectedProgramClasses.computeStronglyConnectedProgramClasses;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.DynamicType;
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
import com.android.tools.r8.optimize.argumentpropagation.utils.WideningUtils;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.stream.IntStream;

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
        computeStronglyConnectedProgramClasses(appView, immediateSubtypingInfo);
  }

  /**
   * Computes an over-approximation of each parameter's value and type and stores the result in
   * {@link com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo}.
   */
  void populateOptimizationInfo(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    // TODO(b/190154391): Propagate argument information to handle virtual dispatch.
    // TODO(b/190154391): To deal with arguments that are themselves passed as arguments to invoke
    //  instructions, build a flow graph where nodes are parameters and there is an edge from a
    //  parameter p1 to p2 if the value of p2 is at least the value of p1. Then propagate the
    //  collected argument information throughout the flow graph.
    // TODO(b/190154391): If we learn that parameter p1 is constant, and that the enclosing method
    //  returns p1 according to the optimization info, then update the optimization info to describe
    //  that the method returns the constant.
    timing.begin("Propagate argument information for virtual methods");
    ThreadUtils.processItems(
        stronglyConnectedComponents, this::processStronglyConnectedComponent, executorService);
    timing.end();

    // Solve the parameter flow constraints.
    timing.begin("Solve flow constraints");
    new InParameterFlowPropagator(appView, methodStates).run(executorService);
    timing.end();

    // The information stored on each method is now sound, and can be used as optimization info.
    timing.begin("Set optimization info");
    setOptimizationInfo(executorService);
    timing.end();

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
    // TODO(b/190154391): Before running the top-down traversal, consider lowering the argument
    //  information for non-private virtual methods. If we have some argument information with upper
    //  bound=B, which is stored on a method on class A, we could move this argument information
    //  from class A to B. This way we could potentially get rid of the "inactive argument
    //  information" during the depth-first class hierarchy traversal, since the argument
    //  information would be active by construction when it is first seen during the top-down class
    //  hierarchy traversal.
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
    MethodState methodState = methodStates.removeOrElse(method, null);
    if (methodState == null) {
      return;
    }

    if (methodState.isBottom()) {
      if (!appView.options().canUseDefaultAndStaticInterfaceMethods()
          && method.getHolder().isInterface()) {
        // TODO(b/190154391): The method has not been moved to the companion class yet, so we can't
        //  remove its code object.
        return;
      }
      DexEncodedMethod definition = method.getDefinition();
      definition.setCode(definition.buildEmptyThrowingCode(appView.options()), appView);
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

    // Verify that there is no parameter with bottom info.
    assert monomorphicMethodState.getParameterStates().stream().noneMatch(ParameterState::isBottom);

    // Verify that all in-parameter information has been pruned by the InParameterFlowPropagator.
    assert monomorphicMethodState.getParameterStates().stream()
        .filter(ParameterState::isConcrete)
        .map(ParameterState::asConcrete)
        .noneMatch(ConcreteParameterState::hasInParameters);

    // Verify that the dynamic type information is correct.
    assert IntStream.range(0, monomorphicMethodState.getParameterStates().size())
        .filter(
            index -> {
              ParameterState parameterState = monomorphicMethodState.getParameterState(index);
              return parameterState.isConcrete() && parameterState.asConcrete().isClassParameter();
            })
        .allMatch(
            index -> {
              DynamicType dynamicType =
                  monomorphicMethodState
                      .getParameterState(index)
                      .asConcrete()
                      .asClassParameter()
                      .getDynamicType();
              DexType staticType = method.getArgumentType(index);
              assert dynamicType
                  == WideningUtils.widenDynamicNonReceiverType(appView, dynamicType, staticType);
              return true;
            });

    method
        .getDefinition()
        .joinCallSiteOptimizationInfo(
            ConcreteCallSiteOptimizationInfo.fromMethodState(
                appView, method, monomorphicMethodState),
            appView);
  }
}
