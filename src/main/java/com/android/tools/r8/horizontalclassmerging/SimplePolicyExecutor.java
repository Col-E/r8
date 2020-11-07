// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.DexProgramClass;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This is a simple policy executor that ensures regular sequential execution of policies. It should
 * primarily be readable and correct. The SimplePolicyExecutor should be a reference implementation,
 * against which more efficient policy executors can be compared.
 */
public class SimplePolicyExecutor extends PolicyExecutor {

  // TODO(b/165506334): if performing mutable operation ensure that linked lists are used
  private LinkedList<List<DexProgramClass>> applySingleClassPolicy(
      SingleClassPolicy policy, LinkedList<List<DexProgramClass>> groups) {
    Iterator<List<DexProgramClass>> i = groups.iterator();
    while (i.hasNext()) {
      Collection<DexProgramClass> group = i.next();
      int previousNumberOfClasses = group.size();
      group.removeIf(clazz -> !policy.canMerge(clazz));
      policy.numberOfRemovedClasses += previousNumberOfClasses - group.size();
      if (group.size() < 2) {
        i.remove();
      }
    }
    return groups;
  }

  private LinkedList<List<DexProgramClass>> applyMultiClassPolicy(
      MultiClassPolicy policy, LinkedList<List<DexProgramClass>> groups) {
    // For each group apply the multi class policy and add all the new groups together.
    return groups.stream()
        .flatMap(group -> policy.apply(group).stream())
        .collect(Collectors.toCollection(LinkedList::new));
  }

  @Override
  public Collection<List<DexProgramClass>> run(
      Collection<List<DexProgramClass>> inputGroups, Collection<Policy> policies) {
    LinkedList<List<DexProgramClass>> linkedGroups;

    if (inputGroups instanceof LinkedList) {
      linkedGroups = (LinkedList<List<DexProgramClass>>) inputGroups;
    } else {
      linkedGroups = new LinkedList<>(inputGroups);
    }

    for (Policy policy : policies) {
      if (policy.shouldSkipPolicy()) {
        continue;
      }

      if (policy instanceof SingleClassPolicy) {
        linkedGroups = applySingleClassPolicy((SingleClassPolicy) policy, linkedGroups);
      } else if (policy instanceof MultiClassPolicy) {
        linkedGroups = applyMultiClassPolicy((MultiClassPolicy) policy, linkedGroups);
      }

      // Any policy should not return any trivial groups.
      assert linkedGroups.stream().allMatch(group -> group.size() >= 2);
    }

    return linkedGroups;
  }
}
