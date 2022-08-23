// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import java.util.Collections;

public class ProguardKeepRuleUtils {

  public static ProguardKeepRule keepClassAndMembersRule(
      Origin origin, Position start, DexType type, String source) {
    return ProguardKeepRule.builder()
        .setType(ProguardKeepRuleType.KEEP)
        .setClassType(ProguardClassType.CLASS)
        .setOrigin(origin)
        .setStart(start)
        .setClassNames(
            ProguardClassNameList.builder()
                .addClassName(false, ProguardTypeMatcher.create(type))
                .build())
        .setMemberRules(Collections.singletonList(ProguardMemberRule.defaultKeepAllRule()))
        .setSource(source)
        .build();
  }
}
