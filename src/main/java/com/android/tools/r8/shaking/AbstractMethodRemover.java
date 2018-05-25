// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.logging.Log;
import java.util.ArrayList;
import java.util.List;

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

  private final AppInfoWithSubtyping appInfo;
  private ScopedDexMethodSet scope = new ScopedDexMethodSet();

  public AbstractMethodRemover(AppInfoWithSubtyping appInfo) {
    this.appInfo = appInfo;
  }

  public void run() {
    assert scope.getParent() == null;
    processClass(appInfo.dexItemFactory.objectType);
  }

  private void processClass(DexType type) {
    DexClass holder = appInfo.definitionFor(type);
    scope = scope.newNestedScope();
    if (holder != null && !holder.isLibraryClass()) {
      holder.setVirtualMethods(processMethods(holder.virtualMethods()));
    }
    type.forAllExtendsSubtypes(this::processClass);
    scope = scope.getParent();
  }

  private DexEncodedMethod[] processMethods(DexEncodedMethod[] virtualMethods) {
    if (virtualMethods == null) {
      return null;
    }
    // Removal of abstract methods is rare, so avoid copying the array until we find one.
    List<DexEncodedMethod> methods = null;
    for (int i = 0; i < virtualMethods.length; i++) {
      DexEncodedMethod method = virtualMethods[i];
      if (scope.addMethodIfMoreVisible(method) || !method.accessFlags.isAbstract()) {
        if (methods != null) {
          methods.add(method);
        }
      } else {
        if (methods == null) {
          methods = new ArrayList<>(virtualMethods.length - 1);
          for (int j = 0; j < i; j++) {
            methods.add(virtualMethods[j]);
          }
        }
        if (Log.ENABLED) {
          Log.debug(getClass(), "Removing abstract method %s.", method.method);
        }
      }
    }
    return methods == null ? virtualMethods : methods.toArray(new DexEncodedMethod[methods.size()]);
  }

}
