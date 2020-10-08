// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.shaking.ClassMergingEnqueuerExtension;

public class NoRuntimeTypeChecks extends SingleClassPolicy {
  private final ClassMergingEnqueuerExtension classMergingEnqueuerExtension;

  public NoRuntimeTypeChecks(ClassMergingEnqueuerExtension classMergingEnqueuerExtension) {
    this.classMergingEnqueuerExtension = classMergingEnqueuerExtension;
  }

  @Override
  public boolean canMerge(DexProgramClass clazz) {
    // We currently assume we only merge classes that implement the same set of interfaces.
    return !classMergingEnqueuerExtension.isRuntimeCheckType(clazz);
  }
}
