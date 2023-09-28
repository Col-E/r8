// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.ir.optimize.info.OptimizationFeedback.getSimpleFeedback;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.ir.conversion.PrimaryR8IRConverter;
import com.android.tools.r8.ir.optimize.info.ConcreteCallSiteOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteClassTypeParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMonomorphicMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.StateCloner;
import com.android.tools.r8.optimize.argumentpropagation.propagation.InParameterFlowPropagator;
import com.android.tools.r8.optimize.argumentpropagation.propagation.InterfaceMethodArgumentPropagator;
import com.android.tools.r8.optimize.argumentpropagation.propagation.VirtualDispatchMethodArgumentPropagator;
import com.android.tools.r8.optimize.argumentpropagation.utils.WideningUtils;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
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
public class ArgumentPropagatorOptimizationInfoPopulator {

  private final AppView<AppInfoWithLiveness> appView;
  private final PrimaryR8IRConverter converter;
  private final MethodStateCollectionByReference methodStates;
  private final InternalOptions options;
  private final PostMethodProcessor.Builder postMethodProcessorBuilder;

  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;
  private final List<Set<DexProgramClass>> stronglyConnectedProgramComponents;

  private final BiConsumer<Set<DexProgramClass>, DexMethodSignature>
      interfaceDispatchOutsideProgram;

  ArgumentPropagatorOptimizationInfoPopulator(
      AppView<AppInfoWithLiveness> appView,
      PrimaryR8IRConverter converter,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      MethodStateCollectionByReference methodStates,
      PostMethodProcessor.Builder postMethodProcessorBuilder,
      List<Set<DexProgramClass>> stronglyConnectedProgramComponents,
      BiConsumer<Set<DexProgramClass>, DexMethodSignature> interfaceDispatchOutsideProgram) {
    this.appView = appView;
    this.converter = converter;
    this.immediateSubtypingInfo = immediateSubtypingInfo;
    this.methodStates = methodStates;
    this.options = appView.options();
    this.postMethodProcessorBuilder = postMethodProcessorBuilder;
    this.stronglyConnectedProgramComponents = stronglyConnectedProgramComponents;
    this.interfaceDispatchOutsideProgram = interfaceDispatchOutsideProgram;
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
    timing.begin("Propagate argument information for virtual methods");
    ThreadUtils.processItems(
        stronglyConnectedProgramComponents,
        this::processStronglyConnectedComponent,
        executorService);
    timing.end();

    // Solve the parameter flow constraints.
    timing.begin("Solve flow constraints");
    new InParameterFlowPropagator(appView, converter, methodStates).run(executorService);
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

  private void setOptimizationInfo(ExecutorService executorService) throws ExecutionException {
    ProgramMethodSet prunedMethods = ProgramMethodSet.createConcurrent();
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> prunedMethods.addAll(setOptimizationInfo(clazz)),
        executorService);
    for (ProgramMethod prunedMethod : prunedMethods) {
      converter.onMethodPruned(prunedMethod);
      postMethodProcessorBuilder.remove(prunedMethod, appView.graphLens());
    }
    converter.waveDone(ProgramMethodSet.empty(), executorService);
  }

  private ProgramMethodSet setOptimizationInfo(DexProgramClass clazz) {
    ProgramMethodSet prunedMethods = ProgramMethodSet.create();
    clazz.forEachProgramMethod(method -> setOptimizationInfo(method, prunedMethods));
    clazz.getMethodCollection().removeMethods(prunedMethods.toDefinitionSet());
    return prunedMethods;
  }

  private void setOptimizationInfo(ProgramMethod method, ProgramMethodSet prunedMethods) {
    MethodState methodState = methodStates.remove(method);
    if (methodState.isBottom()) {
      if (method.getDefinition().isClassInitializer()) {
        return;
      }
      // If all uses of a direct method have been removed, we can remove the method. However, if its
      // return value has been propagated, then we retain it for correct evaluation of -if rules in
      // the final round of tree shaking.
      // TODO(b/203188583): Enable pruning of methods with generic signatures. For this to
      //  work we need to pass in a seed to GenericSignatureContextBuilder.create in R8.
      if (method.getDefinition().belongsToDirectPool()
          && !method.getOptimizationInfo().returnValueHasBeenPropagated()
          && !method.getDefinition().getGenericSignature().hasSignature()
          && !appView.appInfo().isFailedMethodResolutionTarget(method.getReference())) {
        prunedMethods.add(method);
      } else if (method.getDefinition().hasCode()) {
        method.convertToAbstractOrThrowNullMethod(appView);
        converter.onMethodCodePruned(method);
        postMethodProcessorBuilder.remove(method, appView.graphLens());
      }
      return;
    }

    // Do not optimize @KeepConstantArgument methods.
    if (!appView.getKeepInfo(method).isConstantArgumentOptimizationAllowed(options)) {
      methodState = MethodState.unknown();
    }

    methodState = getMethodStateAfterUninstantiatedParameterRemoval(method, methodState);

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

    // Widen the dynamic type information so that we don't store any trivial dynamic types.
    // Note that all dynamic types are already being widened when the method states are created, but
    // this does not guarantee that they are non-trivial at this point, since we may refine the
    // object allocation info collection during the primary optimization pass.
    if (!widenDynamicTypes(method, monomorphicMethodState)) {
      return;
    }

    // Verify that there is no parameter with bottom info.
    assert monomorphicMethodState.getParameterStates().stream().noneMatch(ParameterState::isBottom);

    // Verify that all in-parameter information has been pruned by the InParameterFlowPropagator.
    assert monomorphicMethodState.getParameterStates().stream()
        .filter(ParameterState::isConcrete)
        .map(ParameterState::asConcrete)
        .noneMatch(ConcreteParameterState::hasInParameters);

    if (monomorphicMethodState.size() > 0) {
      getSimpleFeedback()
          .setArgumentInfos(
              method,
              ConcreteCallSiteOptimizationInfo.fromMethodState(
                  appView, method, monomorphicMethodState));
    }

    if (!monomorphicMethodState.isReturnValueUsed()) {
      getSimpleFeedback().setIsReturnValueUsed(OptionalBool.FALSE, method);
    }

    // Strengthen the return value of the method if the method is known to return one of the
    // arguments.
    MethodOptimizationInfo optimizationInfo = method.getOptimizationInfo();
    if (optimizationInfo.returnsArgument()) {
      ParameterState returnedArgumentState =
          monomorphicMethodState.getParameterState(optimizationInfo.getReturnedArgument());
      OptimizationFeedback.getSimple()
          .methodReturnsAbstractValue(
              method.getDefinition(), appView, returnedArgumentState.getAbstractValue(appView));
    }
  }

  private MethodState getMethodStateAfterUninstantiatedParameterRemoval(
      ProgramMethod method, MethodState methodState) {
    assert methodState.isMonomorphic() || methodState.isUnknown();
    if (!appView.getKeepInfo(method).isConstantArgumentOptimizationAllowed(options)) {
      return methodState;
    }

    int numberOfArguments = method.getDefinition().getNumberOfArguments();
    boolean isReturnValueUsed;
    List<ParameterState> parameterStates;
    if (methodState.isMonomorphic()) {
      ConcreteMonomorphicMethodState monomorphicMethodState = methodState.asMonomorphic();
      isReturnValueUsed = monomorphicMethodState.isReturnValueUsed();
      parameterStates = monomorphicMethodState.getParameterStates();
    } else {
      assert methodState.isUnknown();
      isReturnValueUsed = true;
      parameterStates =
          ListUtils.newInitializedArrayList(numberOfArguments, ParameterState.unknown());
    }
    List<ParameterState> narrowedParameterStates =
        ListUtils.mapOrElse(
            parameterStates,
            (argumentIndex, parameterState) -> {
              if (!method.getDefinition().isStatic() && argumentIndex == 0) {
                return parameterState;
              }
              DexType argumentType = method.getArgumentType(argumentIndex);
              if (!argumentType.isAlwaysNull(appView)) {
                return parameterState;
              }
              return new ConcreteClassTypeParameterState(
                  appView.abstractValueFactory().createNullValue(argumentType),
                  DynamicType.definitelyNull());
            },
            null);
    return narrowedParameterStates != null
        ? new ConcreteMonomorphicMethodState(isReturnValueUsed, narrowedParameterStates)
        : methodState;
  }

  private boolean widenDynamicTypes(
      ProgramMethod method, ConcreteMonomorphicMethodState methodState) {
    for (int argumentIndex = 0;
        argumentIndex < methodState.getParameterStates().size();
        argumentIndex++) {
      ConcreteParameterState parameterState =
          methodState.getParameterState(argumentIndex).asConcrete();
      if (parameterState == null || !parameterState.isClassParameter()) {
        continue;
      }
      DynamicType dynamicType = parameterState.asClassParameter().getDynamicType();
      DexType staticType = method.getArgumentType(argumentIndex);
      if (shouldWidenDynamicTypeToUnknown(dynamicType, staticType)) {
        methodState.setParameterState(
            argumentIndex,
            parameterState.mutableJoin(
                appView,
                new ConcreteClassTypeParameterState(AbstractValue.bottom(), DynamicType.unknown()),
                staticType,
                StateCloner.getIdentity()));
      }
    }
    return !methodState.isEffectivelyUnknown();
  }

  private boolean shouldWidenDynamicTypeToUnknown(DynamicType dynamicType, DexType staticType) {
    if (dynamicType.isUnknown()) {
      return false;
    }
    if (WideningUtils.widenDynamicNonReceiverType(appView, dynamicType, staticType).isUnknown()) {
      return true;
    }
    TypeElement staticTypeElement = staticType.toTypeElement(appView);
    TypeElement dynamicUpperBoundType = dynamicType.getDynamicUpperBoundType(staticTypeElement);
    if (!dynamicUpperBoundType.lessThanOrEqual(staticTypeElement, appView)) {
      return true;
    }
    return false;
  }
}
