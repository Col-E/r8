// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.classmerging.StaticallyMergedClasses;
import java.util.Set;
import java.util.function.BiConsumer;

public class StaticallyMergedClassesInspector {

  private final DexItemFactory dexItemFactory;
  private final StaticallyMergedClasses staticallyMergedClasses;

  public StaticallyMergedClassesInspector(
      DexItemFactory dexItemFactory, StaticallyMergedClasses staticallyMergedClasses) {
    this.dexItemFactory = dexItemFactory;
    this.staticallyMergedClasses = staticallyMergedClasses;
  }

  public void forEachMergeGroup(BiConsumer<Set<DexType>, DexType> consumer) {
    staticallyMergedClasses.forEachMergeGroup(consumer);
  }
}
