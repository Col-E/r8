// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class DontMergeSynchronizedClasses extends MultiClassPolicy {
  private final AppView<AppInfoWithLiveness> appView;

  public DontMergeSynchronizedClasses(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  private boolean isSynchronizationClass(DexProgramClass clazz) {
    return appView.appInfo().isLockCandidate(clazz.type) || clazz.hasStaticSynchronizedMethods();
  }

  @Override
  public Collection<? extends List<DexProgramClass>> apply(List<DexProgramClass> group) {
    // Gather all synchronized classes.
    Collection<List<DexProgramClass>> synchronizedGroups = new LinkedList<>();
    group.removeIf(
        clazz -> {
          boolean synchronizationClass = isSynchronizationClass(clazz);
          if (synchronizationClass) {
            List<DexProgramClass> synchronizedGroup = new LinkedList<>();
            synchronizedGroup.add(clazz);
            synchronizedGroups.add(synchronizedGroup);
          }
          return synchronizationClass;
        });

    if (synchronizedGroups.isEmpty()) {
      return Collections.singletonList(group);
    }

    Iterator<List<DexProgramClass>> synchronizedGroupIterator = synchronizedGroups.iterator();
    for (DexProgramClass clazz : group) {
      if (!synchronizedGroupIterator.hasNext()) {
        synchronizedGroupIterator = synchronizedGroups.iterator();
      }
      synchronizedGroupIterator.next().add(clazz);
    }

    removeTrivialGroups(synchronizedGroups);

    return synchronizedGroups;
  }
}
