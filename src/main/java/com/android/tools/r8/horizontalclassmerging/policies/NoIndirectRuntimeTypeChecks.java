// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;
import com.android.tools.r8.shaking.RuntimeTypeCheckInfo;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;

public class NoIndirectRuntimeTypeChecks extends MultiClassSameReferencePolicy<DexTypeList> {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final RuntimeTypeCheckInfo runtimeTypeCheckInfo;

  private final Reference2BooleanMap<DexType> cache = new Reference2BooleanOpenHashMap<>();

  public NoIndirectRuntimeTypeChecks(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      RuntimeTypeCheckInfo runtimeTypeCheckInfo) {
    this.appView = appView;
    this.runtimeTypeCheckInfo = runtimeTypeCheckInfo;
  }

  @Override
  public DexTypeList getMergeKey(DexProgramClass clazz) {
    // Require that classes that implement an interface that has a runtime type check (directly or
    // indirectly on a parent interface) are only merged with classes that implement the same
    // interfaces.
    return clazz
        .getInterfaces()
        .keepIf(this::computeInterfaceHasDirectOrIndirectRuntimeTypeCheck)
        .getSorted();
  }

  private boolean computeInterfaceHasDirectOrIndirectRuntimeTypeCheck(DexType type) {
    if (cache.containsKey(type)) {
      return cache.getBoolean(type);
    }
    DexClass clazz = appView.definitionFor(type);
    if (clazz == null || !clazz.isInterface()) {
      cache.put(type, true);
      return true;
    }
    if (!clazz.isProgramClass()) {
      // Conservatively return true because there could be a type check in the library.
      cache.put(type, true);
      return true;
    }
    if (runtimeTypeCheckInfo == null
        || runtimeTypeCheckInfo.isRuntimeCheckType(clazz.asProgramClass())) {
      cache.put(type, true);
      return true;
    }
    for (DexType parentType : clazz.getInterfaces()) {
      if (computeInterfaceHasDirectOrIndirectRuntimeTypeCheck(parentType)) {
        cache.put(type, true);
        return true;
      }
    }
    return false;
  }

  @Override
  public String getName() {
    return "NoIndirectRuntimeTypeChecks";
  }
}
