// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.profile.art.rewriting.ArtProfileCollectionAdditions;
import com.android.tools.r8.profile.art.rewriting.ConcreteArtProfileCollectionAdditions;

public interface ArgumentPropagatorSyntheticEventConsumer {

  void acceptInitializerArgumentClass(DexProgramClass clazz, ProgramMethod context);

  static ArgumentPropagatorSyntheticEventConsumer create(
      ArtProfileCollectionAdditions collectionAdditions) {
    if (collectionAdditions.isNop()) {
      return empty();
    }
    return create(collectionAdditions.asConcrete());
  }

  static ArgumentPropagatorSyntheticEventConsumer create(
      ConcreteArtProfileCollectionAdditions collectionAdditions) {
    return (clazz, context) ->
        collectionAdditions.applyIfContextIsInProfile(
            context, additionsBuilder -> additionsBuilder.addRule(clazz));
  }

  static ArgumentPropagatorSyntheticEventConsumer empty() {
    return EmptyArgumentPropagatorSyntheticEventConsumer.getInstance();
  }

  class EmptyArgumentPropagatorSyntheticEventConsumer
      implements ArgumentPropagatorSyntheticEventConsumer {

    private static EmptyArgumentPropagatorSyntheticEventConsumer INSTANCE =
        new EmptyArgumentPropagatorSyntheticEventConsumer();

    private EmptyArgumentPropagatorSyntheticEventConsumer() {}

    static EmptyArgumentPropagatorSyntheticEventConsumer getInstance() {
      return INSTANCE;
    }

    @Override
    public void acceptInitializerArgumentClass(DexProgramClass clazz, ProgramMethod context) {
      // Intentionally empty.
    }
  }
}
