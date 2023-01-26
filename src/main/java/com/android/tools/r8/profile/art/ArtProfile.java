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
import com.android.tools.r8.utils.ThrowingConsumer;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class ArtProfile {

  private final Map<DexReference, ArtProfileRule> rules;

  ArtProfile(Map<DexReference, ArtProfileRule> rules) {
    this.rules = rules;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builderForInitialArtProfile(
      ArtProfileProvider artProfileProvider, InternalOptions options) {
    return new Builder(artProfileProvider, options);
  }

  public boolean containsClassRule(DexType type) {
    return rules.containsKey(type);
  }

  public boolean containsMethodRule(DexMethod method) {
    return rules.containsKey(method);
  }

  public <E extends Exception> void forEachRule(ThrowingConsumer<ArtProfileRule, E> ruleConsumer)
      throws E {
    for (ArtProfileRule rule : rules.values()) {
      ruleConsumer.accept(rule);
    }
  }

  public <E1 extends Exception, E2 extends Exception> void forEachRule(
      ThrowingConsumer<ArtProfileClassRule, E1> classRuleConsumer,
      ThrowingConsumer<ArtProfileMethodRule, E2> methodRuleConsumer)
      throws E1, E2 {
    for (ArtProfileRule rule : rules.values()) {
      rule.accept(classRuleConsumer, methodRuleConsumer);
    }
  }

  public ArtProfileClassRule getClassRule(DexType type) {
    return (ArtProfileClassRule) rules.get(type);
  }

  public ArtProfileMethodRule getMethodRule(DexMethod method) {
    return (ArtProfileMethodRule) rules.get(method);
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
    forEachRule(
        // Supply a factory method for creating a builder. If the current rule should be included in
        // the rewritten profile, the caller should call the provided builder factory method to
        // create a class rule builder. If two rules are mapped to the same reference, the same rule
        // builder is reused so that the two rules are merged into a single rule (with their flags
        // merged).
        classRule ->
            classTransformation.accept(
                classRule,
                newType ->
                    ruleBuilders
                        .computeIfAbsent(
                            newType,
                            ignoreKey(() -> ArtProfileClassRule.builder().setType(newType)))
                        .asClassRuleBuilder()),
        // As above.
        methodRule ->
            methodTransformation.accept(
                methodRule,
                newMethod ->
                    ruleBuilders
                        .computeIfAbsent(
                            newMethod,
                            ignoreKey(() -> ArtProfileMethodRule.builder().setMethod(newMethod)))
                        .asMethodRuleBuilder()));
    return builder().addRuleBuilders(ruleBuilders.values()).build();
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
        forEachRule(
            rule -> {
              rule.writeHumanReadableRuleString(outputStreamWriter);
              outputStreamWriter.write('\n');
            });
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void supplyRuleConsumer(ArtProfileRuleConsumer ruleConsumer) {
    forEachRule(
        classRule ->
            ruleConsumer.acceptClassRule(
                classRule.getClassReference(), classRule.getClassRuleInfo()),
        methodRule ->
            ruleConsumer.acceptMethodRule(
                methodRule.getMethodReference(), methodRule.getMethodRuleInfo()));
  }

  public static class Builder implements ArtProfileBuilder {

    private final ArtProfileProvider artProfileProvider;
    private final DexItemFactory dexItemFactory;
    private Reporter reporter;
    private final Map<DexReference, ArtProfileRule> rules = new LinkedHashMap<>();

    Builder() {
      this.artProfileProvider = null;
      this.dexItemFactory = null;
      this.reporter = null;
    }

    // Constructor for building the initial ART profile. The input is based on the Reference API, so
    // access to the DexItemFactory is needed for conversion into the internal DexReference.
    // Moreover, access to the Reporter is needed for diagnostics reporting.
    Builder(ArtProfileProvider artProfileProvider, InternalOptions options) {
      this.artProfileProvider = artProfileProvider;
      this.dexItemFactory = options.dexItemFactory();
      this.reporter = options.reporter;
    }

    public Builder addRule(ArtProfileRule rule) {
      assert !rules.containsKey(rule.getReference());
      rule.accept(
          classRule -> rules.put(classRule.getType(), classRule),
          methodRule -> rules.put(methodRule.getMethod(), methodRule));
      return this;
    }

    public Builder addRules(Collection<ArtProfileRule> rules) {
      rules.forEach(this::addRule);
      return this;
    }

    public Builder addRuleBuilders(Collection<ArtProfileRule.Builder> ruleBuilders) {
      ruleBuilders.forEach(ruleBuilder -> addRule(ruleBuilder.build()));
      return this;
    }

    @Override
    public Builder addClassRule(Consumer<ArtProfileClassRuleBuilder> classRuleBuilderConsumer) {
      ArtProfileClassRule.Builder classRuleBuilder = ArtProfileClassRule.builder(dexItemFactory);
      classRuleBuilderConsumer.accept(classRuleBuilder);
      return addRule(classRuleBuilder.build());
    }

    @Override
    public Builder addMethodRule(Consumer<ArtProfileMethodRuleBuilder> methodRuleBuilderConsumer) {
      ArtProfileMethodRule.Builder methodRuleBuilder = ArtProfileMethodRule.builder(dexItemFactory);
      methodRuleBuilderConsumer.accept(methodRuleBuilder);
      return addRule(methodRuleBuilder.build());
    }

    @Override
    public Builder addHumanReadableArtProfile(
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
