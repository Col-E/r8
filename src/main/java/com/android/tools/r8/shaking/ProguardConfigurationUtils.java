// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexClass;
import com.google.common.collect.ImmutableList;

public class ProguardConfigurationUtils {
  public static ProguardKeepRule buildDefaultInitializerKeepRule(DexClass clazz) {
    ProguardKeepRule.Builder builder = ProguardKeepRule.builder();
    builder.setType(ProguardKeepRuleType.KEEP);
    builder.getModifiersBuilder().allowsObfuscation = true;
    builder.getModifiersBuilder().allowsOptimization = true;
    builder.getClassAccessFlags().setPublic();
    builder.setClassType(ProguardClassType.CLASS);
    ProguardClassNameList.Builder classNameListBuilder = ProguardClassNameList.builder();
    classNameListBuilder.addClassName(false, ProguardTypeMatcher.create(clazz.type));
    builder.setClassNames(classNameListBuilder.build());
    if (clazz.hasDefaultInitializer()) {
      ProguardMemberRule.Builder memberRuleBuilder = ProguardMemberRule.builder();
      memberRuleBuilder.setRuleType(ProguardMemberType.INIT);
      memberRuleBuilder.setName("<init>");
      memberRuleBuilder.setArguments(ImmutableList.of());
      builder.getMemberRules().add(memberRuleBuilder.build());
    }
    return builder.build();
  }
}
