// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import static com.android.tools.r8.utils.ThreadUtils.processItems;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.optimize.InvokeSingleTargetExtractor.InvokeKind;
import com.android.tools.r8.optimize.redundantbridgeremoval.RedundantBridgeRemovalLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class RedundantBridgeRemover {

  private final AppView<AppInfoWithLiveness> appView;

  public RedundantBridgeRemover(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  private DexClassAndMethod getTargetForRedundantBridge(ProgramMethod method) {
    // Clean-up the predicate check.
    if (appView.appInfo().isPinned(method.getReference())) {
      return null;
    }
    DexEncodedMethod definition = method.getDefinition();
    // TODO(b/197490164): Remove if method is abstract.
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
    if (method.getAccessFlags().isPublic()) {
      if (!targetMethod.getAccessFlags().isPublic()) {
        return null;
      }
    } else {
      if (targetMethod.getAccessFlags().isProtected()
          && !targetMethod.getHolderType().isSamePackage(method.getHolderType())) {
        return null;
      }
      if (targetMethod.getAccessFlags().isPrivate()) {
        return null;
      }
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
    assert false : "Unexpected invoke-kind for visibility bridge: " + kind;
    return false;
  }

  public void run(
      MemberRebindingIdentityLens memberRebindingIdentityLens, ExecutorService executorService)
      throws ExecutionException {
    assert memberRebindingIdentityLens == null
        || memberRebindingIdentityLens == appView.graphLens();

    // Collect all redundant bridges to remove.
    RedundantBridgeRemovalLens.Builder lensBuilder = new RedundantBridgeRemovalLens.Builder();
    Map<DexProgramClass, ProgramMethodSet> bridgesToRemove =
        computeBridgesToRemove(lensBuilder, executorService);
    if (bridgesToRemove.isEmpty()) {
      return;
    }

    pruneApp(bridgesToRemove, executorService);

    if (!lensBuilder.isEmpty()) {
      appView.setGraphLens(lensBuilder.build(appView));
    }

    if (memberRebindingIdentityLens != null) {
      for (ProgramMethodSet bridgesToRemoveFromClass : bridgesToRemove.values()) {
        for (ProgramMethod bridgeToRemove : bridgesToRemoveFromClass) {
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
  }

  private Map<DexProgramClass, ProgramMethodSet> computeBridgesToRemove(
      RedundantBridgeRemovalLens.Builder lensBuilder, ExecutorService executorService)
      throws ExecutionException {
    Map<DexProgramClass, ProgramMethodSet> bridgesToRemove = new ConcurrentHashMap<>();
    processItems(
        appView.appInfo().classes(),
        clazz -> {
          ProgramMethodSet bridgesToRemoveForClass = ProgramMethodSet.create();
          clazz.forEachProgramMethod(
              method -> {
                DexClassAndMethod target = getTargetForRedundantBridge(method);
                if (target != null) {
                  // Record that the redundant bridge should be removed.
                  bridgesToRemoveForClass.add(method);

                  // Rewrite invokes to the bridge to the target if it is accessible.
                  // TODO(b/173751869): Consider enabling this for constructors as well.
                  // TODO(b/245882297): Refine these visibility checks so that we also rewrite when
                  //  the target is not public, but still accessible to call sites.
                  boolean isEligibleForRetargeting =
                      appView.testing().enableRetargetingConstructorBridgeCalls
                          || !method.getDefinition().isInstanceInitializer();
                  if (isEligibleForRetargeting
                      && target.getAccessFlags().isPublic()
                      && target.getHolder().isPublic()) {
                    lensBuilder.map(method, target);
                  }
                }
              });
          if (!bridgesToRemoveForClass.isEmpty()) {
            bridgesToRemove.put(clazz, bridgesToRemoveForClass);
          }
        },
        executorService);
    return bridgesToRemove;
  }

  private void pruneApp(
      Map<DexProgramClass, ProgramMethodSet> bridgesToRemove, ExecutorService executorService)
      throws ExecutionException {
    PrunedItems.Builder prunedItemsBuilder = PrunedItems.builder().setPrunedApp(appView.app());
    bridgesToRemove.forEach(
        (clazz, methods) -> {
          clazz.getMethodCollection().removeMethods(methods.toDefinitionSet());
          methods.forEach(method -> prunedItemsBuilder.addRemovedMethod(method.getReference()));
        });
    appView.pruneItems(prunedItemsBuilder.build(), executorService);
  }
}
