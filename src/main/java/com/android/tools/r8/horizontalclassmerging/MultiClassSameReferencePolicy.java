// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.DexProgramClass;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public abstract class MultiClassSameReferencePolicy<T> extends MultiClassPolicy {
  @Override
  public final Collection<List<DexProgramClass>> apply(List<DexProgramClass> group) {
    Map<T, List<DexProgramClass>> groups = new LinkedHashMap<>();
    for (DexProgramClass clazz : group) {
      groups.computeIfAbsent(getMergeKey(clazz), ignore -> new LinkedList<>()).add(clazz);
    }
    removeTrivialGroups(groups.values());
    return groups.values();
  }

  public abstract T getMergeKey(DexProgramClass clazz);
}
