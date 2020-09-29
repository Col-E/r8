// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

/**
 * The super class of all horizontal class merging policies. Most classes will either implement
 * {@link SingleClassPolicy} or {@link MultiClassPolicy}.
 */
public abstract class Policy {
  /** Counter keeping track of how many classes this policy has removed. For debugging only. */
  public int numberOfRemovedClasses;

  public boolean shouldSkipPolicy() {
    return false;
  }
}
