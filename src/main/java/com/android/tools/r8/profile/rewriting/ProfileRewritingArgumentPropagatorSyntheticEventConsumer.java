// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.rewriting;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.argumentpropagation.ArgumentPropagatorSyntheticEventConsumer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class ProfileRewritingArgumentPropagatorSyntheticEventConsumer
    implements ArgumentPropagatorSyntheticEventConsumer {

  private final ConcreteProfileCollectionAdditions additionsCollection;
  private final ArgumentPropagatorSyntheticEventConsumer parent;

  private ProfileRewritingArgumentPropagatorSyntheticEventConsumer(
      ConcreteProfileCollectionAdditions additionsCollection,
      ArgumentPropagatorSyntheticEventConsumer parent) {
    this.additionsCollection = additionsCollection;
    this.parent = parent;
  }

  public static ArgumentPropagatorSyntheticEventConsumer attach(
      AppView<AppInfoWithLiveness> appView,
      ArgumentPropagatorSyntheticEventConsumer eventConsumer) {
    ProfileCollectionAdditions additionsCollection = ProfileCollectionAdditions.create(appView);
    if (additionsCollection.isNop()) {
      return eventConsumer;
    }
    return new ProfileRewritingArgumentPropagatorSyntheticEventConsumer(
        additionsCollection.asConcrete(), eventConsumer);
  }

  @Override
  public void acceptInitializerArgumentClass(DexProgramClass clazz, ProgramMethod context) {
    additionsCollection.applyIfContextIsInProfile(
        context, additionsBuilder -> additionsBuilder.addRule(clazz));
    parent.acceptInitializerArgumentClass(clazz, context);
  }

  @Override
  public void finished(AppView<AppInfoWithLiveness> appView) {
    additionsCollection.commit(appView);
    parent.finished(appView);
  }
}
