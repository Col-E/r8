// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class LimitGroups extends MultiClassPolicy {

  private final int maxGroupSize;

  public LimitGroups(AppView<AppInfoWithLiveness> appView) {
    maxGroupSize = appView.options().horizontalClassMergingMaxGroupSize;
    assert maxGroupSize >= 2;
  }

  @Override
  public Collection<? extends List<DexProgramClass>> apply(List<DexProgramClass> group) {
    if (group.size() <= maxGroupSize) {
      return Collections.singletonList(group);
    }

    LinkedList<LinkedList<DexProgramClass>> newGroups = new LinkedList<>();
    List<DexProgramClass> newGroup = createNewGroup(newGroups);
    for (DexProgramClass clazz : group) {
      if (newGroup.size() == maxGroupSize) {
        newGroup = createNewGroup(newGroups);
      }
      newGroup.add(clazz);
    }
    if (newGroup.size() == 1) {
      if (maxGroupSize == 2) {
        List<DexProgramClass> removedGroup = newGroups.removeLast();
        assert removedGroup == newGroup;
      } else {
        newGroup.add(newGroups.getFirst().removeLast());
      }
    }
    return newGroups;
  }

  private LinkedList<DexProgramClass> createNewGroup(
      LinkedList<LinkedList<DexProgramClass>> newGroups) {
    LinkedList<DexProgramClass> newGroup = new LinkedList<>();
    newGroups.add(newGroup);
    return newGroup;
  }
}
