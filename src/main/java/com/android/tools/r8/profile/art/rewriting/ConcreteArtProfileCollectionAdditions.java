// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.rewriting;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.profile.art.ArtProfile;
import com.android.tools.r8.profile.art.ArtProfileCollection;
import com.android.tools.r8.profile.art.ArtProfileMethodRuleInfoImpl;
import com.android.tools.r8.profile.art.NonEmptyArtProfileCollection;
import com.android.tools.r8.profile.art.rewriting.ArtProfileAdditions.ArtProfileAdditionsBuilder;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class ConcreteArtProfileCollectionAdditions extends ArtProfileCollectionAdditions {

  private final List<ArtProfileAdditions> additionsCollection;

  private boolean committed = false;

  private ConcreteArtProfileCollectionAdditions(List<ArtProfileAdditions> additionsCollection) {
    this.additionsCollection = additionsCollection;
  }

  ConcreteArtProfileCollectionAdditions(NonEmptyArtProfileCollection artProfileCollection) {
    additionsCollection = new ArrayList<>();
    for (ArtProfile artProfile : artProfileCollection) {
      additionsCollection.add(new ArtProfileAdditions(artProfile));
    }
    assert !additionsCollection.isEmpty();
  }

  @Override
  public void addMethodIfContextIsInProfile(ProgramMethod method, ProgramMethod context) {
    applyIfContextIsInProfile(context, additionsBuilder -> additionsBuilder.addRule(method));
  }

  public void addMethodIfContextIsInProfile(
      ProgramMethod method,
      DexClassAndMethod context,
      Consumer<ArtProfileMethodRuleInfoImpl.Builder> methodRuleInfoBuilderConsumer) {
    if (context.isProgramMethod()) {
      applyIfContextIsInProfile(
          context.asProgramMethod(), additionsBuilder -> additionsBuilder.addRule(method));
    } else {
      apply(
          artProfileAdditions ->
              artProfileAdditions.addMethodRule(method, methodRuleInfoBuilderConsumer));
    }
  }

  public void addMethodAndHolderIfContextIsInProfile(ProgramMethod method, ProgramMethod context) {
    applyIfContextIsInProfile(
        context, additionsBuilder -> additionsBuilder.addRule(method).addRule(method.getHolder()));
  }

  void apply(Consumer<ArtProfileAdditions> additionsConsumer) {
    for (ArtProfileAdditions artProfileAdditions : additionsCollection) {
      additionsConsumer.accept(artProfileAdditions);
    }
  }

  void applyIfContextIsInProfile(
      ProgramDefinition context,
      Consumer<ArtProfileAdditions> additionsConsumer,
      Consumer<ArtProfileAdditionsBuilder> additionsBuilderConsumer) {
    if (context.isProgramClass()) {
      applyIfContextIsInProfile(context.asProgramClass(), additionsConsumer);
    } else {
      assert context.isProgramMethod();
      applyIfContextIsInProfile(context.asProgramMethod(), additionsBuilderConsumer);
    }
  }

  void applyIfContextIsInProfile(
      DexProgramClass context, Consumer<ArtProfileAdditions> additionsConsumer) {
    applyIfContextIsInProfile(context.getType(), additionsConsumer);
  }

  void applyIfContextIsInProfile(DexType type, Consumer<ArtProfileAdditions> additionsConsumer) {
    for (ArtProfileAdditions artProfileAdditions : additionsCollection) {
      artProfileAdditions.applyIfContextIsInProfile(type, additionsConsumer);
    }
  }

  public void applyIfContextIsInProfile(
      ProgramMethod context, Consumer<ArtProfileAdditionsBuilder> builderConsumer) {
    applyIfContextIsInProfile(context.getReference(), builderConsumer);
  }

  @Override
  public void applyIfContextIsInProfile(
      DexMethod context, Consumer<ArtProfileAdditionsBuilder> builderConsumer) {
    for (ArtProfileAdditions artProfileAdditions : additionsCollection) {
      artProfileAdditions.applyIfContextIsInProfile(context, builderConsumer);
    }
  }

  @Override
  public ConcreteArtProfileCollectionAdditions asConcrete() {
    return this;
  }

  @Override
  public void commit(AppView<?> appView) {
    assert !committed;
    if (hasAdditions()) {
      appView.setArtProfileCollection(createNewArtProfileCollection());
    }
    committed = true;
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

  @Override
  public ConcreteArtProfileCollectionAdditions rewriteMethodReferences(
      Function<DexMethod, DexMethod> methodFn) {
    List<ArtProfileAdditions> rewrittenAdditionsCollection =
        new ArrayList<>(additionsCollection.size());
    for (ArtProfileAdditions additions : additionsCollection) {
      rewrittenAdditionsCollection.add(additions.rewriteMethodReferences(methodFn));
    }
    return new ConcreteArtProfileCollectionAdditions(rewrittenAdditionsCollection);
  }

  @Override
  public ConcreteArtProfileCollectionAdditions setArtProfileCollection(
      ArtProfileCollection artProfileCollection) {
    assert artProfileCollection.isNonEmpty();
    Iterator<ArtProfile> artProfileIterator = artProfileCollection.asNonEmpty().iterator();
    for (ArtProfileAdditions additions : additionsCollection) {
      additions.setArtProfile(artProfileIterator.next());
    }
    return this;
  }

  @Override
  public boolean verifyIsCommitted() {
    assert committed;
    return true;
  }
}
