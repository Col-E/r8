// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.experimental.startup.profile.StartupItem;
import com.android.tools.r8.experimental.startup.profile.StartupProfile;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.profile.art.ArtProfileBuilderUtils.SyntheticToSyntheticContextGeneralization;
import com.android.tools.r8.startup.StartupProfileBuilder;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.startup.diagnostic.MissingStartupProfileItemsDiagnostic;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.UTF8TextInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

public class StartupProfileProviderUtils {

  public static StartupProfileProvider createFromHumanReadableArtProfile(Path path) {
    return new StartupProfileProvider() {

      @Override
      public void getStartupProfile(StartupProfileBuilder startupProfileBuilder) {
        try {
          startupProfileBuilder.addHumanReadableArtProfile(
              new UTF8TextInputStream(path), ConsumerUtils.emptyConsumer());
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      @Override
      public Origin getOrigin() {
        return new PathOrigin(path);
      }
    };
  }

  /** Serialize the given {@param startupProfileProvider} to a string for writing it to a dump. */
  public static String serializeToString(
      InternalOptions options, StartupProfileProvider startupProfileProvider) {
    // Do not report missing items.
    MissingStartupProfileItemsDiagnostic.Builder missingItemsDiagnosticBuilder =
        MissingStartupProfileItemsDiagnostic.Builder.nop();
    // Do not generalize synthetic items to their synthetic context.
    SyntheticToSyntheticContextGeneralization syntheticToSyntheticContextGeneralization =
        SyntheticToSyntheticContextGeneralization.createForD8();
    StartupProfile.Builder startupProfileBuilder =
        StartupProfile.builder(
            options,
            missingItemsDiagnosticBuilder,
            startupProfileProvider,
            syntheticToSyntheticContextGeneralization);
    // Do not report warnings for lines that cannot be parsed.
    startupProfileBuilder.setIgnoreWarnings();
    // Populate the startup profile builder.
    startupProfileProvider.getStartupProfile(startupProfileBuilder);
    // Serialize the startup items.
    StringBuilder resultBuilder = new StringBuilder();
    for (StartupItem startupItem : startupProfileBuilder.build().getStartupItems()) {
      resultBuilder.append(startupItem.serializeToString()).append('\n');
    }
    return resultBuilder.toString();
  }
}
