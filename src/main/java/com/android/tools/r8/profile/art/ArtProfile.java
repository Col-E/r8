// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.TextInputStream;
import com.android.tools.r8.TextOutputStream;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.optimize.enums.EnumUnboxingLens;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.profile.AbstractProfile;
import com.android.tools.r8.profile.AbstractProfileRule;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.TriConsumer;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class ArtProfile implements AbstractProfile<ArtProfileClassRule, ArtProfileMethodRule> {

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

  @Override
  public boolean containsClassRule(DexType type) {
    return rules.containsKey(type);
  }

  @Override
  public boolean containsMethodRule(DexMethod method) {
    return rules.containsKey(method);
  }

  public <E extends Exception> void forEachRule(ThrowingConsumer<ArtProfileRule, E> ruleConsumer)
      throws E {
    for (ArtProfileRule rule : rules.values()) {
      ruleConsumer.accept(rule);
    }
  }

  @Override
  public <E1 extends Exception, E2 extends Exception> void forEachRule(
      ThrowingConsumer<? super ArtProfileClassRule, E1> classRuleConsumer,
      ThrowingConsumer<? super ArtProfileMethodRule, E2> methodRuleConsumer)
      throws E1, E2 {
    for (ArtProfileRule rule : rules.values()) {
      rule.accept(classRuleConsumer, methodRuleConsumer);
    }
  }

  @Override
  public ArtProfileClassRule getClassRule(DexType type) {
    return (ArtProfileClassRule) rules.get(type);
  }

  @Override
  public ArtProfileMethodRule getMethodRule(DexMethod method) {
    return (ArtProfileMethodRule) rules.get(method);
  }

  public int size() {
    return rules.size();
  }

  public ArtProfile rewrittenWithLens(AppView<?> appView, GraphLens lens) {
    if (lens.isEnumUnboxerLens()) {
      return rewrittenWithLens(appView, lens.asEnumUnboxerLens());
    }
    return transform(
        (classRule, classRuleBuilderFactory) -> {
          DexType newClassRule = lens.lookupType(classRule.getType());
          assert newClassRule.isClassType();
          classRuleBuilderFactory.accept(newClassRule);
        },
        (methodRule, classRuleBuilderFactory, methodRuleBuilderFactory) ->
            methodRuleBuilderFactory
                .apply(lens.getRenamedMethodSignature(methodRule.getMethod()))
                .acceptMethodRuleInfoBuilder(
                    methodRuleInfoBuilder ->
                        methodRuleInfoBuilder.merge(methodRule.getMethodRuleInfo())));
  }

  public ArtProfile rewrittenWithLens(AppView<?> appView, EnumUnboxingLens lens) {
    return transform(
        (classRule, classRuleBuilderFactory) -> {
          DexType newClassRule = lens.lookupType(classRule.getType());
          if (newClassRule.isClassType()) {
            classRuleBuilderFactory.accept(newClassRule);
          } else {
            assert newClassRule.isIntType();
          }
        },
        (methodRule, classRuleBuilderFactory, methodRuleBuilderFactory) -> {
          DexMethod newMethod = lens.getRenamedMethodSignature(methodRule.getMethod());
          // When moving non-synthetic methods from an enum class to its enum utility class we also
          // add a rule for the utility class.
          if (newMethod.getHolderType() != methodRule.getMethod().getHolderType()) {
            assert appView
                .getSyntheticItems()
                .isSyntheticOfKind(
                    newMethod.getHolderType(), naming -> naming.ENUM_UNBOXING_LOCAL_UTILITY_CLASS);
            classRuleBuilderFactory.accept(newMethod.getHolderType());
          }
          methodRuleBuilderFactory
              .apply(newMethod)
              .acceptMethodRuleInfoBuilder(
                  methodRuleInfoBuilder ->
                      methodRuleInfoBuilder.merge(methodRule.getMethodRuleInfo()));
        });
  }

  public ArtProfile rewrittenWithLens(AppView<?> appView, NamingLens lens) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    assert !lens.isIdentityLens();
    return transform(
        (classRule, classRuleBuilderFactory) ->
            classRuleBuilderFactory.accept(lens.lookupType(classRule.getType(), dexItemFactory)),
        (methodRule, classRuleBuilderFactory, methodRuleBuilderFactory) ->
            methodRuleBuilderFactory
                .apply(lens.lookupMethod(methodRule.getMethod(), dexItemFactory))
                .acceptMethodRuleInfoBuilder(
                    methodRuleInfoBuilder ->
                        methodRuleInfoBuilder.merge(methodRule.getMethodRuleInfo())));
  }

  public ArtProfile withoutMissingItems(AppView<?> appView) {
    AppInfo appInfo = appView.appInfo();
    return transform(
        (classRule, classRuleBuilderFactory) -> {
          if (appInfo.hasDefinitionForWithoutExistenceAssert(classRule.getType())) {
            classRuleBuilderFactory.accept(classRule.getType());
          }
        },
        (methodRule, classRuleBuilderFactory, methodRuleBuilderFactory) -> {
          DexClass clazz =
              appInfo.definitionForWithoutExistenceAssert(methodRule.getMethod().getHolderType());
          if (methodRule.getMethod().isDefinedOnClass(clazz)) {
            methodRuleBuilderFactory
                .apply(methodRule.getMethod())
                .acceptMethodRuleInfoBuilder(
                    methodRuleInfoBuilder ->
                        methodRuleInfoBuilder.merge(methodRule.getMethodRuleInfo()));
          }
        });
  }

  public ArtProfile withoutPrunedItems(PrunedItems prunedItems) {
    rules.keySet().removeIf(prunedItems::isRemoved);
    return this;
  }

  private ArtProfile transform(
      BiConsumer<ArtProfileClassRule, Consumer<DexType>> classTransformation,
      TriConsumer<
              ArtProfileMethodRule,
              Consumer<DexType>,
              Function<DexMethod, ArtProfileMethodRule.Builder>>
          methodTransformation) {
    Map<DexReference, ArtProfileRule.Builder> ruleBuilders = new LinkedHashMap<>();
    Consumer<DexType> classRuleBuilderFactory =
        newType ->
            ruleBuilders
                .computeIfAbsent(
                    newType, ignoreKey(() -> ArtProfileClassRule.builder().setType(newType)))
                .asClassRuleBuilder();
    Function<DexMethod, ArtProfileMethodRule.Builder> methodRuleBuilderFactory =
        newMethod ->
            ruleBuilders
                .computeIfAbsent(
                    newMethod, ignoreKey(() -> ArtProfileMethodRule.builder().setMethod(newMethod)))
                .asMethodRuleBuilder();
    forEachRule(
        // Supply a factory method for creating a builder. If the current rule should be included in
        // the rewritten profile, the caller should call the provided builder factory method to
        // create a class rule builder. If two rules are mapped to the same reference, the same rule
        // builder is reused so that the two rules are merged into a single rule (with their flags
        // merged).
        classRule -> classTransformation.accept(classRule, classRuleBuilderFactory),
        // As above.
        methodRule ->
            methodTransformation.accept(
                methodRule, classRuleBuilderFactory, methodRuleBuilderFactory));
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

  public static class Builder
      implements ArtProfileBuilder,
          AbstractProfile.Builder<ArtProfileClassRule, ArtProfileMethodRule, ArtProfile, Builder> {

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

    @Override
    public Builder addRule(AbstractProfileRule rule) {
      return addRule(rule.asArtProfileRule());
    }

    @Override
    public Builder addClassRule(ArtProfileClassRule classRule) {
      rules.put(classRule.getType(), classRule);
      return this;
    }

    @Override
    public Builder addMethodRule(ArtProfileMethodRule methodRule) {
      rules.compute(
          methodRule.getReference(),
          (reference, existingRule) -> {
            if (existingRule == null) {
              return methodRule;
            }
            ArtProfileMethodRule existingMethodRule = (ArtProfileMethodRule) existingRule;
            return ArtProfileMethodRule.builder()
                .setMethod(methodRule.getMethod())
                .join(methodRule)
                .join(existingMethodRule)
                .build();
          });
      return this;
    }

    public Builder addRule(ArtProfileRule rule) {
      return rule.apply(this::addClassRule, this::addMethodRule);
    }

    public Builder addRuleBuilders(Collection<ArtProfileRule.Builder> ruleBuilders) {
      ruleBuilders.forEach(ruleBuilder -> addRule(ruleBuilder.build()));
      return this;
    }

    @Override
    public Builder addClassRule(Consumer<ArtProfileClassRuleBuilder> classRuleBuilderConsumer) {
      ArtProfileClassRule.Builder classRuleBuilder = ArtProfileClassRule.builder(dexItemFactory);
      classRuleBuilderConsumer.accept(classRuleBuilder);
      return addClassRule(classRuleBuilder.build());
    }

    @Override
    public Builder addMethodRule(Consumer<ArtProfileMethodRuleBuilder> methodRuleBuilderConsumer) {
      ArtProfileMethodRule.Builder methodRuleBuilder = ArtProfileMethodRule.builder(dexItemFactory);
      methodRuleBuilderConsumer.accept(methodRuleBuilder);
      return addMethodRule(methodRuleBuilder.build());
    }

    @Override
    public Builder addHumanReadableArtProfile(
        TextInputStream textInputStream,
        Consumer<HumanReadableArtProfileParserBuilder> parserBuilderConsumer) {
      HumanReadableArtProfileParser.Builder parserBuilder =
          HumanReadableArtProfileParser.builder()
              .setDiagnosticConsumer(reporter::info)
              .setReporter(reporter)
              .setProfileBuilder(this);
      parserBuilderConsumer.accept(parserBuilder);
      HumanReadableArtProfileParser parser = parserBuilder.build();
      parser.parse(textInputStream, artProfileProvider.getOrigin());
      return this;
    }

    @Override
    public ArtProfile build() {
      return new ArtProfile(rules);
    }
  }
}
