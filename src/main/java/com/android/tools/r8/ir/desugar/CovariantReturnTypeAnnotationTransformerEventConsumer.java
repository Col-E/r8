// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.profile.art.rewriting.ArtProfileCollectionAdditions;
import com.android.tools.r8.profile.art.rewriting.ArtProfileRewritingCovariantReturnTypeAnnotationTransformerEventConsumer;

public interface CovariantReturnTypeAnnotationTransformerEventConsumer {

  void acceptCovariantReturnTypeBridgeMethod(ProgramMethod bridge, ProgramMethod target);

  static CovariantReturnTypeAnnotationTransformerEventConsumer create(
      ArtProfileCollectionAdditions artProfileCollectionAdditions) {
    if (artProfileCollectionAdditions.isNop()) {
      return empty();
    }
    return ArtProfileRewritingCovariantReturnTypeAnnotationTransformerEventConsumer.attach(
        artProfileCollectionAdditions, empty());
  }

  static EmptyCovariantReturnTypeAnnotationTransformerEventConsumer empty() {
    return EmptyCovariantReturnTypeAnnotationTransformerEventConsumer.getInstance();
  }

  class EmptyCovariantReturnTypeAnnotationTransformerEventConsumer
      implements CovariantReturnTypeAnnotationTransformerEventConsumer {

    private static final EmptyCovariantReturnTypeAnnotationTransformerEventConsumer INSTANCE =
        new EmptyCovariantReturnTypeAnnotationTransformerEventConsumer();

    private EmptyCovariantReturnTypeAnnotationTransformerEventConsumer() {}

    static EmptyCovariantReturnTypeAnnotationTransformerEventConsumer getInstance() {
      return INSTANCE;
    }

    @Override
    public void acceptCovariantReturnTypeBridgeMethod(ProgramMethod bridge, ProgramMethod target) {
      // Intentionally empty.
    }
  }
}
