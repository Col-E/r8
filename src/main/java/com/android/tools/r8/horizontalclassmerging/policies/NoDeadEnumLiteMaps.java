// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.ir.analysis.proto.EnumLiteProtoShrinker;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Collections;
import java.util.Set;

public class NoDeadEnumLiteMaps extends SingleClassPolicy {

  private final Set<DexType> deadEnumLiteMaps;

  public NoDeadEnumLiteMaps(AppView<AppInfoWithLiveness> appView, Mode mode) {
    // This policy is only relevant for the initial round of class merging, since the dead enum lite
    // maps have been removed from the application when the final round of class merging runs.
    assert mode.isInitial();
    this.deadEnumLiteMaps =
        appView.withProtoEnumShrinker(
            EnumLiteProtoShrinker::getDeadEnumLiteMaps, Collections.emptySet());
  }

  @Override
  public boolean canMerge(DexProgramClass clazz) {
    return !deadEnumLiteMaps.contains(clazz.getType());
  }

  @Override
  public String getName() {
    return "NoDeadEnumLiteMaps";
  }
}
