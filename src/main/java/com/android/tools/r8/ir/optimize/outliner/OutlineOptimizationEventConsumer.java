// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.outliner;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.profile.art.rewriting.ArtProfileCollectionAdditions;
import com.android.tools.r8.profile.art.rewriting.ConcreteArtProfileCollectionAdditions;
import java.util.Collection;

public interface OutlineOptimizationEventConsumer {

  void acceptOutlineMethod(ProgramMethod method, Collection<ProgramMethod> contexts);

  static OutlineOptimizationEventConsumer create(
      ArtProfileCollectionAdditions collectionAdditions) {
    if (collectionAdditions.isNop()) {
      return empty();
    }
    return create(collectionAdditions.asConcrete());
  }

  static OutlineOptimizationEventConsumer create(
      ConcreteArtProfileCollectionAdditions collectionAdditions) {
    return (method, contexts) -> {
      for (ProgramMethod context : contexts) {
        collectionAdditions.applyIfContextIsInProfile(
            context,
            additionsBuilder -> additionsBuilder.addRule(method).addRule(method.getHolder()));
      }
    };
  }

  static EmptyOutlineOptimizationEventConsumer empty() {
    return EmptyOutlineOptimizationEventConsumer.getInstance();
  }

  class EmptyOutlineOptimizationEventConsumer implements OutlineOptimizationEventConsumer {

    private static final EmptyOutlineOptimizationEventConsumer INSTANCE =
        new EmptyOutlineOptimizationEventConsumer();

    private EmptyOutlineOptimizationEventConsumer() {}

    static EmptyOutlineOptimizationEventConsumer getInstance() {
      return INSTANCE;
    }

    @Override
    public void acceptOutlineMethod(ProgramMethod method, Collection<ProgramMethod> contexts) {
      // Intentionally empty.
    }
  }
}
