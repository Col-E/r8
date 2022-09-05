// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.TextInputStream;
import com.android.tools.r8.TextOutputStream;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class ArtProfile {

  private final List<ArtProfileRule> rules;

  ArtProfile(List<ArtProfileRule> rules) {
    assert !rules.isEmpty();
    this.rules = rules;
  }

  public static Builder builder(ArtProfileProvider artProfileProvider, InternalOptions options) {
    return new Builder(artProfileProvider, options);
  }

  public ArtProfile rewrittenWithLens(GraphLens lens) {
    return transform(
        (classRule, builderFactory) -> builderFactory.accept(lens.lookupType(classRule.getType())),
        (methodRule, builderFactory) ->
            builderFactory
                .apply(lens.getRenamedMethodSignature(methodRule.getMethod()))
                .acceptMethodRuleInfoBuilder(
                    methodRuleInfoBuilder ->
                        methodRuleInfoBuilder.merge(methodRule.getMethodRuleInfo())));
  }

  public ArtProfile rewrittenWithLens(NamingLens lens, DexItemFactory dexItemFactory) {
    assert !lens.isIdentityLens();
    return transform(
        (classRule, builderFactory) ->
            builderFactory.accept(lens.lookupType(classRule.getType(), dexItemFactory)),
        (methodRule, builderFactory) ->
            builderFactory
                .apply(lens.lookupMethod(methodRule.getMethod(), dexItemFactory))
                .acceptMethodRuleInfoBuilder(
                    methodRuleInfoBuilder ->
                        methodRuleInfoBuilder.merge(methodRule.getMethodRuleInfo())));
  }

  public ArtProfile withoutPrunedItems(PrunedItems prunedItems) {
    return transform(
        (classRule, builderFactory) -> {
          if (!prunedItems.isRemoved(classRule.getType())) {
            builderFactory.accept(classRule.getType());
          }
        },
        (methodRule, builderFactory) -> {
          if (!prunedItems.isRemoved(methodRule.getMethod())) {
            builderFactory
                .apply(methodRule.getMethod())
                .acceptMethodRuleInfoBuilder(
                    methodRuleInfoBuilder ->
                        methodRuleInfoBuilder.merge(methodRule.getMethodRuleInfo()));
          }
        });
  }

  private ArtProfile transform(
      BiConsumer<ArtProfileClassRule, Consumer<DexType>> classTransformation,
      BiConsumer<ArtProfileMethodRule, Function<DexMethod, ArtProfileMethodRule.Builder>>
          methodTransformation) {
    Map<DexReference, ArtProfileRule.Builder> ruleBuilders = new LinkedHashMap<>();
    for (ArtProfileRule rule : rules) {
      if (rule.isClassRule()) {
        // Supply a factory method for creating a builder. If the current rule should be included in
        // the rewritten profile, the caller should call the provided builder factory method to
        // create a class rule builder. If two rules are mapped to the same reference, the same rule
        // builder is reused so that the two rules are merged into a single rule (with their flags
        // merged).
        classTransformation.accept(
            rule.asClassRule(),
            newType ->
                ruleBuilders
                    .computeIfAbsent(
                        newType, ignoreKey(() -> ArtProfileClassRule.builder().setType(newType)))
                    .asClassRuleBuilder());
      } else {
        // As above.
        assert rule.isMethodRule();
        methodTransformation.accept(
            rule.asMethodRule(),
            newMethod ->
                ruleBuilders
                    .computeIfAbsent(
                        newMethod,
                        ignoreKey(() -> ArtProfileMethodRule.builder().setMethod(newMethod)))
                    .asMethodRuleBuilder());
      }
    }
    ImmutableList.Builder<ArtProfileRule> newRules =
        ImmutableList.builderWithExpectedSize(ruleBuilders.size());
    ruleBuilders.values().forEach(ruleBuilder -> newRules.add(ruleBuilder.build()));
    return new ArtProfile(newRules.build());
  }

  public void supplyConsumer(ArtProfileConsumer consumer, Reporter reporter) {
    if (consumer != null) {
      TextOutputStream textOutputStream = consumer.getHumanReadableArtProfileConsumer();
      if (textOutputStream != null) {
        supplyHumanReadableArtProfileConsumer(textOutputStream);
      }
      ArtProfileRuleConsumer ruleConsumer = consumer.getRuleConsumer();
      if (ruleConsumer != null) {
        supplyRuleConsumer(ruleConsumer);
      }
      consumer.finished(reporter);
    }
  }

  private void supplyHumanReadableArtProfileConsumer(TextOutputStream textOutputStream) {
    try {
      try (OutputStreamWriter outputStreamWriter =
          new OutputStreamWriter(
              textOutputStream.getOutputStream(), textOutputStream.getCharset())) {
        for (ArtProfileRule rule : rules) {
          rule.writeHumanReadableRuleString(outputStreamWriter);
          outputStreamWriter.write('\n');
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void supplyRuleConsumer(ArtProfileRuleConsumer ruleConsumer) {
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

  public static class Builder implements ArtProfileBuilder {

    private final ArtProfileProvider artProfileProvider;
    private final DexItemFactory dexItemFactory;
    private Reporter reporter;
    private final List<ArtProfileRule> rules = new ArrayList<>();

    Builder(ArtProfileProvider artProfileProvider, InternalOptions options) {
      this.artProfileProvider = artProfileProvider;
      this.dexItemFactory = options.dexItemFactory();
      this.reporter = options.reporter;
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

    @Override
    public ArtProfileBuilder addHumanReadableArtProfile(
        TextInputStream textInputStream,
        Consumer<HumanReadableArtProfileParserBuilder> parserBuilderConsumer) {
      HumanReadableArtProfileParser.Builder parserBuilder =
          HumanReadableArtProfileParser.builder().setReporter(reporter).setProfileBuilder(this);
      parserBuilderConsumer.accept(parserBuilder);
      HumanReadableArtProfileParser parser = parserBuilder.build();
      parser.parse(textInputStream, artProfileProvider.getOrigin());
      return this;
    }

    public ArtProfile build() {
      return new ArtProfile(rules);
    }
  }
}
