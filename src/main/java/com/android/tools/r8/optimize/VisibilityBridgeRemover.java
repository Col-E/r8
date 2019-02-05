// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.optimize.InvokeSingleTargetExtractor.InvokeKind;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Set;

public class VisibilityBridgeRemover {

  private final AppView<? extends AppInfoWithLiveness> appView;

  public VisibilityBridgeRemover(AppView<? extends AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  private void removeUnneededVisibilityBridgesFromClass(DexProgramClass clazz) {
    clazz.setDirectMethods(removeUnneededVisibilityBridges(clazz.directMethods()));
    clazz.setVirtualMethods(removeUnneededVisibilityBridges(clazz.virtualMethods()));
  }

  private DexEncodedMethod[] removeUnneededVisibilityBridges(DexEncodedMethod[] methods) {
    Set<DexEncodedMethod> methodsToBeRemoved = null;
    for (DexEncodedMethod method : methods) {
      if (isUnneededVisibilityBridge(method)) {
        if (methodsToBeRemoved == null) {
          methodsToBeRemoved = Sets.newIdentityHashSet();
        }
        methodsToBeRemoved.add(method);
      }
    }
    if (methodsToBeRemoved != null) {
      Set<DexEncodedMethod> finalMethodsToBeRemoved = methodsToBeRemoved;
      return Arrays.stream(methods)
          .filter(method -> !finalMethodsToBeRemoved.contains(method))
          .toArray(DexEncodedMethod[]::new);
    }
    return methods;
  }

  private boolean isUnneededVisibilityBridge(DexEncodedMethod method) {
    if (appView.appInfo().isPinned(method.method)) {
      return false;
    }
    MethodAccessFlags accessFlags = method.accessFlags;
    if (!accessFlags.isBridge() || accessFlags.isAbstract()) {
      return false;
    }
    InvokeSingleTargetExtractor targetExtractor =
        new InvokeSingleTargetExtractor(appView.dexItemFactory());
    method.getCode().registerCodeReferences(targetExtractor);
    DexMethod target = targetExtractor.getTarget();
    InvokeKind kind = targetExtractor.getKind();
    // javac-generated visibility forward bridge method has same descriptor (name, signature and
    // return type).
    if (target != null && target.hasSameProtoAndName(method.method)) {
      assert !accessFlags.isPrivate() && !accessFlags.isConstructor();
      if (kind == InvokeKind.SUPER) {
        // This is a visibility forward, so check for the direct target.
        DexEncodedMethod targetMethod =
            appView.appInfo().resolveMethod(target.getHolder(), target).asSingleTarget();
        if (targetMethod != null && targetMethod.accessFlags.isPublic()) {
          if (Log.ENABLED) {
            Log.info(
                getClass(),
                "Removing visibility forwarding %s -> %s",
                method.method,
                targetMethod.method);
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
