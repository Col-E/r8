// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.BottomUpClassHierarchyTraversal;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import java.util.IdentityHashMap;
import java.util.Map;

public class LibraryMethodOverrideAnalysis {

  private final AppView<? extends AppInfoWithSubtyping> appView;

  // A map that for each type specifies if the type may have a method that overrides a library
  // method (conservatively).
  //
  // Note that we intentionally use a Map instead of a Set, such that we can distinguish the "may
  // definitely not have a library method override" (which arises when a type is mapped to FALSE)
  // from the "unknown" case (which arises when there is a type that is not a key in the map).
  private final Map<DexType, Boolean> mayHaveLibraryMethodOverride = new IdentityHashMap<>();

  public LibraryMethodOverrideAnalysis(AppView<? extends AppInfoWithSubtyping> appView) {
    this.appView = appView;
    initializeMayHaveLibraryMethodOverride();
  }

  private void initializeMayHaveLibraryMethodOverride() {
    BottomUpClassHierarchyTraversal.forProgramClasses(appView)
        .visit(
            appView.appInfo().classes(),
            clazz ->
                mayHaveLibraryMethodOverride.put(
                    clazz.type,
                    mayHaveLibraryMethodOverrideDirectly(clazz)
                        || mayHaveLibraryMethodOverrideIndirectly(clazz)));
  }

  private boolean mayHaveLibraryMethodOverrideDirectly(DexProgramClass clazz) {
    for (DexEncodedMethod method : clazz.virtualMethods()) {
      if (method.isLibraryMethodOverride().isPossiblyTrue()) {
        return true;
      }
    }
    return false;
  }

  private boolean mayHaveLibraryMethodOverrideIndirectly(DexProgramClass clazz) {
    for (DexType subtype : appView.appInfo().allImmediateSubtypes(clazz.type)) {
      // If we find a subtype that is not in the mapping, we conservatively record that the current
      // class could have a method that overrides a library method.
      if (mayHaveLibraryMethodOverride.getOrDefault(subtype, Boolean.TRUE)) {
        return true;
      }
    }
    return false;
  }
}
