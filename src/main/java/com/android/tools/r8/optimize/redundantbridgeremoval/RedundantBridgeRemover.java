// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.redundantbridgeremoval;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.FailedResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.optimize.InvokeSingleTargetExtractor;
import com.android.tools.r8.optimize.InvokeSingleTargetExtractor.InvokeKind;
import com.android.tools.r8.optimize.MemberRebindingIdentityLens;
import com.android.tools.r8.optimize.argumentpropagation.utils.DepthFirstTopDownClassHierarchyTraversal;
import com.android.tools.r8.optimize.argumentpropagation.utils.ProgramClassesBidirectedGraph;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class RedundantBridgeRemover {

  private final AppView<AppInfoWithLiveness> appView;
  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;
  private final RedundantBridgeRemovalOptions redundantBridgeRemovalOptions;

  private final RedundantBridgeRemovalLens.Builder lensBuilder =
      new RedundantBridgeRemovalLens.Builder();

  private boolean mustRetargetInvokesToTargetMethod = false;

  public RedundantBridgeRemover(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.immediateSubtypingInfo = ImmediateProgramSubtypingInfo.create(appView);
    this.redundantBridgeRemovalOptions = appView.options().getRedundantBridgeRemovalOptions();
  }

  public RedundantBridgeRemover setMustRetargetInvokesToTargetMethod() {
    mustRetargetInvokesToTargetMethod = true;
    return this;
  }

  private DexClassAndMethod getTargetForRedundantNonAbstractBridge(ProgramMethod method) {
    DexEncodedMethod definition = method.getDefinition();
    BridgeInfo bridgeInfo = definition.getOptimizationInfo().getBridgeInfo();
    boolean isBridge = definition.isBridge() || bridgeInfo != null;
    if (!isBridge || definition.isAbstract()) {
      return null;
    }
    InvokeSingleTargetExtractor targetExtractor = new InvokeSingleTargetExtractor(appView, method);
    method.registerCodeReferences(targetExtractor);
    DexMethod target = targetExtractor.getTarget();
    // javac-generated visibility forward bridge method has same descriptor (name, signature and
    // return type).
    if (target == null || !target.match(method.getReference())) {
      return null;
    }
    if (!isTargetingSuperMethod(method, targetExtractor.getKind(), target)) {
      return null;
    }
    // This is a visibility forward, so check for the direct target.
    DexClassAndMethod targetMethod =
        appView.appInfo().unsafeResolveMethodDueToDexFormatLegacy(target).getResolutionPair();
    if (targetMethod == null) {
      return null;
    }
    if (!targetMethod
        .getDefinition()
        .isAtLeastAsVisibleAsOtherInSameHierarchy(method.getDefinition(), appView)) {
      return null;
    }
    if (definition.isStatic()
        && method.getHolder().hasClassInitializer()
        && method
            .getHolder()
            .classInitializationMayHaveSideEffectsInContext(appView, targetMethod)) {
      return null;
    }
    return targetMethod;
  }

  private boolean isTargetingSuperMethod(ProgramMethod method, InvokeKind kind, DexMethod target) {
    if (kind == InvokeKind.ILLEGAL) {
      return false;
    }
    if (kind == InvokeKind.DIRECT) {
      return method.getDefinition().isInstanceInitializer()
          && appView.options().canHaveNonReboundConstructorInvoke()
          && appView.appInfo().isStrictSubtypeOf(method.getHolderType(), target.getHolderType());
    }
    assert !method.getAccessFlags().isPrivate();
    assert !method.getDefinition().isInstanceInitializer();
    if (kind == InvokeKind.SUPER) {
      return true;
    }
    if (kind == InvokeKind.STATIC) {
      return appView.appInfo().isStrictSubtypeOf(method.getHolderType(), target.holder);
    }
    if (kind == InvokeKind.VIRTUAL) {
      return false;
    }
    assert false : "Unexpected invoke-kind for visibility bridge: " + kind;
    return false;
  }

  public void run(ExecutorService executorService, Timing timing) throws ExecutionException {
    run(executorService, timing, null);
  }

  public void run(
      ExecutorService executorService,
      Timing timing,
      MemberRebindingIdentityLens memberRebindingIdentityLens)
      throws ExecutionException {
    assert memberRebindingIdentityLens == null
        || memberRebindingIdentityLens == appView.graphLens();

    timing.begin("Redundant bridge removal");

    // Collect all redundant bridges to remove.
    ProgramMethodSet bridgesToRemove = removeRedundantBridgesConcurrently(executorService);
    if (!bridgesToRemove.isEmpty()) {
      pruneApp(bridgesToRemove, executorService, timing);

      if (!lensBuilder.isEmpty()) {
        appView.rewriteWithLens(lensBuilder.build(appView), executorService, timing);
      }

      if (memberRebindingIdentityLens != null) {
        for (ProgramMethod bridgeToRemove : bridgesToRemove) {
          DexClassAndMethod resolvedMethod =
              appView
                  .appInfo()
                  .resolveMethodOn(bridgeToRemove.getHolder(), bridgeToRemove.getReference())
                  .getResolutionPair();
          memberRebindingIdentityLens.addNonReboundMethodReference(
              bridgeToRemove.getReference(), resolvedMethod.getReference());
        }
      }
    }
    appView.notifyOptimizationFinishedForTesting();
    appView.appInfo().notifyRedundantBridgeRemoverFinished(memberRebindingIdentityLens == null);
    timing.end();
  }

  private ProgramMethodSet removeRedundantBridgesConcurrently(ExecutorService executorService)
      throws ExecutionException {
    // Compute the strongly connected program components for parallelization.
    List<Set<DexProgramClass>> stronglyConnectedProgramComponents =
        new ProgramClassesBidirectedGraph(appView, immediateSubtypingInfo)
            .computeStronglyConnectedComponents();

    // Process the components concurrently.
    Collection<ProgramMethodSet> results =
        ThreadUtils.processItemsWithResultsThatMatches(
            stronglyConnectedProgramComponents,
            this::removeRedundantBridgesInComponent,
            removedBridges -> !removedBridges.isEmpty(),
            executorService);
    ProgramMethodSet removedBridges = ProgramMethodSet.create();
    results.forEach(
        result -> {
          removedBridges.addAll(result);
          result.clear();
        });
    return removedBridges;
  }

  private ProgramMethodSet removeRedundantBridgesInComponent(
      Set<DexProgramClass> stronglyConnectedProgramComponent) {
    // Remove bridges in a top-down traversal of the class hierarchy. This ensures that we don't map
    // an invoke to a removed bridge method to a method in the superclass hierarchy, which is then
    // also removed by bridge removal.
    RedundantBridgeRemoverClassHierarchyTraversal traversal =
        new RedundantBridgeRemoverClassHierarchyTraversal();
    traversal.run(stronglyConnectedProgramComponent);
    return traversal.getRemovedBridges();
  }

  private DexClassAndMethod getTargetForRedundantAbstractBridge(ProgramMethod method) {
    if (!method.getAccessFlags().isAbstract() || method.getDefinition().getCode() != null) {
      return null;
    }
    DexProgramClass holder = method.getHolder();
    if (holder.getSuperType() == null) {
      assert holder.getType() == appView.dexItemFactory().objectType;
      return null;
    }
    MethodResolutionResult superTypeResolution =
        appView.appInfo().resolveMethodOn(holder.getSuperType(), method.getReference(), false);
    if (superTypeResolution.isMultiMethodResolutionResult()) {
      return null;
    }
    // Check if there is a definition in the super type hieararchy that is also abstract and has the
    // same visibility.
    if (superTypeResolution.isSingleResolution()) {
      DexClassAndMethod resolvedMethod =
          superTypeResolution.asSingleResolution().getResolutionPair();
      if (resolvedMethod.getDefinition().isAbstract()
          && resolvedMethod
              .getDefinition()
              .isAtLeastAsVisibleAsOtherInSameHierarchy(method.getDefinition(), appView)
          && (!resolvedMethod.getHolder().isInterface() || holder.getInterfaces().isEmpty())) {
        return resolvedMethod;
      }
      return null;
    }
    // Only check for interfaces if resolving the method on super type causes NoSuchMethodError.
    FailedResolutionResult failedResolutionResult = superTypeResolution.asFailedResolution();
    if (failedResolutionResult == null
        || !failedResolutionResult.isNoSuchMethodErrorResult(holder, appView, appView.appInfo())
        || holder.getInterfaces().isEmpty()) {
      return null;
    }
    DexClassAndMethod representativeInterfaceMethod = null;
    for (DexType iface : holder.getInterfaces()) {
      SingleResolutionResult<?> singleIfaceResult =
          appView
              .appInfo()
              .resolveMethodOn(iface, method.getReference(), true)
              .asSingleResolution();
      if (singleIfaceResult == null
          || !singleIfaceResult.getResolvedMethod().isAbstract()
          || !singleIfaceResult
              .getResolvedMethod()
              .isAtLeastAsVisibleAsOtherInSameHierarchy(method.getDefinition(), appView)) {
        return null;
      }
      if (representativeInterfaceMethod == null) {
        representativeInterfaceMethod = singleIfaceResult.getResolutionPair();
      }
    }
    assert representativeInterfaceMethod != null;
    return representativeInterfaceMethod;
  }

  private void pruneApp(
      ProgramMethodSet bridgesToRemove, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    PrunedItems.Builder prunedItemsBuilder = PrunedItems.builder().setPrunedApp(appView.app());
    bridgesToRemove.forEach(method -> prunedItemsBuilder.addRemovedMethod(method.getReference()));
    appView.pruneItems(prunedItemsBuilder.build(), executorService, timing);
  }

  class RedundantBridgeRemoverClassHierarchyTraversal
      extends DepthFirstTopDownClassHierarchyTraversal {

    private final ProgramMethodSet removedBridges = ProgramMethodSet.create();

    RedundantBridgeRemoverClassHierarchyTraversal() {
      super(
          RedundantBridgeRemover.this.appView, RedundantBridgeRemover.this.immediateSubtypingInfo);
    }

    public ProgramMethodSet getRemovedBridges() {
      return removedBridges;
    }

    @Override
    public void visit(DexProgramClass clazz) {
      ProgramMethodSet bridgesToRemoveForClass = ProgramMethodSet.create();
      clazz.forEachProgramMethod(
          method -> {
            KeepMethodInfo keepInfo = appView.getKeepInfo(method);
            if (!keepInfo.isShrinkingAllowed(appView.options())
                || !keepInfo.isOptimizationAllowed(appView.options())) {
              return;
            }
            DexClassAndMethod target = getTargetForRedundantAbstractBridge(method);
            if (target == null) {
              target = getTargetForRedundantNonAbstractBridge(method);
              if (target == null) {
                return;
              }
            }

            // Rewrite invokes to the bridge to the target if it is accessible.
            if (canRetargetInvokesToTargetMethod(method, target)) {
              lensBuilder.map(method, target);
            } else if (mustRetargetInvokesToTargetMethod) {
              return;
            }

            // Record that the redundant bridge should be removed.
            bridgesToRemoveForClass.add(method);
          });
      if (!bridgesToRemoveForClass.isEmpty()) {
        clazz.getMethodCollection().removeMethods(bridgesToRemoveForClass.toDefinitionSet());
        removedBridges.addAll(bridgesToRemoveForClass);
      }
    }

    private boolean canRetargetInvokesToTargetMethod(
        ProgramMethod method, DexClassAndMethod target) {
      // Check if constructor retargeting is enabled.
      if (method.getDefinition().isInstanceInitializer()
          && !redundantBridgeRemovalOptions.isRetargetingOfConstructorBridgeCallsEnabled()) {
        return false;
      }
      // Check if the current method is an interface method targeted by invoke-super.
      if (method.getHolder().isInterface()
          && appView
              .appInfo()
              .getMethodAccessInfoCollection()
              .hasSuperInvoke(method.getReference())) {
        return false;
      }
      // Check if all possible contexts that have access to the holder of the redundant bridge
      // method also have access to the holder of the target method.
      DexProgramClass methodHolder = method.getHolder();
      DexClass targetHolder = target.getHolder();
      if (!targetHolder.getAccessFlags().isPublic()) {
        if (methodHolder.getAccessFlags().isPublic() || !method.isSamePackage(target)) {
          return false;
        }
      }
      // Check if all possible contexts that have access to the redundant bridge method also have
      // access to the target method.
      if (target.getAccessFlags().isPublic()) {
        return true;
      }
      MethodAccessFlags methodAccessFlags = method.getAccessFlags();
      MethodAccessFlags targetAccessFlags = target.getAccessFlags();
      if (methodAccessFlags.isPackagePrivate()
          && !targetAccessFlags.isPrivate()
          && method.isSamePackage(target)) {
        return true;
      }
      return methodAccessFlags.isProtected()
          && targetAccessFlags.isProtected()
          && method.isSamePackage(target);
    }

    @Override
    public void prune(DexProgramClass clazz) {
      // Empty.
    }
  }
}
