// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.DexProgramClass;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class MultiClassPolicy extends Policy {

  // TODO(b/165577835): Move to a virtual method on MergeGroup.
  protected boolean isTrivial(Collection<DexProgramClass> group) {
    return group.size() < 2;
  }

  /**
   * Remove all groups containing no or only a single class, as there is no point in merging these.
   */
  protected void removeTrivialGroups(Collection<List<DexProgramClass>> groups) {
    assert !(groups instanceof ArrayList);
    groups.removeIf(this::isTrivial);
  }

  /**
   * Apply the multi class policy to a group of program classes.
   *
   * @param group This is a group of program classes which can currently still be merged.
   * @return The same collection of program classes split into new groups of candidates which can be
   *     merged. If the policy detects no issues then `group` will be returned unchanged. If classes
   *     cannot be merged with any other classes they are returned as singleton lists.
   */
  public abstract Collection<List<DexProgramClass>> apply(List<DexProgramClass> group);
}
