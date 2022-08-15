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
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.optimize.InvokeSingleTargetExtractor.InvokeKind;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class VisibilityBridgeRemover {

  private final AppView<AppInfoWithLiveness> appView;

  public VisibilityBridgeRemover(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  private boolean isUnneededVisibilityBridge(ProgramMethod method) {
    // Clean-up the predicate check.
    if (appView.appInfo().isPinned(method.getReference())) {
      return false;
    }
    DexEncodedMethod definition = method.getDefinition();
    // TODO(b/198133259): Extend to definitions that are not defined as bridges.
    // TODO(b/197490164): Remove if method is abstract.
    if (!definition.isBridge() || definition.isAbstract()) {
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
    assert !definition.isPrivate() && !definition.isInstanceInitializer();
    if (!isTargetingSuperMethod(method, targetExtractor.getKind(), target)) {
      return false;
    }
    // This is a visibility forward, so check for the direct target.
    ProgramMethod targetMethod =
        appView
            .appInfo()
            .unsafeResolveMethodDueToDexFormatLegacy(target)
            .getResolvedProgramMethod();
    if (targetMethod == null || !targetMethod.getAccessFlags().isPublic()) {
      return false;
    }
    if (definition.isStatic()
        && method.getHolder().hasClassInitializer()
        && method
            .getHolder()
            .classInitializationMayHaveSideEffectsInContext(appView, targetMethod)) {
      return false;
    }
    if (Log.ENABLED) {
      Log.info(
          getClass(),
          "Removing visibility forwarding %s -> %s",
          method,
          targetMethod.getReference());
    }
    return true;
  }

  private boolean isTargetingSuperMethod(ProgramMethod method, InvokeKind kind, DexMethod target) {
    if (kind == InvokeKind.ILLEGAL) {
      return false;
    }
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
    // Collect all visibility bridges to remove.
    if (!appView.options().enableVisibilityBridgeRemoval) {
      return;
    }
    ConcurrentHashMap<DexProgramClass, Set<DexEncodedMethod>> visibilityBridgesToRemove =
        new ConcurrentHashMap<>();
    processItems(
        appView.appInfo().classes(),
        clazz -> {
          Set<DexEncodedMethod> bridgesToRemoveForClass = Sets.newIdentityHashSet();
          clazz.forEachProgramMethod(
              method -> {
                if (isUnneededVisibilityBridge(method)) {
                  bridgesToRemoveForClass.add(method.getDefinition());
                }
              });
          if (!bridgesToRemoveForClass.isEmpty()) {
            visibilityBridgesToRemove.put(clazz, bridgesToRemoveForClass);
          }
        },
        executorService);
    // Remove all bridges found.
    PrunedItems.Builder builder = PrunedItems.builder();
    visibilityBridgesToRemove.forEach(
        (clazz, methods) -> {
          clazz.getMethodCollection().removeMethods(methods);
          methods.forEach(method -> builder.addRemovedMethod(method.getReference()));
        });
  }
}
