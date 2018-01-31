// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ProguardConfigurationUtils {
  public static ProguardKeepRule buildDefaultInitializerKeepRule(DexClass clazz) {
    ProguardKeepRule.Builder builder = ProguardKeepRule.builder();
    builder.setType(ProguardKeepRuleType.KEEP);
    builder.getModifiersBuilder().setAllowsObfuscation(true);
    builder.getModifiersBuilder().setAllowsOptimization(true);
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

  public static ProguardKeepRule buildDefaultMethodKeepRule(
      DexClass clazz, DexEncodedMethod method) {
    assert clazz.type == method.method.holder;
    ProguardKeepRule.Builder builder = ProguardKeepRule.builder();
    builder.setType(ProguardKeepRuleType.KEEP);
    builder.getModifiersBuilder().setAllowsObfuscation(true);
    builder.getModifiersBuilder().setAllowsOptimization(true);
    builder.getClassAccessFlags().setPublic();
    builder.setClassType(ProguardClassType.INTERFACE);
    builder.setClassNames(
        ProguardClassNameList.singletonList(ProguardTypeMatcher.create(clazz.type)));
    ProguardMemberRule.Builder memberRuleBuilder = ProguardMemberRule.builder();
    memberRuleBuilder.setRuleType(ProguardMemberType.METHOD);
    memberRuleBuilder.setName(method.method.name.toString());
    memberRuleBuilder.setTypeMatcher(ProguardTypeMatcher.create(method.method.proto.returnType));
    List<ProguardTypeMatcher> arguments = Arrays.stream(method.method.proto.parameters.values)
        .map(ProguardTypeMatcher::create)
        .collect(Collectors.toList());
    memberRuleBuilder.setArguments(arguments);
    builder.getMemberRules().add(memberRuleBuilder.build());
    return builder.build();
  }
}
