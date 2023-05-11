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
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class RedundantBridgeRemover {

  private final AppView<AppInfoWithLiveness> appView;
  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;
  private final RedundantBridgeRemovalOptions redundantBridgeRemovalOptions;

  private final InvokedReflectivelyFromPlatformAnalysis invokedReflectivelyFromPlatformAnalysis =
      new InvokedReflectivelyFromPlatformAnalysis();
  private final RedundantBridgeRemovalLens.Builder lensBuilder =
      new RedundantBridgeRemovalLens.Builder();

  public RedundantBridgeRemover(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.immediateSubtypingInfo = ImmediateProgramSubtypingInfo.create(appView);
    this.redundantBridgeRemovalOptions =
        appView.options().getRedundantBridgeRemovalOptions().ensureInitialized();
  }

  private DexClassAndMethod getTargetForRedundantBridge(ProgramMethod method) {
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
    if (invokedReflectivelyFromPlatformAnalysis.isMaybeInvokedReflectivelyFromPlatform(method)) {
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

  public void run(
      MemberRebindingIdentityLens memberRebindingIdentityLens, ExecutorService executorService)
      throws ExecutionException {
    assert memberRebindingIdentityLens == null
        || memberRebindingIdentityLens == appView.graphLens();

    // Collect all redundant bridges to remove.
    ProgramMethodSet bridgesToRemove = removeRedundantBridgesConcurrently(executorService);
    if (bridgesToRemove.isEmpty()) {
      return;
    }

    pruneApp(bridgesToRemove, executorService);

    if (!lensBuilder.isEmpty()) {
      appView.setGraphLens(lensBuilder.build(appView));
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

  private boolean isRedundantAbstractBridge(ProgramMethod method) {
    if (!method.getAccessFlags().isAbstract() || method.getDefinition().getCode() != null) {
      return false;
    }
    DexProgramClass holder = method.getHolder();
    if (holder.getSuperType() == null) {
      assert holder.getType() == appView.dexItemFactory().objectType;
      return false;
    }
    MethodResolutionResult superTypeResolution =
        appView.appInfo().resolveMethodOn(holder.getSuperType(), method.getReference(), false);
    if (superTypeResolution.isMultiMethodResolutionResult()) {
      return false;
    }
    // Check if there is a definition in the super type hieararchy that is also abstract and has the
    // same visibility.
    if (superTypeResolution.isSingleResolution()) {
      DexClassAndMethod resolutionPair =
          superTypeResolution.asSingleResolution().getResolutionPair();
      return resolutionPair.getDefinition().isAbstract()
          && resolutionPair
              .getDefinition()
              .isAtLeastAsVisibleAsOtherInSameHierarchy(method.getDefinition(), appView)
          && (!resolutionPair.getHolder().isInterface() || holder.getInterfaces().isEmpty());
    }
    // Only check for interfaces if resolving the method on super type causes NoSuchMethodError.
    FailedResolutionResult failedResolutionResult = superTypeResolution.asFailedResolution();
    if (failedResolutionResult == null
        || !failedResolutionResult.isNoSuchMethodErrorResult(holder, appView, appView.appInfo())
        || holder.getInterfaces().isEmpty()) {
      return false;
    }
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
        return false;
      }
    }
    return true;
  }

  private void pruneApp(ProgramMethodSet bridgesToRemove, ExecutorService executorService)
      throws ExecutionException {
    PrunedItems.Builder prunedItemsBuilder = PrunedItems.builder().setPrunedApp(appView.app());
    bridgesToRemove.forEach(method -> prunedItemsBuilder.addRemovedMethod(method.getReference()));
    appView.pruneItems(prunedItemsBuilder.build(), executorService);
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
            if (isRedundantAbstractBridge(method)) {
              // Record that the redundant bridge should be removed.
              bridgesToRemoveForClass.add(method);
              return;
            }
            DexClassAndMethod target = getTargetForRedundantBridge(method);
            if (target != null) {
              // Record that the redundant bridge should be removed.
              bridgesToRemoveForClass.add(method);

              // Rewrite invokes to the bridge to the target if it is accessible.
              // TODO(b/245882297): Refine these visibility checks so that we also rewrite when
              //  the target is not public, but still accessible to call sites.
              boolean isEligibleForRetargeting =
                  redundantBridgeRemovalOptions.isRetargetingOfConstructorBridgeCallsEnabled()
                      || !method.getDefinition().isInstanceInitializer();
              if (isEligibleForRetargeting
                  && target.getAccessFlags().isPublic()
                  && target.getHolder().isPublic()) {
                lensBuilder.map(method, target);
              }
            }
          });
      if (!bridgesToRemoveForClass.isEmpty()) {
        clazz.getMethodCollection().removeMethods(bridgesToRemoveForClass.toDefinitionSet());
        removedBridges.addAll(bridgesToRemoveForClass);
      }
    }

    @Override
    public void prune(DexProgramClass clazz) {
      // Empty.
    }
  }

  class InvokedReflectivelyFromPlatformAnalysis {

    // Maps each class to a boolean indicating if the class inherits from android.app.Fragment or
    // android.app.ZygotePreload.
    private final Map<DexClass, Boolean> cache = new ConcurrentHashMap<>();

    boolean isMaybeInvokedReflectivelyFromPlatform(ProgramMethod method) {
      return method.getDefinition().isDefaultInstanceInitializer()
          && !method.getHolder().isAbstract()
          && computeIsPlatformReflectingOnDefaultConstructor(method.getHolder());
    }

    private boolean computeIsPlatformReflectingOnDefaultConstructor(DexProgramClass clazz) {
      Boolean cacheResult = cache.get(clazz);
      if (cacheResult != null) {
        return cacheResult;
      }
      WorkList.<WorklistItem>newIdentityWorkList(new NotProcessedWorklistItem(clazz))
          .process(WorklistItem::accept);
      assert cache.containsKey(clazz);
      return cache.get(clazz);
    }

    abstract class WorklistItem implements Consumer<WorkList<WorklistItem>> {

      protected final DexClass clazz;

      WorklistItem(DexClass clazz) {
        this.clazz = clazz;
      }

      Iterable<DexClass> getImmediateSupertypes() {
        return IterableUtils.flatMap(
            clazz.allImmediateSupertypes(),
            supertype -> {
              DexClass definition = appView.definitionFor(supertype);
              return definition != null
                  ? Collections.singletonList(definition)
                  : Collections.emptyList();
            });
      }
    }

    class NotProcessedWorklistItem extends WorklistItem {

      NotProcessedWorklistItem(DexClass clazz) {
        super(clazz);
      }

      @Override
      public void accept(WorkList<WorklistItem> worklist) {
        // Enqueue a worklist item to process the current class after processing its super classes.
        worklist.addFirstIgnoringSeenSet(new ProcessedWorklistItem(clazz));
        // Enqueue all superclasses for processing.
        for (DexClass supertype : getImmediateSupertypes()) {
          if (!cache.containsKey(supertype)) {
            worklist.addFirstIgnoringSeenSet(new NotProcessedWorklistItem(supertype));
          }
        }
      }
    }

    class ProcessedWorklistItem extends WorklistItem {

      ProcessedWorklistItem(DexClass clazz) {
        super(clazz);
      }

      @Override
      public void accept(WorkList<WorklistItem> worklist) {
        cache.put(
            clazz,
            Iterables.any(
                getImmediateSupertypes(),
                supertype ->
                    cache.get(supertype)
                        || (supertype.isLibraryClass()
                            && redundantBridgeRemovalOptions
                                .isPlatformReflectingOnDefaultConstructorInSubclasses(
                                    supertype.asLibraryClass()))));
      }
    }
  }
}
