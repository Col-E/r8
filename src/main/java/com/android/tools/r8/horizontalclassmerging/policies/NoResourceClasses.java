// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;

public class NoResourceClasses extends SingleClassPolicy {

  @Override
  public boolean canMerge(DexProgramClass program) {
    return !program.getSimpleName().startsWith("R$");
  }

  @Override
  public String getName() {
    return "NoResourceClasses";
  }
}
