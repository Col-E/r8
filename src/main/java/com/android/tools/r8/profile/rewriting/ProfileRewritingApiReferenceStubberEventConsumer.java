// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.rewriting;

import com.android.tools.r8.androidapi.ApiReferenceStubberEventConsumer;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexProgramClass;

public class ProfileRewritingApiReferenceStubberEventConsumer
    implements ApiReferenceStubberEventConsumer {

  private final ConcreteProfileCollectionAdditions collectionAdditions;
  private final ApiReferenceStubberEventConsumer parent;

  private ProfileRewritingApiReferenceStubberEventConsumer(
      ConcreteProfileCollectionAdditions collectionAdditions,
      ApiReferenceStubberEventConsumer parent) {
    this.collectionAdditions = collectionAdditions;
    this.parent = parent;
  }

  public static ApiReferenceStubberEventConsumer attach(
      AppView<?> appView, ApiReferenceStubberEventConsumer eventConsumer) {
    if (appView.options().getArtProfileOptions().isIncludingApiReferenceStubs()) {
      ProfileCollectionAdditions profileCollectionAdditions =
          ProfileCollectionAdditions.create(appView);
      if (!profileCollectionAdditions.isNop()) {
        return new ProfileRewritingApiReferenceStubberEventConsumer(
            profileCollectionAdditions.asConcrete(), eventConsumer);
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
        additionsBuilder ->
            additionsBuilder
                .addClassRule(mockClass.getType())
                .addMethodRule(mockClass.getProgramClassInitializer().getReference()));
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
