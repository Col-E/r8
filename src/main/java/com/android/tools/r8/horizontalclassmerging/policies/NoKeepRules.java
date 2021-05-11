// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Set;

public class NoKeepRules extends SingleClassPolicy {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final KeepInfoCollection keepInfo;

  private final Set<DexType> dontMergeTypes = Sets.newIdentityHashSet();

  public NoKeepRules(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
    this.keepInfo = appView.getKeepInfo();
    appView.appInfo().classes().forEach(this::processClass);
  }

  private void processClass(DexProgramClass clazz) {
    DexType type = clazz.getType();
    boolean pinHolder = keepInfo.getClassInfo(clazz).isPinned();
    for (DexEncodedMember<?, ?> member : clazz.members()) {
      if (keepInfo.getMemberInfo(member, clazz).isPinned()) {
        pinHolder = true;
        Iterables.addAll(
            dontMergeTypes,
            Iterables.filter(
                member.getReference().getReferencedBaseTypes(appView.dexItemFactory()),
                DexType::isClassType));
      }
    }
    if (pinHolder) {
      dontMergeTypes.add(type);
    }
  }

  @Override
  public boolean canMerge(DexProgramClass program) {
    return !dontMergeTypes.contains(program.getType());
  }

  @Override
  public String getName() {
    return "NoKeepRules";
  }
}
