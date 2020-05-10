// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.optimize.InvokeSingleTargetExtractor.InvokeKind;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ForEachable;
import com.android.tools.r8.utils.IntBox;
import com.google.common.collect.Sets;
import java.util.Set;

public class VisibilityBridgeRemover {

  private final AppView<AppInfoWithLiveness> appView;

  public VisibilityBridgeRemover(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  private void removeUnneededVisibilityBridgesFromClass(DexProgramClass clazz) {
    DexEncodedMethod[] newDirectMethods =
        removeUnneededVisibilityBridges(
            clazz::forEachProgramDirectMethod, clazz.getMethodCollection().numberOfDirectMethods());
    if (newDirectMethods != null) {
      clazz.setDirectMethods(newDirectMethods);
    }
    DexEncodedMethod[] newVirtualMethods =
        removeUnneededVisibilityBridges(
            clazz::forEachProgramVirtualMethod,
            clazz.getMethodCollection().numberOfVirtualMethods());
    if (newVirtualMethods != null) {
      clazz.setVirtualMethods(newVirtualMethods);
    }
  }

  private DexEncodedMethod[] removeUnneededVisibilityBridges(
      ForEachable<ProgramMethod> methods, int size) {
    Set<DexEncodedMethod> methodsToBeRemoved = Sets.newIdentityHashSet();
    methods.forEach(
        method -> {
          if (isUnneededVisibilityBridge(method)) {
            methodsToBeRemoved.add(method.getDefinition());
          }
        });
    if (!methodsToBeRemoved.isEmpty()) {
      DexEncodedMethod[] newMethods = new DexEncodedMethod[size - methodsToBeRemoved.size()];
      IntBox i = new IntBox(0);
      methods.forEach(
          method -> {
            if (!methodsToBeRemoved.contains(method.getDefinition())) {
              newMethods[i.getAndIncrement()] = method.getDefinition();
            }
          });
      return newMethods;
    }
    return null;
  }

  private boolean isUnneededVisibilityBridge(ProgramMethod method) {
    if (appView.appInfo().isPinned(method.getReference())) {
      return false;
    }
    DexEncodedMethod definition = method.getDefinition();
    if (!definition.isBridge() || definition.isAbstract()) {
      return false;
    }
    InvokeSingleTargetExtractor targetExtractor =
        new InvokeSingleTargetExtractor(appView.dexItemFactory());
    method.registerCodeReferences(targetExtractor);
    DexMethod target = targetExtractor.getTarget();
    InvokeKind kind = targetExtractor.getKind();
    // javac-generated visibility forward bridge method has same descriptor (name, signature and
    // return type).
    if (target != null && target.hasSameProtoAndName(method.getReference())) {
      assert !definition.isPrivate() && !definition.isInstanceInitializer();
      if (kind == InvokeKind.SUPER) {
        // This is a visibility forward, so check for the direct target.
        DexEncodedMethod targetMethod =
            appView.appInfo().unsafeResolveMethodDueToDexFormat(target).getSingleTarget();
        if (targetMethod != null && targetMethod.accessFlags.isPublic()) {
          if (Log.ENABLED) {
            Log.info(
                getClass(), "Removing visibility forwarding %s -> %s", method, targetMethod.method);
          }
          return true;
        }
      }
    }
    return false;
  }

  public void run() {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      removeUnneededVisibilityBridgesFromClass(clazz);
    }
  }
}
