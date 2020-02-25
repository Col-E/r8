// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;

class ConsequentRootSetBuilder extends RootSetBuilder {

  private final Enqueuer enqueuer;

  ConsequentRootSetBuilder(AppView<? extends AppInfoWithSubtyping> appView, Enqueuer enqueuer) {
    super(appView, appView.appInfo().app(), null);
    this.enqueuer = enqueuer;
  }

  @Override
  void handleMatchedAnnotation(AnnotationMatchResult annotationMatchResult) {
    if (enqueuer.getMode().isInitialTreeShaking()
        && annotationMatchResult.isConcreteAnnotationMatchResult()) {
      enqueuer.retainAnnotationForFinalTreeShaking(
          annotationMatchResult.asConcreteAnnotationMatchResult().getMatchedAnnotation());
    }
  }
}
