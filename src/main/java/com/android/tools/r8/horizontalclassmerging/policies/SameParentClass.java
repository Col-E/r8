// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Map;

public class SameParentClass extends MultiClassPolicy {

  @Override
  public Collection<Collection<DexProgramClass>> apply(Collection<DexProgramClass> group) {
    Map<DexType, Collection<DexProgramClass>> groups = new IdentityHashMap<>();
    for (DexProgramClass clazz : group) {
      groups
          .computeIfAbsent(clazz.superType, ignore -> new LinkedList<DexProgramClass>())
          .add(clazz);
    }
    removeTrivialGroups(groups.values());
    return groups.values();
  }
}
