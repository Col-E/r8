// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.ProguardConfigurationParser.IdentifierPatternWithWildcards;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.LongInterval;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProguardConfigurationUtils {

  public static List<ProguardConfigurationRule> synthesizeRules(AppView<?> appView) {
    List<ProguardConfigurationRule> synthesizedRules = new ArrayList<>();
    DexItemFactory factory = appView.dexItemFactory();
    InternalOptions options = appView.options();
    // Add synthesized -assumenosideeffects from min api if relevant.
    if (options.isGeneratingDex()) {
      if (!hasExplicitAssumeValuesOrAssumeNoSideEffectsRuleForMinSdk(
          factory, options.getProguardConfiguration().getRules())) {
        synthesizedRules.add(
            buildAssumeNoSideEffectsRuleForApiLevel(factory, options.getMinApiLevel()));
      }
    }
    // Add synthesized -keepclassmembers rules for the default initializer of classes that inherit
    // from android.app.Fragment and android.app.ZygotePreload. This is needed since the Android
    // Platform may reflectively access these instance initializers.
    DexClass androidAppFragment =
        appView.appInfo().definitionForWithoutExistenceAssert(factory.androidAppFragment);
    if (androidAppFragment != null) {
      synthesizedRules.add(
          buildKeepClassMembersNoShrinkingOfInitializerOnSubclasses(factory, androidAppFragment));
    }
    DexClass androidAppZygotePreload =
        appView.appInfo().definitionForWithoutExistenceAssert(factory.androidAppZygotePreload);
    if (androidAppZygotePreload != null) {
      synthesizedRules.add(
          buildKeepClassMembersNoShrinkingOfInitializerOnSubclasses(
              factory, androidAppZygotePreload));
    }
    return synthesizedRules;
  }

  private static ProguardAssumeNoSideEffectRule buildAssumeNoSideEffectsRuleForApiLevel(
      DexItemFactory factory, AndroidApiLevel apiLevel) {
    Origin synthesizedFromApiLevel =
        new Origin(Origin.root()) {
          @Override
          public String part() {
            return "<SYNTHESIZED_FROM_API_LEVEL_" + apiLevel.getLevel() + ">";
          }
        };

    ProguardAccessFlags publicStaticFinalFlags = new ProguardAccessFlags();
    publicStaticFinalFlags.setPublic();
    publicStaticFinalFlags.setStatic();
    publicStaticFinalFlags.setFinal();

    return ProguardAssumeNoSideEffectRule.builder()
        .setOrigin(synthesizedFromApiLevel)
        .setClassType(ProguardClassType.CLASS)
        .setClassNames(
            ProguardClassNameList.singletonList(
                ProguardTypeMatcher.create(factory.androidOsBuildVersionType)))
        .setMemberRules(
            ImmutableList.of(
                ProguardMemberRule.builder()
                    .setAccessFlags(publicStaticFinalFlags)
                    .setRuleType(ProguardMemberType.FIELD)
                    .setTypeMatcher(ProguardTypeMatcher.create(factory.intType))
                    .setName(IdentifierPatternWithWildcards.withoutWildcards("SDK_INT"))
                    .setReturnValue(
                        new ProguardMemberRuleReturnValue(
                            new LongInterval(apiLevel.getLevel(), Integer.MAX_VALUE)))
                    .build()))
        .build();
  }

  /**
   * Check if an explicit rule matching the field public static final int
   * android.os.Build$VERSION.SDK_INT is present.
   */
  private static boolean hasExplicitAssumeValuesOrAssumeNoSideEffectsRuleForMinSdk(
      DexItemFactory factory, List<ProguardConfigurationRule> rules) {
    for (ProguardConfigurationRule rule : rules) {
      if (!(rule instanceof ProguardAssumeValuesRule
          || rule instanceof ProguardAssumeNoSideEffectRule)) {
        continue;
      }
      if (rule.getClassType() != ProguardClassType.CLASS) {
        continue;
      }
      if (!rule.getClassAnnotations().isEmpty() || !rule.getInheritanceAnnotations().isEmpty()) {
        continue;
      }
      if (rule.hasInheritanceClassName()
          && !rule.getInheritanceClassName().matches(factory.objectType)) {
        continue;
      }
      if (rule.getClassNames().hasWildcards()
          || !rule.getClassNames().matches(factory.androidOsBuildVersionType)) {
        continue;
      }
      for (ProguardMemberRule memberRule : rule.getMemberRules()) {
        if (memberRule.getRuleType() == ProguardMemberType.ALL
            || memberRule.getRuleType() == ProguardMemberType.ALL_FIELDS) {
          return true;
        }
        if (memberRule.getRuleType() != ProguardMemberType.FIELD) {
          continue;
        }
        if (!memberRule.getAnnotations().isEmpty()) {
          continue;
        }
        if (memberRule.getAccessFlags().isProtected()
            || memberRule.getAccessFlags().isPrivate()
            || memberRule.getAccessFlags().isAbstract()
            || memberRule.getAccessFlags().isTransient()
            || memberRule.getAccessFlags().isVolatile()) {
          continue;
        }
        if (memberRule.getNegatedAccessFlags().isPublic()
            || memberRule.getNegatedAccessFlags().isStatic()
            || memberRule.getNegatedAccessFlags().isFinal()) {
          continue;
        }
        if (!memberRule.getType().matches(factory.intType)) {
          continue;
        }
        if (!memberRule.getName().matches("SDK_INT")) {
          continue;
        }
        return true;
      }
    }
    return false;
  }

  // -keepclassmembers,allow* !abstract class * extends T { void <init>(); }
  private static ProguardKeepRule buildKeepClassMembersNoShrinkingOfInitializerOnSubclasses(
      DexItemFactory factory, DexClass clazz) {
    return ProguardKeepRule.builder()
        .setClassNames(ProguardClassNameList.singletonList(ProguardTypeMatcher.allClassesMatcher()))
        .setClassType(ProguardClassType.CLASS)
        .setInheritanceClassName(ProguardTypeMatcher.create(clazz.getType()))
        .setInheritanceIsExtends(!clazz.isInterface())
        .setMemberRules(
            Collections.singletonList(
                ProguardMemberRule.builder()
                    .setRuleType(ProguardMemberType.INIT)
                    .setName(IdentifierPatternWithWildcards.init())
                    .setArguments(Collections.emptyList())
                    .setTypeMatcher(ProguardTypeMatcher.create(factory.voidType))
                    .build()))
        .setNegatedClassAccessFlags(new ProguardAccessFlags().setAbstract())
        .setOrigin(clazz.getOrigin())
        .setType(ProguardKeepRuleType.KEEP_CLASS_MEMBERS)
        .updateModifiers(
            modifiersBuilder -> modifiersBuilder.setAllowsAll().setAllowsShrinking(false).build())
        .build();
  }
}
