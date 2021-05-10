// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import java.util.Collection;

public abstract class MultiClassPolicy extends Policy {

  /**
   * Apply the multi class policy to a group of program classes.
   *
   * @param group This is a group of program classes which can currently still be merged.
   * @return The same collection of program classes split into new groups of candidates which can be
   *     merged. If the policy detects no issues then `group` will be returned unchanged. If classes
   *     cannot be merged with any other classes they are returned as singleton lists.
   */
  public abstract Collection<MergeGroup> apply(MergeGroup group);

  @Override
  public boolean isMultiClassPolicy() {
    return true;
  }

  @Override
  public MultiClassPolicy asMultiClassPolicy() {
    return this;
  }
}
