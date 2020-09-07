// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.DexProgramClass;
import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Collectors;

/**
 * This is a simple policy executor that ensures regular sequential execution of policies. It should
 * primarily be readable and correct. The SimplePolicyExecutor should be a reference implementation,
 * against which more efficient policy executors can be compared.
 */
public class SimplePolicyExecutor extends PolicyExecutor {
  public SimplePolicyExecutor(Collection<Policy> policies) {
    super(policies);
  }

  // TODO(b/165506334): if performing mutable operation ensure that linked lists are used
  private Collection<Collection<DexProgramClass>> applySingleClassPolicy(
      SingleClassPolicy policy, Collection<Collection<DexProgramClass>> groups) {
    Iterator<Collection<DexProgramClass>> i = groups.iterator();
    while (i.hasNext()) {
      Collection<DexProgramClass> group = i.next();
      Iterator<DexProgramClass> j = group.iterator();
      while (j.hasNext()) {
        DexProgramClass clazz = j.next();
        if (!policy.canMerge(clazz)) {
          j.remove();
        }
      }
      if (group.size() < 2) {
        i.remove();
      }
    }
    return groups;
  }

  private Collection<Collection<DexProgramClass>> applyMultiClassPolicy(
      MultiClassPolicy policy, Collection<Collection<DexProgramClass>> groups) {
    // For each group apply the multi class policy and add all the new groups together.
    return groups.stream()
        .flatMap(group -> policy.apply(group).stream())
        .collect(Collectors.toList());
  }

  @Override
  public Collection<Collection<DexProgramClass>> run(
      Collection<Collection<DexProgramClass>> groups) {
    for (Policy policy : policies) {
      if (policy instanceof SingleClassPolicy) {
        groups = applySingleClassPolicy((SingleClassPolicy) policy, groups);
      } else if (policy instanceof MultiClassPolicy) {
        groups = applyMultiClassPolicy((MultiClassPolicy) policy, groups);
      }

      // Any policy should not return any trivial groups.
      assert groups.stream().allMatch(group -> group.size() >= 2);
    }

    return groups;
  }
}
