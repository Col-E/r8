// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.utils;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class StronglyConnectedProgramClasses {

  /**
   * Computes the strongly connected components in the program class hierarchy (where extends and
   * implements edges are treated as bidirectional).
   */
  public static List<Set<DexProgramClass>> computeStronglyConnectedProgramClasses(
      AppView<AppInfoWithLiveness> appView, ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    Set<DexProgramClass> seen = Sets.newIdentityHashSet();
    List<Set<DexProgramClass>> stronglyConnectedComponents = new ArrayList<>();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (seen.contains(clazz)) {
        continue;
      }
      Set<DexProgramClass> stronglyConnectedComponent =
          internalComputeStronglyConnectedProgramClasses(clazz, immediateSubtypingInfo);
      stronglyConnectedComponents.add(stronglyConnectedComponent);
      seen.addAll(stronglyConnectedComponent);
    }
    return stronglyConnectedComponents;
  }

  private static Set<DexProgramClass> internalComputeStronglyConnectedProgramClasses(
      DexProgramClass clazz, ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    WorkList<DexProgramClass> worklist = WorkList.newIdentityWorkList(clazz);
    while (worklist.hasNext()) {
      DexProgramClass current = worklist.next();
      immediateSubtypingInfo.forEachImmediateProgramSuperClass(current, worklist::addIfNotSeen);
      worklist.addIfNotSeen(immediateSubtypingInfo.getSubclasses(current));
    }
    return worklist.getSeenSet();
  }
}
