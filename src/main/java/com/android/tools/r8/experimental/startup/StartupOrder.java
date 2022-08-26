// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.experimental.startup.profile.StartupItem;
import com.android.tools.r8.experimental.startup.profile.StartupProfile;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.profile.art.ArtProfileBuilderUtils.SyntheticToSyntheticContextGeneralization;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.InternalOptions;
import java.util.Collection;
import java.util.LinkedHashSet;

public abstract class StartupOrder {

  StartupOrder() {}

  public static StartupOrder createInitialStartupOrder(
      InternalOptions options,
      DexDefinitionSupplier definitions,
      SyntheticToSyntheticContextGeneralization syntheticToSyntheticContextGeneralization) {
    StartupProfile startupProfile =
        StartupProfile.parseStartupProfile(
            options, definitions, syntheticToSyntheticContextGeneralization);
    if (startupProfile == null || startupProfile.getStartupItems().isEmpty()) {
      return empty();
    }
    return new NonEmptyStartupOrder(new LinkedHashSet<>(startupProfile.getStartupItems()));
  }

  public static StartupOrder createInitialStartupOrderForD8(AppView<?> appView) {
    return createInitialStartupOrder(
        appView.options(), appView, SyntheticToSyntheticContextGeneralization.createForD8());
  }

  public static StartupOrder createInitialStartupOrderForR8(DexApplication application) {
    return createInitialStartupOrder(
        application.options, application, SyntheticToSyntheticContextGeneralization.createForR8());
  }

  public static StartupOrder empty() {
    return new EmptyStartupOrder();
  }

  public abstract boolean contains(DexMethod method, SyntheticItems syntheticItems);

  public abstract boolean contains(DexType type, SyntheticItems syntheticItems);

  public abstract Collection<StartupItem> getItems();

  public abstract boolean isEmpty();

  public abstract StartupOrder rewrittenWithLens(GraphLens graphLens);

  public abstract StartupOrder toStartupOrderForWriting(AppView<?> appView);

  public abstract StartupOrder withoutPrunedItems(
      PrunedItems prunedItems, SyntheticItems syntheticItems);
}
