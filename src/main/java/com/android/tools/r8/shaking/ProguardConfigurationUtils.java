// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
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

  public static ProguardKeepRule buildFieldKeepRule(DexClass clazz, DexEncodedField field) {
    assert clazz.type == field.field.getHolder();
    ProguardKeepRule.Builder builder = ProguardKeepRule.builder();
    builder.setType(ProguardKeepRuleType.KEEP_CLASS_MEMBERS);
    builder.getModifiersBuilder().setAllowsObfuscation(true);
    builder.getModifiersBuilder().setAllowsOptimization(true);
    builder.getClassAccessFlags().setPublic();
    if (clazz.isInterface()) {
      builder.setClassType(ProguardClassType.INTERFACE);
    } else {
      builder.setClassType(ProguardClassType.CLASS);
    }
    builder.setClassNames(
        ProguardClassNameList.singletonList(ProguardTypeMatcher.create(clazz.type)));
    ProguardMemberRule.Builder memberRuleBuilder = ProguardMemberRule.builder();
    memberRuleBuilder.setRuleType(ProguardMemberType.FIELD);
    memberRuleBuilder.getAccessFlags().setFlags(field.accessFlags);
    memberRuleBuilder.setName(field.field.name.toString());
    memberRuleBuilder.setTypeMatcher(ProguardTypeMatcher.create(field.field.type));
    builder.getMemberRules().add(memberRuleBuilder.build());
    return builder.build();
  }

  public static ProguardKeepRule buildMethodKeepRule(DexClass clazz, DexEncodedMethod method) {
    assert clazz.type == method.method.getHolder();
    ProguardKeepRule.Builder builder = ProguardKeepRule.builder();
    builder.setType(ProguardKeepRuleType.KEEP_CLASS_MEMBERS);
    builder.getModifiersBuilder().setAllowsObfuscation(true);
    builder.getModifiersBuilder().setAllowsOptimization(true);
    builder.getClassAccessFlags().setPublic();
    if (clazz.isInterface()) {
      builder.setClassType(ProguardClassType.INTERFACE);
    } else {
      builder.setClassType(ProguardClassType.CLASS);
    }
    builder.setClassNames(
        ProguardClassNameList.singletonList(ProguardTypeMatcher.create(clazz.type)));
    ProguardMemberRule.Builder memberRuleBuilder = ProguardMemberRule.builder();
    memberRuleBuilder.setRuleType(ProguardMemberType.METHOD);
    memberRuleBuilder.getAccessFlags().setFlags(method.accessFlags);
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
