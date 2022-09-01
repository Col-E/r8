// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import static com.android.tools.r8.utils.ThreadUtils.processItems;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.optimize.InvokeSingleTargetExtractor.InvokeKind;
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

  private boolean isRedundantBridge(ProgramMethod method) {
    // Clean-up the predicate check.
    if (appView.appInfo().isPinned(method.getReference())) {
      return false;
    }
    DexEncodedMethod definition = method.getDefinition();
    // TODO(b/197490164): Remove if method is abstract.
    BridgeInfo bridgeInfo = definition.getOptimizationInfo().getBridgeInfo();
    boolean isBridge = definition.isBridge() || bridgeInfo != null;
    if (!isBridge || definition.isAbstract()) {
      return false;
    }
    InvokeSingleTargetExtractor targetExtractor = new InvokeSingleTargetExtractor(appView, method);
    method.registerCodeReferences(targetExtractor);
    DexMethod target = targetExtractor.getTarget();
    // javac-generated visibility forward bridge method has same descriptor (name, signature and
    // return type).
    if (target == null || !target.match(method.getReference())) {
      return false;
    }
    if (!isTargetingSuperMethod(method, targetExtractor.getKind(), target)) {
      return false;
    }
    // This is a visibility forward, so check for the direct target.
    ProgramMethod targetMethod =
        appView
            .appInfo()
            .unsafeResolveMethodDueToDexFormatLegacy(target)
            .getResolvedProgramMethod();
    if (targetMethod == null) {
      return false;
    }
    if (method.getAccessFlags().isPublic()) {
      if (!targetMethod.getAccessFlags().isPublic()) {
        return false;
      }
    } else {
      if (targetMethod.getAccessFlags().isProtected()
          && !targetMethod.getHolderType().isSamePackage(method.getHolderType())) {
        return false;
      }
      if (targetMethod.getAccessFlags().isPrivate()) {
        return false;
      }
    }
    if (definition.isStatic()
        && method.getHolder().hasClassInitializer()
        && method
            .getHolder()
            .classInitializationMayHaveSideEffectsInContext(appView, targetMethod)) {
      return false;
    }
    return true;
  }

  private boolean isTargetingSuperMethod(ProgramMethod method, InvokeKind kind, DexMethod target) {
    if (kind == InvokeKind.ILLEGAL) {
      return false;
    }
    if (kind == InvokeKind.DIRECT) {
      return method.getDefinition().isInstanceInitializer()
          && appView.options().canHaveNonReboundConstructorInvoke()
          && appView.testing().enableRedundantConstructorBridgeRemoval
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

  public void run(ExecutorService executorService) throws ExecutionException {
    // Collect all redundant bridges to remove.
    Map<DexProgramClass, ProgramMethodSet> bridgesToRemove =
        computeBridgesToRemove(executorService);
    pruneApp(bridgesToRemove, executorService);
  }

  private Map<DexProgramClass, ProgramMethodSet> computeBridgesToRemove(
      ExecutorService executorService) throws ExecutionException {
    Map<DexProgramClass, ProgramMethodSet> bridgesToRemove = new ConcurrentHashMap<>();
    processItems(
        appView.appInfo().classes(),
        clazz -> {
          ProgramMethodSet bridgesToRemoveForClass = ProgramMethodSet.create();
          clazz.forEachProgramMethod(
              method -> {
                if (isRedundantBridge(method)) {
                  bridgesToRemoveForClass.add(method);
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
