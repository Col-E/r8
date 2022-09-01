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
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Function;

public class NonEmptyArtProfileCollection extends ArtProfileCollection {

  private final Collection<ArtProfile> artProfiles;

  NonEmptyArtProfileCollection(Collection<ArtProfile> artProfiles) {
    this.artProfiles = artProfiles;
  }

  @Override
  public NonEmptyArtProfileCollection rewrittenWithLens(GraphLens lens) {
    return map(artProfile -> artProfile.rewrittenWithLens(lens));
  }

  @Override
  public NonEmptyArtProfileCollection rewrittenWithLens(
      NamingLens lens, DexItemFactory dexItemFactory) {
    assert !lens.isIdentityLens();
    return map(artProfile -> artProfile.rewrittenWithLens(lens, dexItemFactory));
  }

  @Override
  public void supplyConsumers(AppView<?> appView) {
    NonEmptyArtProfileCollection collection =
        appView.getNamingLens().isIdentityLens()
            ? this
            : rewrittenWithLens(appView.getNamingLens(), appView.dexItemFactory());
    InternalOptions options = appView.options();
    Collection<ArtProfileForRewriting> inputs =
        options.getArtProfileOptions().getArtProfilesForRewriting();
    assert !inputs.isEmpty();
    assert collection.artProfiles.size() == inputs.size();
    Iterator<ArtProfileForRewriting> inputIterator = inputs.iterator();
    for (ArtProfile artProfile : collection.artProfiles) {
      ArtProfileForRewriting input = inputIterator.next();
      artProfile.supplyConsumer(input.getResidualArtProfileConsumer(), options.reporter);
    }
  }

  @Override
  public NonEmptyArtProfileCollection withoutPrunedItems(PrunedItems prunedItems) {
    return map(artProfile -> artProfile.withoutPrunedItems(prunedItems));
  }

  private NonEmptyArtProfileCollection map(Function<ArtProfile, ArtProfile> fn) {
    ImmutableList.Builder<ArtProfile> newArtProfiles =
        ImmutableList.builderWithExpectedSize(artProfiles.size());
    for (ArtProfile artProfile : artProfiles) {
      newArtProfiles.add(fn.apply(artProfile));
    }
    return new NonEmptyArtProfileCollection(newArtProfiles.build());
  }
}
