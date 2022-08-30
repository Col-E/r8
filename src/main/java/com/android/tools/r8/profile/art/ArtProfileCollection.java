// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.InternalOptions;
import java.util.ArrayList;
import java.util.List;

public abstract class ArtProfileCollection {

  public static ArtProfileCollection createInitialArtProfileCollection(InternalOptions options) {
    List<ArtProfile> artProfiles = new ArrayList<>();
    for (ArtProfileInput input : options.getArtProfileOptions().getArtProfileInputs()) {
      ArtProfile.Builder artProfileBuilder = ArtProfile.builder(options.dexItemFactory());
      input.getArtProfile(artProfileBuilder);
      artProfiles.add(artProfileBuilder.build());
    }
    if (artProfiles.isEmpty()) {
      return empty();
    }
    return new NonEmptyArtProfileCollection(artProfiles);
  }

  public static EmptyArtProfileCollection empty() {
    return EmptyArtProfileCollection.getInstance();
  }

  public abstract ArtProfileCollection rewrittenWithLens(GraphLens lens);

  public abstract ArtProfileCollection rewrittenWithLens(
      NamingLens lens, DexItemFactory dexItemFactory);

  public abstract void supplyConsumers(AppView<?> appView);

  public abstract ArtProfileCollection withoutPrunedItems(PrunedItems prunedItems);
}
