// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.rewriting;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.profile.art.ArtProfile;
import com.android.tools.r8.profile.art.ArtProfileCollection;
import com.android.tools.r8.profile.art.NonEmptyArtProfileCollection;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;

public class ConcreteArtProfileCollectionAdditions extends ArtProfileCollectionAdditions {

  private final List<ArtProfileAdditions> additionsCollection = new ArrayList<>();

  ConcreteArtProfileCollectionAdditions(NonEmptyArtProfileCollection artProfileCollection) {
    for (ArtProfile artProfile : artProfileCollection) {
      additionsCollection.add(new ArtProfileAdditions(artProfile));
    }
    assert !additionsCollection.isEmpty();
  }

  void addRulesIfContextIsInProfile(DexClassAndMethod context, ProgramDefinition... definitions) {
    for (ArtProfileAdditions artProfileAdditions : additionsCollection) {
      artProfileAdditions.addRulesIfContextIsInProfile(context, definitions);
    }
  }

  // Specialization of the above method to avoid redundant varargs array creation.
  void addRulesIfContextIsInProfile(DexClassAndMethod context, ProgramDefinition definition) {
    for (ArtProfileAdditions artProfileAdditions : additionsCollection) {
      artProfileAdditions.addRulesIfContextIsInProfile(context, definition);
    }
  }

  @Override
  ConcreteArtProfileCollectionAdditions asConcrete() {
    return this;
  }

  @Override
  public void commit(AppView<?> appView) {
    if (hasAdditions()) {
      appView.setArtProfileCollection(createNewArtProfileCollection());
    }
  }

  private ArtProfileCollection createNewArtProfileCollection() {
    assert hasAdditions();
    List<ArtProfile> newArtProfiles = new ArrayList<>(additionsCollection.size());
    for (ArtProfileAdditions additions : additionsCollection) {
      newArtProfiles.add(additions.createNewArtProfile());
    }
    return new NonEmptyArtProfileCollection(newArtProfiles);
  }

  private boolean hasAdditions() {
    return Iterables.any(additionsCollection, ArtProfileAdditions::hasAdditions);
  }
}
