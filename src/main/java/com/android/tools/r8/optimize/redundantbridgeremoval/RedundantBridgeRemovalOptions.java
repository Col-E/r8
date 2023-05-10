// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.redundantbridgeremoval;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.SetUtils;
import java.util.Collections;
import java.util.Set;

public class RedundantBridgeRemovalOptions {

  private final InternalOptions options;

  private boolean enableRetargetingOfConstructorBridgeCalls = true;
  private Set<DexType> noConstructorShrinkingHierarchies;

  public RedundantBridgeRemovalOptions(InternalOptions options) {
    this.options = options;
  }

  public void clearNoConstructorShrinkingHierarchiesForTesting() {
    noConstructorShrinkingHierarchies = Collections.emptySet();
  }

  public RedundantBridgeRemovalOptions ensureInitialized() {
    if (noConstructorShrinkingHierarchies == null) {
      DexItemFactory dexItemFactory = options.dexItemFactory();
      noConstructorShrinkingHierarchies =
          SetUtils.newIdentityHashSet(
              dexItemFactory.androidAppFragment, dexItemFactory.androidAppZygotePreload);
    }
    return this;
  }

  public boolean isPlatformReflectingOnDefaultConstructorInSubclasses(DexLibraryClass clazz) {
    return noConstructorShrinkingHierarchies.contains(clazz.getType());
  }

  public boolean isRetargetingOfConstructorBridgeCallsEnabled() {
    return enableRetargetingOfConstructorBridgeCalls;
  }

  public void setEnableRetargetingOfConstructorBridgeCalls(
      boolean enableRetargetingOfConstructorBridgeCalls) {
    this.enableRetargetingOfConstructorBridgeCalls = enableRetargetingOfConstructorBridgeCalls;
  }
}
