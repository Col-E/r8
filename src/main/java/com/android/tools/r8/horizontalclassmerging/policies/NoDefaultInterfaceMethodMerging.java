// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * For interfaces, we cannot introduce an instance field `int $r8$classId`. Therefore, we can't
 * merge two interfaces that declare the same default interface method.
 *
 * <p>This policy attempts to split a merge group consisting of interfaces into smaller merge groups
 * such that each pairs of interfaces in each merge group does not have conflicting default
 * interface methods.
 */
public class NoDefaultInterfaceMethodMerging extends MultiClassPolicy {

  private final Mode mode;
  private final InternalOptions options;

  public NoDefaultInterfaceMethodMerging(AppView<?> appView, Mode mode) {
    this.mode = mode;
    this.options = appView.options();
  }

  @Override
  public Collection<MergeGroup> apply(MergeGroup group) {
    if (!group.isInterfaceGroup()) {
      return ListUtils.newLinkedList(group);
    }

    // Split the group into smaller groups such that no default methods collide.
    Map<MergeGroup, DexMethodSignatureSet> newGroups = new LinkedHashMap<>();
    for (DexProgramClass clazz : group) {
      addClassToGroup(clazz, newGroups);
    }

    return removeTrivialGroups(Lists.newLinkedList(newGroups.keySet()));
  }

  private void addClassToGroup(
      DexProgramClass clazz, Map<MergeGroup, DexMethodSignatureSet> newGroups) {
    DexMethodSignatureSet classSignatures = DexMethodSignatureSet.create();
    classSignatures.addAllMethods(clazz.virtualMethods(DexEncodedMethod::isDefaultMethod));

    // Find a group that does not have any collisions with `clazz`.
    for (Entry<MergeGroup, DexMethodSignatureSet> entry : newGroups.entrySet()) {
      MergeGroup group = entry.getKey();
      DexMethodSignatureSet groupSignatures = entry.getValue();
      if (!groupSignatures.containsAnyOf(classSignatures)) {
        groupSignatures.addAll(classSignatures);
        group.add(clazz);
        return;
      }
    }

    // Else create a new group.
    newGroups.put(new MergeGroup(clazz), classSignatures);
  }

  @Override
  public String getName() {
    return "NoDefaultInterfaceMethodMerging";
  }

  @Override
  public boolean shouldSkipPolicy() {
    return !options.horizontalClassMergerOptions().isInterfaceMergingEnabled(mode);
  }
}
