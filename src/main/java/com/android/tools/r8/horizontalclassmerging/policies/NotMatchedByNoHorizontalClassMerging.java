// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.ir.analysis.proto.EnumLiteProtoShrinker;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Collections;
import java.util.Set;

public class NotMatchedByNoHorizontalClassMerging extends SingleClassPolicy {

  private final Set<DexType> deadEnumLiteMaps;
  private final Set<DexType> neverMergeClassHorizontally;

  public NotMatchedByNoHorizontalClassMerging(AppView<AppInfoWithLiveness> appView) {
    deadEnumLiteMaps =
        appView.withProtoEnumShrinker(
            EnumLiteProtoShrinker::getDeadEnumLiteMaps, Collections.emptySet());
    neverMergeClassHorizontally = appView.appInfo().getNoHorizontalClassMergingSet();
  }

  @Override
  public boolean canMerge(DexProgramClass program) {
    return !deadEnumLiteMaps.contains(program.getType())
        && !neverMergeClassHorizontally.contains(program.getType());
  }
}
