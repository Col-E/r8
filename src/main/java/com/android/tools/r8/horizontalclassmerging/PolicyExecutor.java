// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.Timing;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * This is a simple policy executor that ensures regular sequential execution of policies. It should
 * primarily be readable and correct. The SimplePolicyExecutor should be a reference implementation,
 * against which more efficient policy executors can be compared.
 */
public class PolicyExecutor {

  // TODO(b/165506334): if performing mutable operation ensure that linked lists are used
  private void applySingleClassPolicy(SingleClassPolicy policy, LinkedList<MergeGroup> groups) {
    Iterator<MergeGroup> i = groups.iterator();
    while (i.hasNext()) {
      MergeGroup group = i.next();
      int previousNumberOfClasses = group.size();
      group.removeIf(clazz -> !policy.canMerge(clazz));
      policy.numberOfRemovedClasses += previousNumberOfClasses - group.size();
      if (group.size() < 2) {
        i.remove();
      }
    }
  }

  private LinkedList<MergeGroup> applyMultiClassPolicy(
      MultiClassPolicy policy, LinkedList<MergeGroup> groups) {
    // For each group apply the multi class policy and add all the new groups together.
    LinkedList<MergeGroup> newGroups = new LinkedList<>();
    groups.forEach(
        group -> {
          int previousNumberOfClasses = group.size();
          Collection<MergeGroup> policyGroups = policy.apply(group);
          policyGroups.forEach(newGroup -> newGroup.applyMetadataFrom(group));
          policy.numberOfRemovedClasses +=
              previousNumberOfClasses - IterableUtils.sumInt(policyGroups, MergeGroup::size);
          newGroups.addAll(policyGroups);
        });
    return newGroups;
  }

  /**
   * Given an initial collection of class groups which can potentially be merged, run all of the
   * policies registered to this policy executor on the class groups yielding a new collection of
   * class groups.
   */
  public Collection<MergeGroup> run(
      Collection<MergeGroup> inputGroups, Collection<Policy> policies, Timing timing) {
    LinkedList<MergeGroup> linkedGroups;

    if (inputGroups instanceof LinkedList) {
      linkedGroups = (LinkedList<MergeGroup>) inputGroups;
    } else {
      linkedGroups = new LinkedList<>(inputGroups);
    }

    for (Policy policy : policies) {
      if (policy.shouldSkipPolicy()) {
        continue;
      }

      timing.begin(policy.getName());
      if (policy instanceof SingleClassPolicy) {
        applySingleClassPolicy((SingleClassPolicy) policy, linkedGroups);
      } else {
        assert policy instanceof MultiClassPolicy;
        linkedGroups = applyMultiClassPolicy((MultiClassPolicy) policy, linkedGroups);
      }
      timing.end();

      policy.clear();

      if (linkedGroups.isEmpty()) {
        break;
      }

      // Any policy should not return any trivial groups.
      assert linkedGroups.stream().allMatch(group -> group.size() >= 2);
    }

    return linkedGroups;
  }
}
