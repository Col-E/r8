// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.rewriting;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.profile.AbstractProfileMethodRule;
import com.android.tools.r8.profile.art.ArtProfile;
import com.android.tools.r8.profile.art.ArtProfileCollection;
import com.android.tools.r8.profile.art.NonEmptyArtProfileCollection;
import com.android.tools.r8.profile.art.rewriting.ArtProfileAdditions;
import com.android.tools.r8.profile.rewriting.ProfileAdditions.ProfileAdditionsBuilder;
import com.android.tools.r8.profile.startup.profile.StartupProfile;
import com.android.tools.r8.profile.startup.rewriting.StartupProfileAdditions;
import com.android.tools.r8.utils.Box;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class ConcreteProfileCollectionAdditions extends ProfileCollectionAdditions {

  private final List<ArtProfileAdditions> additionsCollection;
  private final Box<StartupProfileAdditions> startupProfileAdditions;

  private boolean committed = false;

  private ConcreteProfileCollectionAdditions(
      List<ArtProfileAdditions> additionsCollection,
      Box<StartupProfileAdditions> startupProfileAdditions) {
    this.additionsCollection = additionsCollection;
    this.startupProfileAdditions = startupProfileAdditions;
  }

  ConcreteProfileCollectionAdditions(
      ArtProfileCollection artProfileCollection, StartupProfile startupProfile) {
    additionsCollection = new ArrayList<>();
    if (artProfileCollection.isNonEmpty()) {
      for (ArtProfile artProfile : artProfileCollection.asNonEmpty()) {
        additionsCollection.add(new ArtProfileAdditions(artProfile));
      }
      assert !additionsCollection.isEmpty();
    }
    startupProfileAdditions =
        new Box<>(startupProfile.isEmpty() ? null : new StartupProfileAdditions(startupProfile));
  }

  void accept(Consumer<ProfileAdditions<?, ?, ?, ?, ?, ?, ?, ?>> additionsConsumer) {
    for (ArtProfileAdditions additions : additionsCollection) {
      additionsConsumer.accept(additions);
    }
    startupProfileAdditions.accept(additionsConsumer);
  }

  @Override
  public void addMethodIfContextIsInProfile(ProgramMethod method, ProgramMethod context) {
    applyIfContextIsInProfile(context, additionsBuilder -> additionsBuilder.addRule(method));
  }

  public void addMethodIfContextIsInProfile(ProgramMethod method, DexClassAndMethod context) {
    if (context.isProgramMethod()) {
      addMethodIfContextIsInProfile(method, context.asProgramMethod());
    } else {
      accept(
          additions ->
              additions.addMethodRule(method, AbstractProfileMethodRule.Builder::setIsStartup));
    }
  }

  public void addMethodAndHolderIfContextIsInProfile(ProgramMethod method, ProgramMethod context) {
    applyIfContextIsInProfile(
        context, additionsBuilder -> additionsBuilder.addRule(method).addRule(method.getHolder()));
  }

  void applyIfContextIsInProfile(
      ProgramDefinition context, Consumer<ProfileAdditionsBuilder> builderConsumer) {
    if (context.isProgramClass()) {
      applyIfContextIsInProfile(context.asProgramClass(), builderConsumer);
    } else {
      assert context.isProgramMethod();
      applyIfContextIsInProfile(context.asProgramMethod(), builderConsumer);
    }
  }

  public void applyIfContextIsInProfile(
      DexProgramClass context, Consumer<ProfileAdditionsBuilder> builderConsumer) {
    accept(additions -> additions.applyIfContextIsInProfile(context.getType(), builderConsumer));
  }

  public void applyIfContextIsInProfile(
      ProgramMethod context, Consumer<ProfileAdditionsBuilder> builderConsumer) {
    applyIfContextIsInProfile(context.getReference(), builderConsumer);
  }

  @Override
  public void applyIfContextIsInProfile(
      DexMethod context, Consumer<ProfileAdditionsBuilder> builderConsumer) {
    accept(additions -> additions.applyIfContextIsInProfile(context, builderConsumer));
  }

  @Override
  public ConcreteProfileCollectionAdditions asConcrete() {
    return this;
  }

  @Override
  public void commit(AppView<?> appView) {
    assert !committed;
    if (hasArtProfileAdditions()) {
      appView.setArtProfileCollection(createNewArtProfileCollection());
    }
    if (hasStartupProfileAdditions()) {
      appView.setStartupProfile(createNewStartupProfile());
    }
    committed = true;
  }

  private ArtProfileCollection createNewArtProfileCollection() {
    assert hasArtProfileAdditions();
    List<ArtProfile> newArtProfiles = new ArrayList<>(additionsCollection.size());
    for (ArtProfileAdditions additions : additionsCollection) {
      newArtProfiles.add(additions.createNewProfile());
    }
    return new NonEmptyArtProfileCollection(newArtProfiles);
  }

  private StartupProfile createNewStartupProfile() {
    assert hasStartupProfileAdditions();
    return startupProfileAdditions.get().createNewProfile();
  }

  private boolean hasArtProfileAdditions() {
    return Iterables.any(additionsCollection, ProfileAdditions::hasAdditions);
  }

  private boolean hasStartupProfileAdditions() {
    return startupProfileAdditions.test(ProfileAdditions::hasAdditions);
  }

  @Override
  public ConcreteProfileCollectionAdditions rewriteMethodReferences(
      Function<DexMethod, DexMethod> methodFn) {
    List<ArtProfileAdditions> rewrittenAdditionsCollection =
        new ArrayList<>(additionsCollection.size());
    for (ArtProfileAdditions additions : additionsCollection) {
      rewrittenAdditionsCollection.add(additions.rewriteMethodReferences(methodFn));
    }
    Box<StartupProfileAdditions> rewrittenStartupProfileAdditions =
        startupProfileAdditions.rebuild(additions -> additions.rewriteMethodReferences(methodFn));
    return new ConcreteProfileCollectionAdditions(
        rewrittenAdditionsCollection, rewrittenStartupProfileAdditions);
  }

  @Override
  public ConcreteProfileCollectionAdditions setArtProfileCollection(
      ArtProfileCollection artProfileCollection) {
    if (artProfileCollection.isNonEmpty()) {
      Iterator<ArtProfile> artProfileIterator = artProfileCollection.asNonEmpty().iterator();
      for (ArtProfileAdditions additions : additionsCollection) {
        additions.setProfile(artProfileIterator.next());
      }
    } else {
      assert additionsCollection.isEmpty();
      assert startupProfileAdditions.isSet();
    }
    return this;
  }

  @Override
  public ProfileCollectionAdditions setStartupProfile(StartupProfile startupProfile) {
    startupProfileAdditions.accept(additions -> additions.setProfile(startupProfile));
    return this;
  }

  @Override
  public boolean verifyIsCommitted() {
    assert committed;
    return true;
  }
}
