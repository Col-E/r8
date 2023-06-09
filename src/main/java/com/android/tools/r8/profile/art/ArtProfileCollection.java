// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class ArtProfileCollection {

  public static ArtProfileCollection createInitialArtProfileCollection(
      AppInfo appInfo, InternalOptions options) {
    ArtProfileOptions artProfileOptions = options.getArtProfileOptions();
    Collection<ArtProfileProvider> artProfileProviders = artProfileOptions.getArtProfileProviders();
    List<ArtProfile> artProfiles =
        new ArrayList<>(
            artProfileProviders.size()
                + BooleanUtils.intValue(artProfileOptions.isCompletenessCheckForTestingEnabled()));
    for (ArtProfileProvider artProfileProvider : artProfileProviders) {
      ArtProfile.Builder artProfileBuilder =
          ArtProfile.builderForInitialArtProfile(artProfileProvider, options);
      artProfileProvider.getArtProfile(artProfileBuilder);
      artProfiles.add(artProfileBuilder.build());
    }
    if (artProfileOptions.isCompletenessCheckForTestingEnabled()) {
      artProfiles.add(createCompleteArtProfile(appInfo));
    }
    if (artProfiles.isEmpty()) {
      return empty();
    }
    return new NonEmptyArtProfileCollection(artProfiles);
  }

  private static ArtProfile createCompleteArtProfile(AppInfo appInfo) {
    ArtProfile.Builder artProfileBuilder = ArtProfile.builder();
    for (DexProgramClass clazz : appInfo.classesWithDeterministicOrder()) {
      artProfileBuilder.addClassRule(
          ArtProfileClassRule.builder().setType(clazz.getType()).build());
      clazz.forEachMethod(
          method ->
              artProfileBuilder.addMethodRule(
                  ArtProfileMethodRule.builder()
                      .setMethod(method.getReference())
                      .acceptMethodRuleInfoBuilder(
                          methodRuleInfoBuilder ->
                              methodRuleInfoBuilder.setIsHot().setIsStartup().setIsPostStartup())
                      .build()));
    }
    return artProfileBuilder.build();
  }

  public static EmptyArtProfileCollection empty() {
    return EmptyArtProfileCollection.getInstance();
  }

  public abstract boolean isEmpty();

  public abstract boolean isNonEmpty();

  public abstract NonEmptyArtProfileCollection asNonEmpty();

  public abstract ArtProfileCollection rewrittenWithLens(
      AppView<?> appView, GraphLens lens, Timing timing);

  public abstract ArtProfileCollection rewrittenWithLens(AppView<?> appView, NamingLens lens);

  public abstract void supplyConsumers(AppView<?> appView);

  public abstract ArtProfileCollection withoutMissingItems(AppView<?> appView);

  public abstract ArtProfileCollection withoutPrunedItems(PrunedItems prunedItems);
}
