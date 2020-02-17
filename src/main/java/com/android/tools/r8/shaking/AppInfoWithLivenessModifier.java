// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ObjectAllocationInfoCollectionImpl;
import com.google.common.collect.Sets;
import java.util.Set;

/** Used to mutate AppInfoWithLiveness between waves. */
public class AppInfoWithLivenessModifier {

  private final Set<DexProgramClass> noLongerInstantiatedClasses = Sets.newIdentityHashSet();

  AppInfoWithLivenessModifier() {}

  public boolean isEmpty() {
    return noLongerInstantiatedClasses.isEmpty();
  }

  public void removeInstantiatedType(DexProgramClass clazz) {
    noLongerInstantiatedClasses.add(clazz);
  }

  public void modify(AppInfoWithLiveness appInfo) {
    ObjectAllocationInfoCollectionImpl objectAllocationInfoCollection =
        appInfo.getMutableObjectAllocationInfoCollection();
    noLongerInstantiatedClasses.forEach(objectAllocationInfoCollection::markNoLongerInstantiated);
    clear();
  }

  private void clear() {
    noLongerInstantiatedClasses.clear();
  }
}
