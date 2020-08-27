// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import java.util.Collections;
import java.util.Set;

public class NoInternalUtilityClasses extends SingleClassPolicy {
  private final Set<DexType> internalUtilityClasses;

  public NoInternalUtilityClasses(DexItemFactory dexItemFactory) {
    this.internalUtilityClasses = Collections.singleton(dexItemFactory.enumUnboxingUtilityType);
  }

  @Override
  public boolean canMerge(DexProgramClass program) {
    return !internalUtilityClasses.contains(program.type);
  }
}
