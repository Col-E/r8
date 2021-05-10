// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import java.util.ArrayList;
import java.util.Collection;

/**
 * The super class of all horizontal class merging policies. Most classes will either implement
 * {@link SingleClassPolicy} or {@link MultiClassPolicy}.
 */
public abstract class Policy {

  /** Counter keeping track of how many classes this policy has removed. For debugging only. */
  public int numberOfRemovedClasses;

  public int numberOfRemovedInterfaces;

  public void clear() {}

  public abstract String getName();

  public boolean isSingleClassPolicy() {
    return false;
  }

  public SingleClassPolicy asSingleClassPolicy() {
    return null;
  }

  public boolean isMultiClassPolicy() {
    return false;
  }

  public MultiClassPolicy asMultiClassPolicy() {
    return null;
  }

  public boolean isMultiClassPolicyWithPreprocessing() {
    return false;
  }

  public MultiClassPolicyWithPreprocessing<?> asMultiClassPolicyWithPreprocessing() {
    return null;
  }

  public boolean shouldSkipPolicy() {
    return false;
  }

  /**
   * Remove all groups containing no or only a single class, as there is no point in merging these.
   */
  protected Collection<MergeGroup> removeTrivialGroups(Collection<MergeGroup> groups) {
    assert !(groups instanceof ArrayList);
    groups.removeIf(MergeGroup::isTrivial);
    return groups;
  }

  boolean recordRemovedClassesForDebugging(
      boolean isInterfaceGroup, int previousGroupSize, Collection<MergeGroup> newGroups) {
    assert previousGroupSize >= 2;
    int previousNumberOfRemovedClasses = previousGroupSize - 1;
    int newNumberOfRemovedClasses = 0;
    for (MergeGroup newGroup : newGroups) {
      if (newGroup.isNonTrivial()) {
        newNumberOfRemovedClasses += newGroup.size() - 1;
      }
    }
    assert previousNumberOfRemovedClasses >= newNumberOfRemovedClasses;
    int change = previousNumberOfRemovedClasses - newNumberOfRemovedClasses;
    if (isInterfaceGroup) {
      numberOfRemovedInterfaces += change;
    } else {
      numberOfRemovedClasses += change;
    }
    return true;
  }
}
