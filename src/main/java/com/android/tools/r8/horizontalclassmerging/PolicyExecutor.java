// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * This is a simple policy executor that ensures regular sequential execution of policies. It should
 * primarily be readable and correct. The SimplePolicyExecutor should be a reference implementation,
 * against which more efficient policy executors can be compared.
 */
public class PolicyExecutor {

  private void applySingleClassPolicy(SingleClassPolicy policy, LinkedList<MergeGroup> groups) {
    Iterator<MergeGroup> i = groups.iterator();
    while (i.hasNext()) {
      MergeGroup group = i.next();
      boolean isInterfaceGroup = group.isInterfaceGroup();
      int previousGroupSize = group.size();
      group.removeIf(clazz -> !policy.canMerge(clazz));
      assert policy.recordRemovedClassesForDebugging(
          isInterfaceGroup, previousGroupSize, ImmutableList.of(group));
      if (group.isTrivial()) {
        i.remove();
      }
    }
  }

  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  private LinkedList<MergeGroup> applyMultiClassPolicy(
      MultiClassPolicy policy, LinkedList<MergeGroup> groups) {
    // For each group apply the multi class policy and add all the new groups together.
    LinkedList<MergeGroup> newGroups = new LinkedList<>();
    groups.forEach(
        group -> {
          boolean isInterfaceGroup = group.isInterfaceGroup();
          int previousGroupSize = group.size();
          Collection<MergeGroup> policyGroups = policy.apply(group);
          policyGroups.forEach(newGroup -> newGroup.applyMetadataFrom(group));
          assert policy.recordRemovedClassesForDebugging(
              isInterfaceGroup, previousGroupSize, policyGroups);
          newGroups.addAll(policyGroups);
        });
    return newGroups;
  }

  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  private <T> LinkedList<MergeGroup> applyMultiClassPolicyWithPreprocessing(
      MultiClassPolicyWithPreprocessing<T> policy,
      LinkedList<MergeGroup> groups,
      ExecutorService executorService)
      throws ExecutionException {
    // For each group apply the multi class policy and add all the new groups together.
    T data = policy.preprocess(groups, executorService);
    LinkedList<MergeGroup> newGroups = new LinkedList<>();
    groups.forEach(
        group -> {
          boolean isInterfaceGroup = group.isInterfaceGroup();
          int previousGroupSize = group.size();
          Collection<MergeGroup> policyGroups = policy.apply(group, data);
          policyGroups.forEach(newGroup -> newGroup.applyMetadataFrom(group));
          assert policy.recordRemovedClassesForDebugging(
              isInterfaceGroup, previousGroupSize, policyGroups);
          newGroups.addAll(policyGroups);
        });
    return newGroups;
  }

  /**
   * Given an initial collection of class groups which can potentially be merged, run all of the
   * policies registered to this policy executor on the class groups yielding a new collection of
   * class groups.
   */
  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  public Collection<MergeGroup> run(
      Collection<MergeGroup> inputGroups,
      Collection<Policy> policies,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
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
      if (policy.isSingleClassPolicy()) {
        applySingleClassPolicy(policy.asSingleClassPolicy(), linkedGroups);
      } else if (policy.isMultiClassPolicy()) {
        linkedGroups = applyMultiClassPolicy(policy.asMultiClassPolicy(), linkedGroups);
      } else {
        assert policy.isMultiClassPolicyWithPreprocessing();
        linkedGroups =
            applyMultiClassPolicyWithPreprocessing(
                policy.asMultiClassPolicyWithPreprocessing(), linkedGroups, executorService);
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
