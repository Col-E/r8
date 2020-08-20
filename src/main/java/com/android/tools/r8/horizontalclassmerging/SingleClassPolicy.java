// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.DexProgramClass;

public abstract class SingleClassPolicy extends Policy {
  /**
   * Determine if {@param program} can be merged with any other classes.
   *
   * @return {@code false} if the class should not be merged, otherwise {@code true}.
   */
  public abstract boolean canMerge(DexProgramClass program);
}
