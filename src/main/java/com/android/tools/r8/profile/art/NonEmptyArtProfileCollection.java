// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Timing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

public class NonEmptyArtProfileCollection extends ArtProfileCollection
    implements Iterable<ArtProfile> {

  private final List<ArtProfile> artProfiles;

  public NonEmptyArtProfileCollection(List<ArtProfile> artProfiles) {
    this.artProfiles = artProfiles;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public boolean isNonEmpty() {
    return true;
  }

  @Override
  public NonEmptyArtProfileCollection asNonEmpty() {
    return this;
  }

  public ArtProfile getLast() {
    return ListUtils.last(artProfiles);
  }

  @Override
  public Iterator<ArtProfile> iterator() {
    return artProfiles.iterator();
  }

  @Override
  public NonEmptyArtProfileCollection rewrittenWithLens(
      AppView<?> appView, GraphLens lens, Timing timing) {
    return timing.time(
        "Rewrite NonEmptyArtProfileCollection", () -> rewrittenWithLens(appView, lens));
  }

  private NonEmptyArtProfileCollection rewrittenWithLens(AppView<?> appView, GraphLens lens) {
    return map(artProfile -> artProfile.rewrittenWithLens(appView, lens));
  }

  @Override
  public NonEmptyArtProfileCollection rewrittenWithLens(AppView<?> appView, NamingLens lens) {
    assert !lens.isIdentityLens();
    return map(artProfile -> artProfile.rewrittenWithLens(appView, lens));
  }

  @Override
  public void supplyConsumers(AppView<?> appView) {
    if (appView.options().getArtProfileOptions().isCompletenessCheckForTestingEnabled()) {
      assert ArtProfileCompletenessChecker.verify(appView);
      ListUtils.removeLast(artProfiles);
      if (artProfiles.isEmpty()) {
        appView.setArtProfileCollection(ArtProfileCollection.empty());
        return;
      }
    }
    NonEmptyArtProfileCollection collection =
        appView.getNamingLens().isIdentityLens()
            ? this
            : rewrittenWithLens(appView, appView.getNamingLens());
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
  public ArtProfileCollection withoutMissingItems(AppView<?> appView) {
    return map(artProfile -> artProfile.withoutMissingItems(appView));
  }

  @Override
  public NonEmptyArtProfileCollection withoutPrunedItems(PrunedItems prunedItems, Timing timing) {
    timing.begin("Prune NonEmptyArtProfileCollection");
    NonEmptyArtProfileCollection result =
        map(artProfile -> artProfile.withoutPrunedItems(prunedItems));
    timing.end();
    return result;
  }

  private NonEmptyArtProfileCollection map(Function<ArtProfile, ArtProfile> fn) {
    List<ArtProfile> newArtProfiles = new ArrayList<>(artProfiles.size());
    for (ArtProfile artProfile : artProfiles) {
      newArtProfiles.add(fn.apply(artProfile));
    }
    return new NonEmptyArtProfileCollection(newArtProfiles);
  }
}
