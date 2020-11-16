// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class LimitGroups extends MultiClassPolicy {

  private final int maxGroupSize;

  private LimitGroups(AppView<AppInfoWithLiveness> appView) {
    maxGroupSize = appView.options().horizontalClassMergingMaxGroupSize;
    assert maxGroupSize >= 2;
  }

  @Override
  public Collection<List<DexProgramClass>> apply(List<DexProgramClass> group) {
    if (group.size() <= maxGroupSize) {
      return Collections.singletonList(group);
    }

    List<List<DexProgramClass>> newGroups = new ArrayList<>();
    List<DexProgramClass> newGroup = createNewGroup(newGroups);
    for (DexProgramClass clazz : group) {
      if (newGroup.size() == maxGroupSize) {
        newGroup = createNewGroup(newGroups);
      }
      newGroup.add(clazz);
    }
    return newGroups;
  }

  private List<DexProgramClass> createNewGroup(List<List<DexProgramClass>> newGroups) {
    List<DexProgramClass> newGroup = new ArrayList<>();
    newGroups.add(newGroup);
    return newGroup;
  }
}
