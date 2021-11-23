// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public class LimitInterfaceGroups extends MultiClassPolicy {

  private final int maxGroupSize;

  public LimitInterfaceGroups(AppView<? extends AppInfoWithClassHierarchy> appView) {
    maxGroupSize = appView.options().horizontalClassMergerOptions().getMaxInterfaceGroupSize();
    assert maxGroupSize >= 0;
  }

  @Override
  public Collection<MergeGroup> apply(MergeGroup group) {
    if (group.isClassGroup()) {
      return Collections.singletonList(group);
    }
    // Mapping from new merge groups to their size.
    Map<MergeGroup, Integer> newGroups = new LinkedHashMap<>();
    for (DexProgramClass clazz : group) {
      processClass(clazz, newGroups);
    }
    return removeTrivialGroups(newGroups.keySet());
  }

  private void processClass(DexProgramClass clazz, Map<MergeGroup, Integer> newGroups) {
    int increment = clazz.getMethodCollection().size();

    // Find an existing group.
    for (Entry<MergeGroup, Integer> entry : newGroups.entrySet()) {
      MergeGroup candidateGroup = entry.getKey();
      int candidateGroupSize = entry.getValue();
      int newCandidateGroupSize = candidateGroupSize + increment;
      if (newCandidateGroupSize <= maxGroupSize) {
        candidateGroup.add(clazz);
        entry.setValue(newCandidateGroupSize);
        return;
      }
    }

    // Failed to find an existing group.
    newGroups.put(new MergeGroup(clazz), increment);
  }

  @Override
  public String getName() {
    return "LimitInterfaceGroups";
  }
}
