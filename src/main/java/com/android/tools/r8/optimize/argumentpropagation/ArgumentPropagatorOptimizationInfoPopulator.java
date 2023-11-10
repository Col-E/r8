// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.ir.optimize.info.OptimizationFeedback.getSimpleFeedback;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
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
import com.android.tools.r8.optimize.argumentpropagation.utils.WideningUtils;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

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

  public ArgumentPropagatorOptimizationInfoPopulator(
      AppView<AppInfoWithLiveness> appView,
      PrimaryR8IRConverter converter,
      MethodStateCollectionByReference methodStates,
      PostMethodProcessor.Builder postMethodProcessorBuilder) {
    this.appView = appView;
    this.converter = converter;
    this.methodStates = methodStates;
    this.options = appView.options();
    this.postMethodProcessorBuilder = postMethodProcessorBuilder;
  }

  /**
   * Computes an over-approximation of each parameter's value and type and stores the result in
   * {@link com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo}.
   */
  void populateOptimizationInfo(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    // The information stored on each method is now sound, and can be used as optimization info.
    timing.begin("Set optimization info");
    setOptimizationInfo(executorService);
    timing.end();

    assert methodStates.isEmpty();
  }

  private void setOptimizationInfo(ExecutorService executorService) throws ExecutionException {
    ProgramMethodSet prunedMethods = ProgramMethodSet.createConcurrent();
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> prunedMethods.addAll(setOptimizationInfo(clazz)),
        appView.options().getThreadingModule(),
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

  public void setOptimizationInfo(ProgramMethod method, ProgramMethodSet prunedMethods) {
    setOptimizationInfo(method, prunedMethods, methodStates.remove(method));
  }

  public void setOptimizationInfo(
      ProgramMethod method, ProgramMethodSet prunedMethods, MethodState methodState) {
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
