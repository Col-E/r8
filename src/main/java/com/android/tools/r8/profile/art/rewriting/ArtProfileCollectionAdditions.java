// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.rewriting;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.profile.art.ArtProfileCollection;
import com.android.tools.r8.profile.art.rewriting.ArtProfileAdditions.ArtProfileAdditionsBuilder;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Interface for adding (synthetic) items to an existing ArtProfileCollection.
 *
 * <p>The interface will be implemented by {@link NopArtProfileCollectionAdditions} when the
 * compilation does not contain any ART profiles, for minimal performance overhead.
 *
 * <p>When one or more ART profiles are present, this is implemented by {@link
 * ConcreteArtProfileCollectionAdditions}.
 */
public abstract class ArtProfileCollectionAdditions {

  public static ArtProfileCollectionAdditions create(AppView<?> appView) {
    ArtProfileCollection artProfileCollection = appView.getArtProfileCollection();
    if (artProfileCollection.isNonEmpty()) {
      return new ConcreteArtProfileCollectionAdditions(artProfileCollection.asNonEmpty());
    }
    return nop();
  }

  public static NopArtProfileCollectionAdditions nop() {
    return NopArtProfileCollectionAdditions.getInstance();
  }

  public abstract void addMethodIfContextIsInProfile(ProgramMethod method, ProgramMethod context);

  public abstract void applyIfContextIsInProfile(
      DexMethod context, Consumer<ArtProfileAdditionsBuilder> builderConsumer);

  public abstract void commit(AppView<?> appView);

  public boolean isNop() {
    return false;
  }

  public ConcreteArtProfileCollectionAdditions asConcrete() {
    return null;
  }

  public abstract ArtProfileCollectionAdditions rewriteMethodReferences(
      Function<DexMethod, DexMethod> methodFn);

  public abstract ArtProfileCollectionAdditions setArtProfileCollection(
      ArtProfileCollection artProfileCollection);

  public abstract boolean verifyIsCommitted();
}
