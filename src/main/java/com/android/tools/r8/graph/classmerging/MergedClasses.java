// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.classmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Set;
import java.util.function.BiConsumer;

public interface MergedClasses {

  void forEachMergeGroup(BiConsumer<Set<DexType>, DexType> consumer);

  boolean hasBeenMergedIntoDifferentType(DexType type);

  boolean isMergeTarget(DexType type);

  boolean verifyAllSourcesPruned(AppView<AppInfoWithLiveness> appView);
}
