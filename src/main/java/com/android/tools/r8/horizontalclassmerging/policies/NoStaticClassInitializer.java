// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;

/**
 * Prevent merging of classes with static initializers, as merging these causes side effects. It is
 * okay for superclasses to have static initializers as all classes are expected to have the same
 * super class.
 */
public class NoStaticClassInitializer extends SingleClassPolicy {
  @Override
  public boolean canMerge(DexProgramClass program) {
    return !program.hasClassInitializer();
  }
}
