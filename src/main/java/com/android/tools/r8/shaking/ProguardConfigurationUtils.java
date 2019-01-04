// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.ProguardConfigurationParser.IdentifierPatternWithWildcards;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ProguardConfigurationUtils {

  private static Origin proguardCompatOrigin =
      new Origin(Origin.root()) {
        @Override
        public String part() {
          return "<PROGUARD_COMPATIBILITY_RULE>";
        }
      };

  public static ProguardKeepRule buildMethodHandleKeepRule(DexClass clazz) {
    ProguardKeepRule.Builder builder = ProguardKeepRule.builder();
    builder.setOrigin(proguardCompatOrigin);
    builder.setType(ProguardKeepRuleType.KEEP);
    builder.getModifiersBuilder().setAllowsObfuscation(true);
    builder.getModifiersBuilder().setAllowsOptimization(true);
    builder.setClassType(
        clazz.isInterface() ? ProguardClassType.INTERFACE : ProguardClassType.CLASS);
    builder.setClassNames(
        ProguardClassNameList.singletonList(ProguardTypeMatcher.create(clazz.type)));
    return builder.build();
  }

  public static ProguardKeepRule buildDefaultInitializerKeepRule(DexClass clazz) {
    ProguardKeepRule.Builder builder = ProguardKeepRule.builder();
    builder.setOrigin(proguardCompatOrigin);
    builder.setType(ProguardKeepRuleType.KEEP);
    builder.getModifiersBuilder().setAllowsObfuscation(true);
    builder.getModifiersBuilder().setAllowsOptimization(true);
    builder.getClassAccessFlags().setVisibility(clazz.accessFlags);
    builder.setClassType(ProguardClassType.CLASS);
    builder.setClassNames(
        ProguardClassNameList.singletonList(ProguardTypeMatcher.create(clazz.type)));
    if (clazz.hasDefaultInitializer()) {
      ProguardMemberRule.Builder memberRuleBuilder = ProguardMemberRule.builder();
      memberRuleBuilder.setRuleType(ProguardMemberType.INIT);
      memberRuleBuilder.setName(IdentifierPatternWithWildcards.withoutWildcards("<init>"));
      memberRuleBuilder.setArguments(ImmutableList.of());
      builder.getMemberRules().add(memberRuleBuilder.build());
    }
    return builder.build();
  }

  public static ProguardKeepRule buildFieldKeepRule(
      DexClass clazz, DexEncodedField field, boolean keepClass) {
    assert clazz.type == field.field.getHolder();
    ProguardKeepRule.Builder builder = ProguardKeepRule.builder();
    builder.setOrigin(proguardCompatOrigin);
    if (keepClass) {
      builder.setType(ProguardKeepRuleType.KEEP);
    } else {
      builder.setType(ProguardKeepRuleType.KEEP_CLASS_MEMBERS);
    }
    builder.getModifiersBuilder().setAllowsObfuscation(true);
    builder.getModifiersBuilder().setAllowsOptimization(true);
    builder.getClassAccessFlags().setVisibility(clazz.accessFlags);
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
    memberRuleBuilder.setName(
        IdentifierPatternWithWildcards.withoutWildcards(field.field.name.toString()));
    memberRuleBuilder.setTypeMatcher(ProguardTypeMatcher.create(field.field.type));
    builder.getMemberRules().add(memberRuleBuilder.build());
    return builder.build();
  }

  public static ProguardKeepRule buildMethodKeepRule(DexClass clazz, DexEncodedMethod method) {
    // TODO(b/122295241): These generated rules should be linked into the graph, eg, the method
    // using identified reflection should be the source keeping the target alive.
    assert clazz.type == method.method.getHolder();
    ProguardKeepRule.Builder builder = ProguardKeepRule.builder();
    builder.setOrigin(proguardCompatOrigin);
    builder.setType(ProguardKeepRuleType.KEEP_CLASS_MEMBERS);
    builder.getModifiersBuilder().setAllowsObfuscation(true);
    builder.getModifiersBuilder().setAllowsOptimization(true);
    builder.getClassAccessFlags().setVisibility(clazz.accessFlags);
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
    memberRuleBuilder.setName(
        IdentifierPatternWithWildcards.withoutWildcards(method.method.name.toString()));
    memberRuleBuilder.setTypeMatcher(ProguardTypeMatcher.create(method.method.proto.returnType));
    List<ProguardTypeMatcher> arguments = Arrays.stream(method.method.proto.parameters.values)
        .map(ProguardTypeMatcher::create)
        .collect(Collectors.toList());
    memberRuleBuilder.setArguments(arguments);
    builder.getMemberRules().add(memberRuleBuilder.build());
    return builder.build();
  }

  public static ProguardIdentifierNameStringRule buildIdentifierNameStringRule(DexReference item) {
    assert item.isDexField() || item.isDexMethod();
    ProguardIdentifierNameStringRule.Builder builder = ProguardIdentifierNameStringRule.builder();
    ProguardMemberRule.Builder memberRuleBuilder = ProguardMemberRule.builder();
    DexType holderType;
    if (item.isDexField()) {
      DexField field = item.asDexField();
      holderType = field.getHolder();
      memberRuleBuilder.setRuleType(ProguardMemberType.FIELD);
      memberRuleBuilder.setName(
          IdentifierPatternWithWildcards.withoutWildcards(field.name.toString()));
      memberRuleBuilder.setTypeMatcher(ProguardTypeMatcher.create(field.type));
    } else {
      DexMethod method = item.asDexMethod();
      holderType = method.getHolder();
      memberRuleBuilder.setRuleType(ProguardMemberType.METHOD);
      memberRuleBuilder.setName(
          IdentifierPatternWithWildcards.withoutWildcards(method.name.toString()));
      memberRuleBuilder.setTypeMatcher(ProguardTypeMatcher.create(method.proto.returnType));
      List<ProguardTypeMatcher> arguments = Arrays.stream(method.proto.parameters.values)
          .map(ProguardTypeMatcher::create)
          .collect(Collectors.toList());
      memberRuleBuilder.setArguments(arguments);
    }
    if (holderType.isInterface()) {
      builder.setClassType(ProguardClassType.INTERFACE);
    } else {
      builder.setClassType(ProguardClassType.CLASS);
    }
    builder.setClassNames(
        ProguardClassNameList.singletonList(ProguardTypeMatcher.create(holderType)));
    builder.getMemberRules().add(memberRuleBuilder.build());
    return builder.build();
  }
}
