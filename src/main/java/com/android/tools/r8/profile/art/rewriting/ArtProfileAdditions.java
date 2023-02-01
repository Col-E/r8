// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.rewriting;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.profile.art.ArtProfile;
import com.android.tools.r8.profile.art.ArtProfileClassRule;
import com.android.tools.r8.profile.art.ArtProfileMethodRule;
import com.android.tools.r8.profile.art.ArtProfileRule;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Mutable extension of an existing ArtProfile. */
public class ArtProfileAdditions {

  public interface ArtProfileAdditionsBuilder {

    ArtProfileAdditionsBuilder addRule(ProgramDefinition definition);

    ArtProfileAdditionsBuilder addRule(DexReference reference);

    ArtProfileAdditionsBuilder removeMovedMethodRule(
        ProgramMethod oldMethod, ProgramMethod newMethod);
  }

  private final ArtProfile artProfile;

  private final Map<DexType, ArtProfileClassRule.Builder> classRuleAdditions =
      new ConcurrentHashMap<>();
  private final Map<DexMethod, ArtProfileMethodRule.Builder> methodRuleAdditions =
      new ConcurrentHashMap<>();
  private final Set<DexMethod> methodRuleRemovals = Sets.newConcurrentHashSet();

  ArtProfileAdditions(ArtProfile artProfile) {
    this.artProfile = artProfile;
  }

  void applyIfContextIsInProfile(
      DexMethod context, Consumer<ArtProfileAdditionsBuilder> builderConsumer) {
    ArtProfileMethodRule contextMethodRule = artProfile.getMethodRule(context);
    if (contextMethodRule != null) {
      builderConsumer.accept(
          new ArtProfileAdditionsBuilder() {

            @Override
            public ArtProfileAdditionsBuilder addRule(ProgramDefinition definition) {
              return addRule(definition.getReference());
            }

            @Override
            public ArtProfileAdditionsBuilder addRule(DexReference reference) {
              addRuleFromContext(
                  reference, contextMethodRule, MethodRuleAdditionConfig.getDefault());
              return this;
            }

            @Override
            public ArtProfileAdditionsBuilder removeMovedMethodRule(
                ProgramMethod oldMethod, ProgramMethod newMethod) {
              ArtProfileAdditions.this.removeMovedMethodRule(oldMethod, newMethod);
              return this;
            }
          });
    }
  }

  private void addRuleFromContext(
      DexReference reference,
      ArtProfileMethodRule contextMethodRule,
      MethodRuleAdditionConfig config) {
    if (reference.isDexType()) {
      addClassRule(reference.asDexType());
    } else {
      assert reference.isDexMethod();
      addMethodRuleFromContext(reference.asDexMethod(), contextMethodRule, config);
    }
  }

  private void addClassRule(DexType type) {
    if (artProfile.containsClassRule(type)) {
      return;
    }

    // Create profile rule for class.
    classRuleAdditions.computeIfAbsent(type, key -> ArtProfileClassRule.builder().setType(key));
  }

  private void addMethodRuleFromContext(
      DexMethod method, ArtProfileMethodRule contextMethodRule, MethodRuleAdditionConfig config) {
    // Create profile rule for method.
    ArtProfileMethodRule.Builder methodRuleBuilder =
        methodRuleAdditions.computeIfAbsent(
            method, methodReference -> ArtProfileMethodRule.builder().setMethod(method));

    // Setup the rule.
    synchronized (methodRuleBuilder) {
      methodRuleBuilder.acceptMethodRuleInfoBuilder(
          methodRuleInfoBuilder ->
              config.configureMethodRuleInfo(methodRuleInfoBuilder, contextMethodRule));
    }
  }

  void removeMovedMethodRule(ProgramMethod oldMethod, ProgramMethod newMethod) {
    assert artProfile.containsMethodRule(oldMethod.getReference());
    assert methodRuleAdditions.containsKey(newMethod.getReference());
    methodRuleRemovals.add(oldMethod.getReference());
  }

  ArtProfile createNewArtProfile() {
    if (!hasAdditions()) {
      assert !hasRemovals();
      return artProfile;
    }

    // Add existing rules to new profile.
    ArtProfile.Builder artProfileBuilder = ArtProfile.builder();
    artProfile.forEachRule(
        artProfileBuilder::addRule,
        methodRule -> {
          if (!methodRuleRemovals.contains(methodRule.getMethod())) {
            artProfileBuilder.addRule(methodRule);
          }
        });

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

  private boolean hasRemovals() {
    return !methodRuleRemovals.isEmpty();
  }
}
