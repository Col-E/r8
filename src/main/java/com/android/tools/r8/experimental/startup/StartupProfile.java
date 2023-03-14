// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.TextInputStream;
import com.android.tools.r8.experimental.startup.profile.NonEmptyStartupProfile;
import com.android.tools.r8.experimental.startup.profile.StartupProfileClassRule;
import com.android.tools.r8.experimental.startup.profile.StartupProfileMethodRule;
import com.android.tools.r8.experimental.startup.profile.StartupProfileRule;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.profile.AbstractProfile;
import com.android.tools.r8.profile.AbstractProfileRule;
import com.android.tools.r8.profile.art.ArtProfileBuilderUtils;
import com.android.tools.r8.profile.art.HumanReadableArtProfileParser;
import com.android.tools.r8.profile.art.HumanReadableArtProfileParserBuilder;
import com.android.tools.r8.startup.StartupClassBuilder;
import com.android.tools.r8.startup.StartupMethodBuilder;
import com.android.tools.r8.startup.StartupProfileBuilder;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.startup.diagnostic.MissingStartupProfileItemsDiagnostic;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

public abstract class StartupProfile
    implements AbstractProfile<StartupProfileClassRule, StartupProfileMethodRule> {

  protected StartupProfile() {}

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(
      InternalOptions options,
      MissingStartupProfileItemsDiagnostic.Builder missingItemsDiagnosticBuilder,
      StartupProfileProvider startupProfileProvider) {
    return new Builder(options, missingItemsDiagnosticBuilder, startupProfileProvider);
  }

  public static StartupProfile createInitialStartupOrder(
      InternalOptions options, DexDefinitionSupplier definitions) {
    StartupProfile startupProfile = StartupProfile.parseStartupProfile(options, definitions);
    if (startupProfile == null || startupProfile.isEmpty()) {
      return empty();
    }
    StartupProfile.Builder builder = StartupProfile.builder();
    for (StartupProfileRule startupItem : startupProfile.getRules()) {
      builder.addStartupItem(startupItem);
    }
    return builder.build();
  }

  public static StartupProfile createInitialStartupOrderForD8(AppView<?> appView) {
    return createInitialStartupOrder(appView.options(), appView);
  }

  public static StartupProfile createInitialStartupOrderForR8(DexApplication application) {
    return createInitialStartupOrder(application.options, application);
  }

  public static StartupProfile empty() {
    return new EmptyStartupProfile();
  }

  public static StartupProfile merge(Collection<StartupProfile> startupProfiles) {
    Builder builder = builder();
    for (StartupProfile startupProfile : startupProfiles) {
      startupProfile.getRules().forEach(builder::addStartupItem);
    }
    return builder.build();
  }

  /**
   * Parses the supplied startup configuration, if any. The startup configuration is a list of class
   * and method descriptors.
   *
   * <p>Example:
   *
   * <pre>
   * Landroidx/compose/runtime/ComposerImpl;->updateValue(Ljava/lang/Object;)V
   * Landroidx/compose/runtime/ComposerImpl;->updatedNodeCount(I)I
   * Landroidx/compose/runtime/ComposerImpl;->validateNodeExpected()V
   * Landroidx/compose/runtime/CompositionImpl;->applyChanges()V
   * Landroidx/compose/runtime/ComposerKt;->findLocation(Ljava/util/List;I)I
   * Landroidx/compose/runtime/ComposerImpl;
   * </pre>
   */
  public static StartupProfile parseStartupProfile(
      InternalOptions options, DexDefinitionSupplier definitions) {
    if (!options.getStartupOptions().hasStartupProfileProviders()) {
      return null;
    }
    Collection<StartupProfileProvider> startupProfileProviders =
        options.getStartupOptions().getStartupProfileProviders();
    List<StartupProfile> startupProfiles = new ArrayList<>(startupProfileProviders.size());
    for (StartupProfileProvider startupProfileProvider : startupProfileProviders) {
      MissingStartupProfileItemsDiagnostic.Builder missingItemsDiagnosticBuilder =
          new MissingStartupProfileItemsDiagnostic.Builder(definitions)
              .setOrigin(startupProfileProvider.getOrigin());
      StartupProfile.Builder startupProfileBuilder =
          StartupProfile.builder(options, missingItemsDiagnosticBuilder, startupProfileProvider);
      startupProfileProvider.getStartupProfile(startupProfileBuilder);
      startupProfiles.add(startupProfileBuilder.build());
      if (missingItemsDiagnosticBuilder.hasMissingStartupItems()) {
        options.reporter.warning(missingItemsDiagnosticBuilder.build());
      }
    }
    return StartupProfile.merge(startupProfiles);
  }

  public abstract Collection<StartupProfileRule> getRules();

  public abstract boolean isEmpty();

  public abstract StartupProfile rewrittenWithLens(GraphLens graphLens);

  public abstract StartupProfile toStartupOrderForWriting(AppView<?> appView);

  public abstract StartupProfile withoutPrunedItems(
      PrunedItems prunedItems, SyntheticItems syntheticItems);

  public static class Builder
      implements AbstractProfile.Builder<
              StartupProfileClassRule, StartupProfileMethodRule, StartupProfile, Builder>,
          StartupProfileBuilder {

    private final DexItemFactory dexItemFactory;
    private final MissingStartupProfileItemsDiagnostic.Builder missingItemsDiagnosticBuilder;
    private Reporter reporter;
    private final StartupProfileProvider startupProfileProvider;

    private final LinkedHashMap<DexReference, StartupProfileRule> startupItems =
        new LinkedHashMap<>();

    Builder() {
      this.dexItemFactory = null;
      this.missingItemsDiagnosticBuilder = null;
      this.reporter = null;
      this.startupProfileProvider = null;
    }

    Builder(
        InternalOptions options,
        MissingStartupProfileItemsDiagnostic.Builder missingItemsDiagnosticBuilder,
        StartupProfileProvider startupProfileProvider) {
      this.dexItemFactory = options.dexItemFactory();
      this.missingItemsDiagnosticBuilder = missingItemsDiagnosticBuilder;
      this.reporter = options.reporter;
      this.startupProfileProvider = startupProfileProvider;
    }

    @Override
    public Builder addRule(AbstractProfileRule rule) {
      return addStartupItem(rule.asStartupProfileRule());
    }

    @Override
    public Builder addClassRule(StartupProfileClassRule classRule) {
      return addStartupItem(classRule);
    }

    @Override
    public Builder addMethodRule(StartupProfileMethodRule methodRule) {
      return addStartupItem(methodRule);
    }

    @Override
    public Builder addStartupClass(Consumer<StartupClassBuilder> startupClassBuilderConsumer) {
      StartupProfileClassRule.Builder startupClassBuilder =
          StartupProfileClassRule.builder(dexItemFactory);
      startupClassBuilderConsumer.accept(startupClassBuilder);
      StartupProfileClassRule startupClass = startupClassBuilder.build();
      if (missingItemsDiagnosticBuilder.registerStartupClass(startupClass)) {
        return this;
      }
      return addStartupItem(startupClass);
    }

    @Override
    public Builder addStartupMethod(Consumer<StartupMethodBuilder> startupMethodBuilderConsumer) {
      StartupProfileMethodRule.Builder startupMethodBuilder =
          StartupProfileMethodRule.builder(dexItemFactory);
      startupMethodBuilderConsumer.accept(startupMethodBuilder);
      StartupProfileMethodRule startupMethod = startupMethodBuilder.build();
      if (missingItemsDiagnosticBuilder.registerStartupMethod(startupMethod)) {
        return this;
      }
      return addStartupItem(startupMethod);
    }

    @Override
    public StartupProfileBuilder addHumanReadableArtProfile(
        TextInputStream textInputStream,
        Consumer<HumanReadableArtProfileParserBuilder> parserBuilderConsumer) {
      HumanReadableArtProfileParser.Builder parserBuilder =
          HumanReadableArtProfileParser.builder()
              .setReporter(reporter)
              .setProfileBuilder(
                  ArtProfileBuilderUtils.createBuilderForArtProfileToStartupProfileConversion(
                      this));
      parserBuilderConsumer.accept(parserBuilder);
      HumanReadableArtProfileParser parser = parserBuilder.build();
      parser.parse(textInputStream, startupProfileProvider.getOrigin());
      return this;
    }

    public Builder addStartupItem(StartupProfileRule startupItem) {
      startupItems.put(startupItem.getReference(), startupItem);
      return this;
    }

    public Builder apply(Consumer<Builder> consumer) {
      consumer.accept(this);
      return this;
    }

    public Builder setIgnoreWarnings() {
      return setReporter(null);
    }

    public Builder setReporter(Reporter reporter) {
      this.reporter = reporter;
      return this;
    }

    @Override
    public StartupProfile build() {
      if (startupItems.isEmpty()) {
        return empty();
      }
      return new NonEmptyStartupProfile(startupItems);
    }
  }
}
