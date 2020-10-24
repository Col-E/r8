// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.MainDexClasses;
import com.android.tools.r8.shaking.MainDexTracingResult;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class PreventMergeIntoMainDex extends MultiClassPolicy {
  private final MainDexClasses mainDexClasses;
  private final MainDexTracingResult mainDexTracingResult;

  public PreventMergeIntoMainDex(
      AppView<AppInfoWithLiveness> appView, MainDexTracingResult mainDexTracingResult) {
    this.mainDexClasses = appView.appInfo().getMainDexClasses();
    this.mainDexTracingResult = mainDexTracingResult;
  }

  public boolean isMainDexClass(DexProgramClass clazz) {
    return mainDexClasses.contains(clazz) || mainDexTracingResult.contains(clazz);
  }

  @Override
  public Collection<List<DexProgramClass>> apply(List<DexProgramClass> group) {
    List<DexProgramClass> mainDexMembers = new LinkedList<>();
    Iterator<DexProgramClass> iterator = group.iterator();
    while (iterator.hasNext()) {
      DexProgramClass clazz = iterator.next();
      if (isMainDexClass(clazz)) {
        iterator.remove();
        mainDexMembers.add(clazz);
      }
    }

    Collection<List<DexProgramClass>> newGroups = new LinkedList<>();
    if (!isTrivial(mainDexMembers)) {
      newGroups.add(mainDexMembers);
    }
    if (!isTrivial(group)) {
      newGroups.add(group);
    }
    return newGroups;
  }
}
