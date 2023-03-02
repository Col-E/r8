// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import static com.android.tools.r8.utils.IteratorUtils.createCircularIterator;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public abstract class AtMostOneClassThatMatchesPolicy extends MultiClassPolicy {

  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  @Override
  public Collection<MergeGroup> apply(MergeGroup group) {
    // Create a new merge group for each class that we want at most one of.
    List<MergeGroup> newGroups = new LinkedList<>();
    for (DexProgramClass clazz : group) {
      if (atMostOneOf(clazz)) {
        newGroups.add(new MergeGroup(clazz));
      }
    }

    // If there were at most one class with that we could have at most one of, then just return the
    // original merge group.
    if (newGroups.size() <= 1) {
      return ImmutableList.of(group);
    }

    // Otherwise, fill up the new merge groups with the remaining classes.
    Iterator<MergeGroup> newGroupsIterator = createCircularIterator(newGroups);
    for (DexProgramClass clazz : group) {
      if (!atMostOneOf(clazz)) {
        newGroupsIterator.next().add(clazz);
      }
    }
    return removeTrivialGroups(newGroups);
  }

  abstract boolean atMostOneOf(DexProgramClass clazz);
}
