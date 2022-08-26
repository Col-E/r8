// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup.profile;

import com.android.tools.r8.TextInputStream;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.profile.art.AlwaysTrueArtProfileRulePredicate;
import com.android.tools.r8.profile.art.ArtProfileBuilderUtils;
import com.android.tools.r8.profile.art.ArtProfileBuilderUtils.SyntheticToSyntheticContextGeneralization;
import com.android.tools.r8.profile.art.ArtProfileRulePredicate;
import com.android.tools.r8.profile.art.HumanReadableArtProfileParser;
import com.android.tools.r8.profile.art.HumanReadableArtProfileParserBuilder;
import com.android.tools.r8.startup.StartupClassBuilder;
import com.android.tools.r8.startup.StartupMethodBuilder;
import com.android.tools.r8.startup.StartupProfileBuilder;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.startup.SyntheticStartupMethodBuilder;
import com.android.tools.r8.startup.diagnostic.MissingStartupProfileItemsDiagnostic;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

public class StartupProfile {

  private final LinkedHashSet<StartupItem> startupItems;

  StartupProfile(LinkedHashSet<StartupItem> startupItems) {
    this.startupItems = startupItems;
  }

  public static Builder builder(
      InternalOptions options,
      MissingStartupProfileItemsDiagnostic.Builder missingItemsDiagnosticBuilder,
      StartupProfileProvider startupProfileProvider,
      SyntheticToSyntheticContextGeneralization syntheticToSyntheticContextGeneralization) {
    return new Builder(
        options,
        missingItemsDiagnosticBuilder,
        startupProfileProvider,
        syntheticToSyntheticContextGeneralization);
  }

  public static StartupProfile merge(Collection<StartupProfile> startupProfiles) {
    LinkedHashSet<StartupItem> mergedStartupItems = new LinkedHashSet<>();
    for (StartupProfile startupProfile : startupProfiles) {
      mergedStartupItems.addAll(startupProfile.getStartupItems());
    }
    return new StartupProfile(mergedStartupItems);
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
      InternalOptions options,
      DexDefinitionSupplier definitions,
      SyntheticToSyntheticContextGeneralization syntheticToSyntheticContextGeneralization) {
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
          StartupProfile.builder(
              options,
              missingItemsDiagnosticBuilder,
              startupProfileProvider,
              syntheticToSyntheticContextGeneralization);
      startupProfileProvider.getStartupProfile(startupProfileBuilder);
      startupProfiles.add(startupProfileBuilder.build());
      if (missingItemsDiagnosticBuilder.hasMissingStartupItems()) {
        options.reporter.warning(missingItemsDiagnosticBuilder.build());
      }
    }
    return StartupProfile.merge(startupProfiles);
  }

  public Collection<StartupItem> getStartupItems() {
    return startupItems;
  }

  public static class Builder implements StartupProfileBuilder {

    private final DexItemFactory dexItemFactory;
    private final MissingStartupProfileItemsDiagnostic.Builder missingItemsDiagnosticBuilder;
    private Reporter reporter;
    private final StartupProfileProvider startupProfileProvider;
    private final SyntheticToSyntheticContextGeneralization
        syntheticToSyntheticContextGeneralization;

    private final LinkedHashSet<StartupItem> startupItems = new LinkedHashSet<>();

    Builder(
        InternalOptions options,
        MissingStartupProfileItemsDiagnostic.Builder missingItemsDiagnosticBuilder,
        StartupProfileProvider startupProfileProvider,
        SyntheticToSyntheticContextGeneralization syntheticToSyntheticContextGeneralization) {
      this.dexItemFactory = options.dexItemFactory();
      this.missingItemsDiagnosticBuilder = missingItemsDiagnosticBuilder;
      this.reporter = options.reporter;
      this.startupProfileProvider = startupProfileProvider;
      this.syntheticToSyntheticContextGeneralization = syntheticToSyntheticContextGeneralization;
    }

    @Override
    public Builder addStartupClass(Consumer<StartupClassBuilder> startupClassBuilderConsumer) {
      StartupClass.Builder startupClassBuilder = StartupClass.builder(dexItemFactory);
      startupClassBuilderConsumer.accept(startupClassBuilder);
      StartupClass startupClass = startupClassBuilder.build();
      if (missingItemsDiagnosticBuilder.registerStartupClass(startupClass)) {
        return this;
      }
      return addStartupItem(startupClass);
    }

    @Override
    public Builder addStartupMethod(Consumer<StartupMethodBuilder> startupMethodBuilderConsumer) {
      StartupMethod.Builder startupMethodBuilder = StartupMethod.builder(dexItemFactory);
      startupMethodBuilderConsumer.accept(startupMethodBuilder);
      StartupMethod startupMethod = startupMethodBuilder.build();
      if (missingItemsDiagnosticBuilder.registerStartupMethod(startupMethod)) {
        return this;
      }
      return addStartupItem(startupMethod);
    }

    @Override
    public StartupProfileBuilder addSyntheticStartupMethod(
        Consumer<SyntheticStartupMethodBuilder> syntheticStartupMethodBuilderConsumer) {
      SyntheticStartupMethod.Builder syntheticStartupMethodBuilder =
          SyntheticStartupMethod.builder(dexItemFactory);
      syntheticStartupMethodBuilderConsumer.accept(syntheticStartupMethodBuilder);
      SyntheticStartupMethod syntheticStartupMethod = syntheticStartupMethodBuilder.build();
      if (missingItemsDiagnosticBuilder.registerSyntheticStartupMethod(syntheticStartupMethod)) {
        return this;
      }
      return addStartupItem(syntheticStartupMethod);
    }

    @Override
    public StartupProfileBuilder addHumanReadableArtProfile(
        TextInputStream textInputStream,
        Consumer<HumanReadableArtProfileParserBuilder> parserBuilderConsumer) {
      Box<ArtProfileRulePredicate> rulePredicateBox =
          new Box<>(new AlwaysTrueArtProfileRulePredicate());
      parserBuilderConsumer.accept(
          new HumanReadableArtProfileParserBuilder() {
            @Override
            public HumanReadableArtProfileParserBuilder setRulePredicate(
                ArtProfileRulePredicate rulePredicate) {
              rulePredicateBox.set(rulePredicate);
              return this;
            }
          });

      HumanReadableArtProfileParser parser =
          HumanReadableArtProfileParser.builder()
              .setReporter(reporter)
              .setProfileBuilder(
                  ArtProfileBuilderUtils.createBuilderForArtProfileToStartupProfileConversion(
                      this, rulePredicateBox.get(), syntheticToSyntheticContextGeneralization))
              .build();
      parser.parse(textInputStream, startupProfileProvider.getOrigin());
      return this;
    }

    private Builder addStartupItem(StartupItem startupItem) {
      this.startupItems.add(startupItem);
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

    public StartupProfile build() {
      return new StartupProfile(startupItems);
    }
  }
}
