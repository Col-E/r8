// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.startup.profile;

import com.android.tools.r8.TextInputStream;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.origin.Origin;
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
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.Timing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class StartupProfile
    implements AbstractProfile<StartupProfileClassRule, StartupProfileMethodRule> {

  protected StartupProfile() {}

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builderWithCapacity(int capacity) {
    return new Builder(capacity);
  }

  public static Builder builder(
      InternalOptions options,
      MissingStartupProfileItemsDiagnostic.Builder missingItemsDiagnosticBuilder,
      StartupProfileProvider startupProfileProvider) {
    return new Builder(options, missingItemsDiagnosticBuilder, startupProfileProvider);
  }

  public static StartupProfile createInitialStartupProfile(
      InternalOptions options,
      Function<Origin, MissingStartupProfileItemsDiagnostic.Builder>
          missingItemsDiagnosticBuilderFactory) {
    StartupProfile startupProfile =
        StartupProfile.parseStartupProfile(options, missingItemsDiagnosticBuilderFactory);
    return startupProfile != null ? startupProfile : empty();
  }

  public static StartupProfile createInitialStartupProfileForD8(AppView<?> appView) {
    return createInitialStartupProfile(
        appView.options(),
        origin -> new MissingStartupProfileItemsDiagnostic.Builder(appView).setOrigin(origin));
  }

  public static StartupProfile createInitialStartupProfileForR8(DexApplication application) {
    // In R8 we expect a startup profile that matches the input app. Since profiles gathered from
    // running on ART will include synthetics, and these synthetics are not in the input app, we do
    // not raise warnings if some rules in the profile do not match anything.
    return createInitialStartupProfile(
        application.options, origin -> MissingStartupProfileItemsDiagnostic.Builder.nop());
  }

  public static StartupProfile empty() {
    return new EmptyStartupProfile();
  }

  public static StartupProfile merge(Collection<StartupProfile> startupProfiles) {
    Builder builder = builder();
    for (StartupProfile startupProfile : startupProfiles) {
      startupProfile.forEachRule(builder::addStartupItem);
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
      InternalOptions options,
      Function<Origin, MissingStartupProfileItemsDiagnostic.Builder>
          missingItemsDiagnosticBuilderFactory) {
    if (!options.getStartupOptions().hasStartupProfileProviders()) {
      return null;
    }
    Collection<StartupProfileProvider> startupProfileProviders =
        options.getStartupOptions().getStartupProfileProviders();
    List<StartupProfile> startupProfiles = new ArrayList<>(startupProfileProviders.size());
    for (StartupProfileProvider startupProfileProvider : startupProfileProviders) {
      MissingStartupProfileItemsDiagnostic.Builder missingItemsDiagnosticBuilder =
          missingItemsDiagnosticBuilderFactory.apply(startupProfileProvider.getOrigin());
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

  public abstract <E extends Exception> void forEachRule(
      ThrowingConsumer<? super StartupProfileRule, E> consumer) throws E;

  public abstract boolean isStartupClass(DexType type);

  public abstract boolean isEmpty();

  public abstract StartupProfile rewrittenWithLens(GraphLens graphLens, Timing timing);

  public abstract StartupProfile toStartupProfileForWriting(AppView<?> appView);

  public abstract StartupProfile withoutMissingItems(AppView<?> appView);

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

    private final LinkedHashMap<DexReference, StartupProfileRule> startupItems;

    Builder() {
      this.dexItemFactory = null;
      this.missingItemsDiagnosticBuilder = null;
      this.reporter = null;
      this.startupItems = new LinkedHashMap<>();
      this.startupProfileProvider = null;
    }

    Builder(int capacity) {
      this.dexItemFactory = null;
      this.missingItemsDiagnosticBuilder = null;
      this.reporter = null;
      this.startupItems = new LinkedHashMap<>(capacity);
      this.startupProfileProvider = null;
    }

    Builder(
        InternalOptions options,
        MissingStartupProfileItemsDiagnostic.Builder missingItemsDiagnosticBuilder,
        StartupProfileProvider startupProfileProvider) {
      this.dexItemFactory = options.dexItemFactory();
      this.missingItemsDiagnosticBuilder = missingItemsDiagnosticBuilder;
      this.reporter = options.reporter;
      this.startupItems = new LinkedHashMap<>();
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

    public int size() {
      return startupItems.size();
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
