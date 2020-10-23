// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Set;

public class NoKeepRules extends SingleClassPolicy {
  private final AppView<AppInfoWithLiveness> appView;
  private final Set<DexType> dontMergeTypes = Sets.newIdentityHashSet();

  public NoKeepRules(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;

    appView.appInfo().classes().forEach(this::processClass);
  }

  private void processClass(DexProgramClass programClass) {
    DexType type = programClass.getType();
    boolean pinProgramClass = appView.appInfo().isPinned(type);

    for (DexEncodedMember<?, ?> member : programClass.members()) {
      DexMember<?, ?> reference = member.toReference();
      if (appView.appInfo().isPinned(reference)) {
        pinProgramClass = true;
        Iterables.addAll(
            dontMergeTypes,
            Iterables.filter(
                reference.getReferencedBaseTypes(appView.dexItemFactory()), DexType::isClassType));
      }
    }

    if (pinProgramClass) {
      dontMergeTypes.add(type);
    }
  }

  @Override
  public boolean canMerge(DexProgramClass program) {
    return !dontMergeTypes.contains(program.getType());
  }
}
