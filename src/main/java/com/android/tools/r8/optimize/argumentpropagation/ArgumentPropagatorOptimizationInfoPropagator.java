// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.ir.conversion.PrimaryR8IRConverter;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.optimize.argumentpropagation.propagation.InParameterFlowPropagator;
import com.android.tools.r8.optimize.argumentpropagation.propagation.InterfaceMethodArgumentPropagator;
import com.android.tools.r8.optimize.argumentpropagation.propagation.VirtualDispatchMethodArgumentPropagator;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

/**
 * Propagates the argument flow information collected by the {@link ArgumentPropagatorCodeScanner}.
 * This is needed to propagate argument information from call sites to all possible dispatch
 * targets.
 */
public class ArgumentPropagatorOptimizationInfoPropagator {

  private final AppView<AppInfoWithLiveness> appView;
  private final PrimaryR8IRConverter converter;
  private final MethodStateCollectionByReference methodStates;

  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;
  private final List<Set<DexProgramClass>> stronglyConnectedProgramComponents;

  private final BiConsumer<Set<DexProgramClass>, DexMethodSignature>
      interfaceDispatchOutsideProgram;

  ArgumentPropagatorOptimizationInfoPropagator(
      AppView<AppInfoWithLiveness> appView,
      PrimaryR8IRConverter converter,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      MethodStateCollectionByReference methodStates,
      List<Set<DexProgramClass>> stronglyConnectedProgramComponents,
      BiConsumer<Set<DexProgramClass>, DexMethodSignature> interfaceDispatchOutsideProgram) {
    this.appView = appView;
    this.converter = converter;
    this.immediateSubtypingInfo = immediateSubtypingInfo;
    this.methodStates = methodStates;
    this.stronglyConnectedProgramComponents = stronglyConnectedProgramComponents;
    this.interfaceDispatchOutsideProgram = interfaceDispatchOutsideProgram;
  }

  /** Computes an over-approximation of each parameter's value and type. */
  void propagateOptimizationInfo(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    timing.begin("Propagate argument information for virtual methods");
    ThreadUtils.processItems(
        stronglyConnectedProgramComponents,
        this::processStronglyConnectedComponent,
        appView.options().getThreadingModule(),
        executorService);
    timing.end();

    // Solve the parameter flow constraints.
    timing.begin("Solve flow constraints");
    new InParameterFlowPropagator(appView, converter, methodStates).run(executorService);
    timing.end();
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
    new InterfaceMethodArgumentPropagator(
            appView,
            immediateSubtypingInfo,
            methodStates,
            signature ->
                interfaceDispatchOutsideProgram.accept(stronglyConnectedComponent, signature))
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
}
