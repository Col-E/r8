// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class CheckAbstractClasses extends MultiClassPolicy {

  private final InternalOptions options;

  public CheckAbstractClasses(AppView<AppInfoWithLiveness> appView) {
    this.options = appView.options();
  }

  @Override
  public Collection<List<DexProgramClass>> apply(List<DexProgramClass> group) {
    if (options.canUseAbstractMethodOnNonAbstractClass()) {
      // We can just make the target class non-abstract if one of the classes in the group
      // is non-abstract.
      return Lists.<List<DexProgramClass>>newArrayList(group);
    }
    List<DexProgramClass> abstractClasses = new LinkedList<>();
    List<DexProgramClass> nonAbstractClasses = new LinkedList<>();
    for (DexProgramClass clazz : group) {
      if (clazz.isAbstract()) {
        abstractClasses.add(clazz);
      } else {
        nonAbstractClasses.add(clazz);
      }
    }
    List<List<DexProgramClass>> newGroups = new LinkedList<>();
    if (abstractClasses.size() > 1) {
      newGroups.add(abstractClasses);
    }
    if (nonAbstractClasses.size() > 1) {
      newGroups.add(nonAbstractClasses);
    }
    return newGroups;
  }
}
