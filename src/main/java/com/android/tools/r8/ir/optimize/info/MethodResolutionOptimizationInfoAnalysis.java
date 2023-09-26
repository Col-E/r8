// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import static com.google.common.base.Predicates.not;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ObjectAllocationInfoCollection;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.optimize.argumentpropagation.utils.DepthFirstTopDownClassHierarchyTraversal;
import com.android.tools.r8.optimize.argumentpropagation.utils.ProgramClassesBidirectedGraph;
import com.android.tools.r8.optimize.argumentpropagation.utils.WideningUtils;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.utils.BitSetUtils;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.NumberUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.DexMethodSignatureMap;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class MethodResolutionOptimizationInfoAnalysis {

  public static void run(AppView<AppInfoWithLiveness> appView, ExecutorService executorService)
      throws ExecutionException {
    ImmediateProgramSubtypingInfo immediateSubtypingInfo =
        ImmediateProgramSubtypingInfo.create(appView);
    List<Set<DexProgramClass>> stronglyConnectedComponents =
        new ProgramClassesBidirectedGraph(appView, immediateSubtypingInfo)
            .computeStronglyConnectedComponents();
    MethodResolutionOptimizationInfoCollection.Builder builder =
        MethodResolutionOptimizationInfoCollection.builder();
    ThreadUtils.processItems(
        stronglyConnectedComponents,
        stronglyConnectedComponent ->
            new Traversal(appView, builder, immediateSubtypingInfo).run(stronglyConnectedComponent),
        executorService);
    appView.setMethodResolutionOptimizationInfoCollection(builder.build());
  }

  private static class Traversal extends DepthFirstTopDownClassHierarchyTraversal {

    private final MethodResolutionOptimizationInfoCollection.Builder builder;
    private final Map<DexProgramClass, TraversalState> states = new IdentityHashMap<>();

    private Traversal(
        AppView<AppInfoWithLiveness> appView,
        MethodResolutionOptimizationInfoCollection.Builder builder,
        ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
      super(appView, immediateSubtypingInfo);
      this.builder = builder;
    }

    /**
     * Build up a state that describes which virtual methods need to have their optimization info
     * pushed upwards in the subsequent upwards traversal (instead of naively pushing optimization
     * info upwards for *all* virtual methods).
     *
     * <p>Example: In the below piece of code, we want to push the optimization for C.m() up to
     * class B, but we do not want to push the optimization info for B.m() and C.m() up to class A,
     * since class A does not declare the method m().
     *
     * <pre>
     * class A {}
     * class B extends A {
     *   void m() { ... }
     * }
     * class C extends B {
     *   void m() { ... }
     * }
     * </pre>
     */
    @Override
    public void visit(DexProgramClass clazz) {
      DownwardsTraversalState state = new DownwardsTraversalState(DexMethodSignatureSet.create());
      immediateSubtypingInfo.forEachImmediateProgramSuperClass(
          clazz,
          superClass -> {
            DownwardsTraversalState superState =
                states
                    .getOrDefault(superClass, DownwardsTraversalState.empty())
                    .asDownwardsTraversalState();
            state.add(superState);
            for (DexEncodedMethod method : superClass.virtualMethods()) {
              // No need to request optimization info for the (non-existing) overrides of final
              // methods.
              if (method.isFinal()) {
                continue;
              }
              // If the method is not abstract and does not have any optimization info, there is no
              // need to request the optimization info for overrides in subclasses, since the join
              // of the optimization info becomes unknown anyway.
              if (method.isAbstract() || !method.getOptimizationInfo().isDefault()) {
                state.virtualMethodsInSuperClasses.add(method);
              }
            }
          });
      if (!state.isEmpty()) {
        states.put(clazz, state);
      }
    }

    @Override
    public void prune(DexProgramClass clazz) {
      // Extract the downwards traversal state that describes what info needs to be pushed upwards.
      DownwardsTraversalState state =
          MapUtils.removeOrDefault(states, clazz, DownwardsTraversalState.empty())
              .asDownwardsTraversalState();

      // Join the optimization info for all methods in all immediate subclasses.
      UpwardsTraversalState newState = new UpwardsTraversalState(DexMethodSignatureMap.create());
      forEachSubClass(
          clazz,
          subClass -> {
            UpwardsTraversalState subClassState =
                states
                    .getOrDefault(subClass, UpwardsTraversalState.empty())
                    .asUpwardsTraversalState();
            newState.join(appView, subClassState);

            // If the current class is an interface and the current subclass is not, then we need
            // special handling to account for the fact that invoke-interface instructions may
            // dispatch to virtual "sibling" methods that are declared on a class that is not a
            // subtype of the interface declaring the interface method.
            if (clazz.isInterface() && !subClass.isInterface()) {
              handleInvokeInterfaceToSiblingMethod(clazz, subClass, state, newState);
            }
          });
      ObjectAllocationInfoCollection objectAllocationInfoCollection =
          appView.appInfo().getObjectAllocationInfoCollection();
      if (objectAllocationInfoCollection.isImmediateInterfaceOfInstantiatedLambda(clazz)) {
        for (DexEncodedMethod method : clazz.virtualMethods()) {
          newState.joinMethodOptimizationInfo(
              appView, method.getSignature(), DefaultMethodOptimizationInfo.getInstance());
        }
      } else {
        for (DexEncodedMethod method : clazz.virtualMethods()) {
          KeepMethodInfo keepInfo = appView.getKeepInfo().getMethodInfo(method, clazz);
          if (!method.isAbstract()) {
            newState.joinMethodOptimizationInfo(
                appView, method.getSignature(), method.getOptimizationInfo());
          } else if (!keepInfo.isShrinkingAllowed(appView.options())) {
            // Method is kept and could be overridden outside app (e.g., in tests).
            newState.joinMethodOptimizationInfo(
                appView, method.getSignature(), DefaultMethodOptimizationInfo.getInstance());
          }
        }
      }

      // Record the computed optimization info for all non-final virtual methods on the current
      // class.
      if (!clazz.isFinal()) {
        for (DexEncodedMethod method : clazz.virtualMethods(not(DexEncodedMethod::isFinal))) {
          MethodOptimizationInfo info = newState.getMethodOptimizationInfo(method);
          builder.add(method.getReference(), info);
        }
      }

      // Prune out everything that does not need to be pushed upwards and store the resulting state.
      newState
          .infos
          .keySet()
          .removeIf(method -> !state.virtualMethodsInSuperClasses.contains(method));
      if (!newState.isEmpty()) {
        states.put(clazz, newState);
      }
    }

    private void handleInvokeInterfaceToSiblingMethod(
        DexProgramClass iface,
        DexProgramClass subClass,
        DownwardsTraversalState state,
        UpwardsTraversalState newState) {
      assert iface.isInterface();
      assert !subClass.isInterface();

      DexMethodSignatureSet interfaceMethodsInClassOrAbove =
          DexMethodSignatureSet.create(state.virtualMethodsInSuperClasses);
      interfaceMethodsInClassOrAbove.addAllMethods(iface.virtualMethods());

      for (DexMethodSignature method : interfaceMethodsInClassOrAbove) {
        MethodResolutionResult resolutionResult =
            appView.appInfo().resolveMethodOnClass(subClass, method);
        if (resolutionResult.isFailedResolution()) {
          assert resolutionResult.asFailedResolution().hasMethodsCausingError();
          continue;
        }

        if (resolutionResult.isMultiMethodResolutionResult()) {
          // Conservatively drop the current optimization info.
          newState.joinMethodOptimizationInfo(
              appView, method, DefaultMethodOptimizationInfo.getInstance());
          continue;
        }

        assert resolutionResult.isSingleResolution();
        DexClassAndMethod resolvedMethod = resolutionResult.getResolutionPair();
        if (!resolvedMethod.getHolder().isInterface() && resolvedMethod.getHolder() != subClass) {
          newState.joinMethodOptimizationInfo(
              appView, method, resolvedMethod.getOptimizationInfo());
        }
      }
    }
  }

  private abstract static class TraversalState {

    DownwardsTraversalState asDownwardsTraversalState() {
      return null;
    }

    UpwardsTraversalState asUpwardsTraversalState() {
      return null;
    }
  }

  // State that is used for each class during the downwards traversal over the class hierarchy.
  private static class DownwardsTraversalState extends TraversalState {

    private static final DownwardsTraversalState EMPTY =
        new DownwardsTraversalState(DexMethodSignatureSet.empty());

    // The set of virtual methods in the super classes of the current class. For each method in this
    // set we want the subsequent upwards traversal to include the optimization info for any
    // overrides.
    DexMethodSignatureSet virtualMethodsInSuperClasses;

    DownwardsTraversalState(DexMethodSignatureSet virtualMethodsInSuperClasses) {
      this.virtualMethodsInSuperClasses = virtualMethodsInSuperClasses;
    }

    static DownwardsTraversalState empty() {
      return EMPTY;
    }

    void add(DownwardsTraversalState state) {
      virtualMethodsInSuperClasses.addAll(state.virtualMethodsInSuperClasses);
    }

    boolean isEmpty() {
      return virtualMethodsInSuperClasses.isEmpty();
    }

    @Override
    DownwardsTraversalState asDownwardsTraversalState() {
      return this;
    }
  }

  private static class UpwardsTraversalState extends TraversalState {

    private static final UpwardsTraversalState EMPTY =
        new UpwardsTraversalState(DexMethodSignatureMap.empty());

    private final DexMethodSignatureMap<MethodOptimizationInfo> infos;

    UpwardsTraversalState(DexMethodSignatureMap<MethodOptimizationInfo> infos) {
      this.infos = infos;
    }

    static UpwardsTraversalState empty() {
      return EMPTY;
    }

    void join(AppView<AppInfoWithLiveness> appView, UpwardsTraversalState state) {
      state.infos.forEach((method, info) -> joinMethodOptimizationInfo(appView, method, info));
    }

    void joinMethodOptimizationInfo(
        AppView<AppInfoWithLiveness> appView,
        DexMethodSignature method,
        MethodOptimizationInfo info) {
      infos.compute(
          method,
          (m, existingInfo) -> existingInfo == null ? info : join(appView, m, existingInfo, info));
    }

    static MethodOptimizationInfo join(
        AppView<AppInfoWithLiveness> appView,
        DexMethodSignature method,
        MethodOptimizationInfo info,
        MethodOptimizationInfo otherInfo) {
      if (info.isDefault() || otherInfo.isDefault()) {
        return DefaultMethodOptimizationInfo.getInstance();
      }

      // Join dynamic return type.
      DynamicType dynamicReturnType;
      if (method.getReturnType().isVoidType()) {
        dynamicReturnType = DynamicType.unknown();
      } else {
        dynamicReturnType = info.getDynamicType().join(appView, otherInfo.getDynamicType());
        if (method.getReturnType().isClassType()) {
          dynamicReturnType =
              WideningUtils.widenDynamicNonReceiverType(
                  appView, dynamicReturnType, method.getReturnType());
        } else {
          // TODO: also widen array types.
          assert method.getReturnType().isArrayType() || dynamicReturnType.isUnknown();
        }
      }

      // Join abstract return value.
      AbstractValue abstractReturnValue;
      TypeElement staticReturnType = null;
      if (method.getReturnType().isVoidType()) {
        abstractReturnValue = AbstractValue.unknown();
      } else {
        staticReturnType = method.getReturnType().toTypeElement(appView);
        abstractReturnValue =
            appView
                .getAbstractValueConstantPropagationJoiner()
                .join(
                    info.getAbstractReturnValue(),
                    otherInfo.getAbstractReturnValue(),
                    staticReturnType);
      }

      // Join returned argument index.
      int returnedArgument =
          info.returnsArgument() && otherInfo.returnsArgument()
              ? NumberUtils.getIfEqualsOrDefault(
                  info.getReturnedArgument(), otherInfo.getReturnedArgument(), -1)
              : -1;

      // Join non-null-param-on-normal-exits information.
      BitSet nonNullParamOnNormalExits =
          BitSetUtils.intersectNullableBitSets(
              info.getNonNullParamOnNormalExits(), otherInfo.getNonNullParamOnNormalExits());
      assert nonNullParamOnNormalExits == null || !nonNullParamOnNormalExits.isEmpty();

      // Join non-null-param-or-throw information.
      BitSet nonNullParamOrThrow =
          BitSetUtils.intersectNullableBitSets(
              info.getNonNullParamOrThrow(), otherInfo.getNonNullParamOrThrow());
      assert nonNullParamOrThrow == null || !nonNullParamOrThrow.isEmpty();

      // Join flags.
      boolean mayNotHaveSideEffects = !info.mayHaveSideEffects() && !otherInfo.mayHaveSideEffects();
      boolean neverReturnsNormally =
          info.neverReturnsNormally() && otherInfo.neverReturnsNormally();
      boolean returnValueOnlyDependsOnArguments =
          info.returnValueOnlyDependsOnArguments() && otherInfo.returnValueOnlyDependsOnArguments();

      if (dynamicReturnType.isUnknown()
          && abstractReturnValue.isUnknown()
          && returnedArgument < 0
          && nonNullParamOnNormalExits == null
          && nonNullParamOrThrow == null
          && !mayNotHaveSideEffects
          && !neverReturnsNormally
          && !returnValueOnlyDependsOnArguments) {
        return DefaultMethodOptimizationInfo.getInstance();
      } else {
        MutableMethodOptimizationInfo result =
            DefaultMethodOptimizationInfo.getInstance().toMutableOptimizationInfo();
        result.setDynamicType(appView, dynamicReturnType, staticReturnType);
        result.markReturnsAbstractValue(abstractReturnValue);
        if (returnedArgument >= 0) {
          result.markReturnsArgument(returnedArgument);
        }
        result.setNonNullParamOnNormalExits(nonNullParamOnNormalExits);
        result.setNonNullParamOrThrow(nonNullParamOrThrow);
        if (mayNotHaveSideEffects) {
          result.markMayNotHaveSideEffects();
        }
        if (neverReturnsNormally) {
          result.markNeverReturnsNormally();
        }
        if (returnValueOnlyDependsOnArguments) {
          result.markReturnValueOnlyDependsOnArguments();
        }
        return result;
      }
    }

    MethodOptimizationInfo getMethodOptimizationInfo(DexEncodedMethod method) {
      return infos.getOrDefault(method, DefaultMethodOptimizationInfo.getInstance());
    }

    boolean isEmpty() {
      return infos.isEmpty();
    }

    @Override
    UpwardsTraversalState asUpwardsTraversalState() {
      return this;
    }
  }
}
