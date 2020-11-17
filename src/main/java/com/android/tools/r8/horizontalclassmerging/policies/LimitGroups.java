// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

public class LimitGroups extends MultiClassPolicy {

  private final int maxGroupSize;

  public LimitGroups(AppView<AppInfoWithLiveness> appView) {
    maxGroupSize = appView.options().horizontalClassMergingMaxGroupSize;
    assert maxGroupSize >= 2;
  }

  @Override
  public Collection<MergeGroup> apply(MergeGroup group) {
    if (group.size() <= maxGroupSize) {
      return Collections.singletonList(group);
    }

    LinkedList<MergeGroup> newGroups = new LinkedList<>();
    MergeGroup newGroup = createNewGroup(newGroups);
    for (DexProgramClass clazz : group) {
      if (newGroup.size() == maxGroupSize) {
        newGroup = createNewGroup(newGroups);
      }
      newGroup.add(clazz);
    }
    if (newGroup.size() == 1) {
      if (maxGroupSize == 2) {
        MergeGroup removedGroup = newGroups.removeLast();
        assert removedGroup == newGroup;
      } else {
        newGroup.add(newGroups.getFirst().removeLast());
      }
    }
    return newGroups;
  }

  private MergeGroup createNewGroup(LinkedList<MergeGroup> newGroups) {
    MergeGroup newGroup = new MergeGroup();
    newGroups.add(newGroup);
    return newGroup;
  }
}
