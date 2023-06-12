// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.shaking.ScopedDexMethodSet.AddMethodIfMoreVisibleResult;
import java.util.HashSet;
import java.util.Set;

/**
 * Removes abstract methods if they only shadow methods of the same signature in a superclass.
 * <p>
 * We do not consider classes from the library for this optimization, as the program might run
 * against a different version of the library where methods are missing.
 * <p>
 * This optimization is beneficial mostly as it removes superfluous abstract methods that are
 * created by the {@link TreePruner}.
 */
public class AbstractMethodRemover {

  private final AppView<AppInfoWithLiveness> appView;
  private final SubtypingInfo subtypingInfo;
  private ScopedDexMethodSet scope = new ScopedDexMethodSet();

  public AbstractMethodRemover(AppView<AppInfoWithLiveness> appView, SubtypingInfo subtypingInfo) {
    this.appView = appView;
    this.subtypingInfo = subtypingInfo;
  }

  public void run() {
    assert scope.getParent() == null;
    processClass(appView.dexItemFactory().objectType);
    appView.notifyOptimizationFinishedForTesting();
  }

  private void processClass(DexType type) {
    DexClass holder = appView.definitionFor(type);
    scope = scope.newNestedScope();
    if (holder != null && holder.isProgramClass()) {
      processMethods(holder.asProgramClass());
    }
    // TODO(b/154881041): Does this need the full subtype hierarchy of referenced types!?
    subtypingInfo.forAllImmediateExtendsSubtypes(type, this::processClass);
    scope = scope.getParent();
  }

  private void processMethods(DexProgramClass clazz) {
    Set<DexEncodedMethod> toRemove = null;
    for (ProgramMethod method : clazz.virtualProgramMethods()) {
      if (!isNonAbstractPinnedOrWideningVisibility(method)) {
        if (toRemove == null) {
          toRemove = new HashSet<>();
        }
        toRemove.add(method.getDefinition());
      }
    }
    if (toRemove != null) {
      clazz.getMethodCollection().removeMethods(toRemove);
    }
  }

  private boolean isNonAbstractPinnedOrWideningVisibility(ProgramMethod method) {
    if (!method.getAccessFlags().isAbstract()) {
      return true;
    }
    // Check if the method widens visibility. Adding to the scope mutates it.
    if (scope.addMethodIfMoreVisible(method.getDefinition())
        != AddMethodIfMoreVisibleResult.NOT_ADDED) {
      return true;
    }
    if (appView.appInfo().isPinned(method)) {
      return true;
    }
    // We will filter the method out since it is not pinned.
    return false;
  }

}
