// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.rewriting;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.profile.art.ArtProfile;
import com.android.tools.r8.profile.art.ArtProfileClassRule;
import com.android.tools.r8.profile.art.ArtProfileMethodRule;
import com.android.tools.r8.profile.art.ArtProfileRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Mutable extension of an existing ArtProfile. */
class ArtProfileAdditions {

  private final ArtProfile artProfile;

  private final Map<DexType, ArtProfileClassRule.Builder> classRuleAdditions =
      new ConcurrentHashMap<>();
  private final Map<DexMethod, ArtProfileMethodRule.Builder> methodRuleAdditions =
      new ConcurrentHashMap<>();

  ArtProfileAdditions(ArtProfile artProfile) {
    this.artProfile = artProfile;
  }

  void addClassRule(DexProgramClass clazz) {
    if (artProfile.containsClassRule(clazz.getType())) {
      return;
    }

    // Create profile rule for class.
    classRuleAdditions.computeIfAbsent(
        clazz.getType(), type -> ArtProfileClassRule.builder().setType(type));
  }

  boolean addMethodRuleIfContextIsInProfile(ProgramMethod method, ProgramMethod context) {
    return addMethodRuleIfContextIsInProfile(
        method, context, MethodRuleAdditionConfig.getDefault());
  }

  /**
   * Adds the given {@param method} to the ART profile if {@param context} is live (i.e., present in
   * the baseline profile).
   *
   * @return true if {@param context} is live.
   */
  boolean addMethodRuleIfContextIsInProfile(
      ProgramMethod method, ProgramMethod context, MethodRuleAdditionConfig config) {
    assert !artProfile.containsMethodRule(method.getReference());

    ArtProfileMethodRule contextMethodRule = artProfile.getMethodRule(context.getReference());
    if (contextMethodRule == null) {
      return false;
    }

    // Create profile rule for method.
    ArtProfileMethodRule.Builder methodRuleBuilder =
        methodRuleAdditions.computeIfAbsent(
            method.getReference(),
            methodReference -> ArtProfileMethodRule.builder().setMethod(method.getReference()));

    // Setup the rule.
    methodRuleBuilder.acceptMethodRuleInfoBuilder(
        methodRuleInfoBuilder ->
            config.configureMethodRuleInfo(methodRuleInfoBuilder, contextMethodRule));
    return true;
  }

  ArtProfile createNewArtProfile() {
    if (!hasAdditions()) {
      return artProfile;
    }

    // Add existing rules to new profile.
    ArtProfile.Builder artProfileBuilder = ArtProfile.builder();
    artProfile.forEachRule(artProfileBuilder::addRule);

    // Sort and add additions to new profile. Sorting is needed since the additions to this
    // collection may be concurrent.
    List<ArtProfileRule> ruleAdditionsSorted =
        new ArrayList<>(classRuleAdditions.size() + methodRuleAdditions.size());
    classRuleAdditions
        .values()
        .forEach(classRuleBuilder -> ruleAdditionsSorted.add(classRuleBuilder.build()));
    methodRuleAdditions
        .values()
        .forEach(methodRuleBuilder -> ruleAdditionsSorted.add(methodRuleBuilder.build()));
    ruleAdditionsSorted.sort(ArtProfileRule::compareTo);
    artProfileBuilder.addRules(ruleAdditionsSorted);

    return artProfileBuilder.build();
  }

  boolean hasAdditions() {
    return !classRuleAdditions.isEmpty() || !methodRuleAdditions.isEmpty();
  }
}
