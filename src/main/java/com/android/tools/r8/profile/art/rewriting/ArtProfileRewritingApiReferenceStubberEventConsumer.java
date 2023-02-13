// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.rewriting;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.androidapi.ApiReferenceStubberEventConsumer;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexProgramClass;

public class ArtProfileRewritingApiReferenceStubberEventConsumer
    implements ApiReferenceStubberEventConsumer {

  private final ConcreteArtProfileCollectionAdditions collectionAdditions;
  private final ApiReferenceStubberEventConsumer parent;

  private ArtProfileRewritingApiReferenceStubberEventConsumer(
      ConcreteArtProfileCollectionAdditions collectionAdditions,
      ApiReferenceStubberEventConsumer parent) {
    this.collectionAdditions = collectionAdditions;
    this.parent = parent;
  }

  public static ApiReferenceStubberEventConsumer attach(
      AppView<?> appView, ApiReferenceStubberEventConsumer eventConsumer) {
    if (appView.options().getArtProfileOptions().isIncludingApiReferenceStubs()) {
      ArtProfileCollectionAdditions artProfileCollectionAdditions =
          ArtProfileCollectionAdditions.create(appView);
      if (!artProfileCollectionAdditions.isNop()) {
        return new ArtProfileRewritingApiReferenceStubberEventConsumer(
            artProfileCollectionAdditions.asConcrete(), eventConsumer);
      }
    }
    return eventConsumer;
  }

  @Override
  public void acceptMockedLibraryClass(DexProgramClass mockClass, DexLibraryClass libraryClass) {
    parent.acceptMockedLibraryClass(mockClass, libraryClass);
  }

  @Override
  public void acceptMockedLibraryClassContext(
      DexProgramClass mockClass, DexLibraryClass libraryClass, DexProgramClass context) {
    collectionAdditions.applyIfContextIsInProfile(
        context,
        additions ->
            additions
                .addClassRule(mockClass)
                .addMethodRule(mockClass.getProgramClassInitializer(), emptyConsumer()));
    parent.acceptMockedLibraryClassContext(mockClass, libraryClass, context);
  }

  @Override
  public void finished(AppView<?> appView) {
    collectionAdditions.commit(appView);
    parent.finished(appView);
  }

  @Override
  public boolean isEmpty() {
    return false;
  }
}
