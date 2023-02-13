// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.rewriting;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.outliner.OutlineOptimizationEventConsumer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Collection;

public class ArtProfileRewritingOutlineOptimizationEventConsumer
    implements OutlineOptimizationEventConsumer {

  private final ConcreteArtProfileCollectionAdditions additionsCollection;
  private final OutlineOptimizationEventConsumer parent;

  private ArtProfileRewritingOutlineOptimizationEventConsumer(
      ConcreteArtProfileCollectionAdditions additionsCollection,
      OutlineOptimizationEventConsumer parent) {
    this.additionsCollection = additionsCollection;
    this.parent = parent;
  }

  public static OutlineOptimizationEventConsumer attach(
      AppView<AppInfoWithLiveness> appView, OutlineOptimizationEventConsumer eventConsumer) {
    ArtProfileCollectionAdditions additionsCollection =
        ArtProfileCollectionAdditions.create(appView);
    if (additionsCollection.isNop()) {
      return eventConsumer;
    }
    return new ArtProfileRewritingOutlineOptimizationEventConsumer(
        additionsCollection.asConcrete(), eventConsumer);
  }

  @Override
  public void acceptOutlineMethod(ProgramMethod method, Collection<ProgramMethod> contexts) {
    for (ProgramMethod context : contexts) {
      additionsCollection.applyIfContextIsInProfile(
          context,
          additionsBuilder -> additionsBuilder.addRule(method).addRule(method.getHolder()));
    }
    parent.acceptOutlineMethod(method, contexts);
  }

  @Override
  public void finished(AppView<AppInfoWithLiveness> appView) {
    additionsCollection.commit(appView);
    parent.finished(appView);
  }
}
