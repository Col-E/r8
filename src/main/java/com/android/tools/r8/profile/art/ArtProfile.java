// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class ArtProfile {

  private final List<ArtProfileRule> rules;

  ArtProfile(List<ArtProfileRule> rules) {
    this.rules = rules;
  }

  public static Builder builder(DexItemFactory dexItemFactory) {
    return new Builder(dexItemFactory);
  }

  public ArtProfile rewrittenWithLens(GraphLens lens) {
    return transform(rule -> rule.rewrittenWithLens(lens));
  }

  public ArtProfile rewrittenWithLens(NamingLens lens, DexItemFactory dexItemFactory) {
    assert !lens.isIdentityLens();
    return transform(rule -> rule.rewrittenWithLens(dexItemFactory, lens));
  }

  public ArtProfile withoutPrunedItems(PrunedItems prunedItems) {
    return transform(
        rule -> {
          if (rule.isClassRule()) {
            if (prunedItems.isRemoved(rule.asClassRule().getType())) {
              return null;
            }
          } else {
            assert rule.isMethodRule();
            if (prunedItems.isRemoved(rule.asMethodRule().getMethod())) {
              return null;
            }
          }
          return rule;
        });
  }

  private ArtProfile transform(UnaryOperator<ArtProfileRule> transformation) {
    ImmutableList.Builder<ArtProfileRule> newRules =
        ImmutableList.builderWithExpectedSize(rules.size());
    for (ArtProfileRule rule : rules) {
      ArtProfileRule transformedRule = transformation.apply(rule);
      if (transformedRule != null) {
        newRules.add(transformedRule);
      }
    }
    return new ArtProfile(newRules.build());
  }

  public void supplyConsumer(ResidualArtProfileConsumer consumer, Reporter reporter) {
    if (consumer != null) {
      ResidualArtProfileRuleConsumer ruleConsumer = consumer.getRuleConsumer();
      if (ruleConsumer != null) {
        for (ArtProfileRule rule : rules) {
          rule.accept(
              classRule ->
                  ruleConsumer.acceptClassRule(
                      classRule.getClassReference(), classRule.getClassRuleInfo()),
              methodRule ->
                  ruleConsumer.acceptMethodRule(
                      methodRule.getMethodReference(), methodRule.getMethodRuleInfo()));
        }
      }
      consumer.finished(reporter);
    }
  }

  public static class Builder implements ArtProfileBuilder {

    private final DexItemFactory dexItemFactory;
    private final List<ArtProfileRule> rules = new ArrayList<>();

    Builder(DexItemFactory dexItemFactory) {
      this.dexItemFactory = dexItemFactory;
    }

    @Override
    public ArtProfileBuilder addClassRule(
        Consumer<ArtProfileClassRuleBuilder> classRuleBuilderConsumer) {
      ArtProfileClassRule.Builder classRuleBuilder = ArtProfileClassRule.builder(dexItemFactory);
      classRuleBuilderConsumer.accept(classRuleBuilder);
      rules.add(classRuleBuilder.build());
      return this;
    }

    @Override
    public ArtProfileBuilder addMethodRule(
        Consumer<ArtProfileMethodRuleBuilder> methodRuleBuilderConsumer) {
      ArtProfileMethodRule.Builder methodRuleBuilder = ArtProfileMethodRule.builder(dexItemFactory);
      methodRuleBuilderConsumer.accept(methodRuleBuilder);
      rules.add(methodRuleBuilder.build());
      return this;
    }

    public ArtProfile build() {
      return new ArtProfile(rules);
    }
  }
}
