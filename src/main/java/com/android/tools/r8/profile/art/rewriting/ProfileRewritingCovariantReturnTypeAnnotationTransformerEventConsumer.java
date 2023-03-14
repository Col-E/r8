// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.rewriting;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CovariantReturnTypeAnnotationTransformerEventConsumer;

public class ProfileRewritingCovariantReturnTypeAnnotationTransformerEventConsumer
    implements CovariantReturnTypeAnnotationTransformerEventConsumer {

  private final ConcreteProfileCollectionAdditions additionsCollection;
  private final CovariantReturnTypeAnnotationTransformerEventConsumer parent;

  private ProfileRewritingCovariantReturnTypeAnnotationTransformerEventConsumer(
      ConcreteProfileCollectionAdditions additionsCollection,
      CovariantReturnTypeAnnotationTransformerEventConsumer parent) {
    this.additionsCollection = additionsCollection;
    this.parent = parent;
  }

  public static CovariantReturnTypeAnnotationTransformerEventConsumer attach(
      ProfileCollectionAdditions additionsCollection,
      CovariantReturnTypeAnnotationTransformerEventConsumer eventConsumer) {
    if (additionsCollection.isNop()) {
      return eventConsumer;
    }
    return new ProfileRewritingCovariantReturnTypeAnnotationTransformerEventConsumer(
        additionsCollection.asConcrete(), eventConsumer);
  }

  @Override
  public void acceptCovariantReturnTypeBridgeMethod(ProgramMethod bridge, ProgramMethod target) {
    additionsCollection.addMethodIfContextIsInProfile(bridge, target);
    parent.acceptCovariantReturnTypeBridgeMethod(bridge, target);
  }
}
