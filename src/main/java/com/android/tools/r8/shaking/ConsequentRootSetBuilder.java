// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.SubtypingInfo;

class ConsequentRootSetBuilder extends RootSetBuilder {

  private final Enqueuer enqueuer;

  ConsequentRootSetBuilder(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      SubtypingInfo subtypingInfo,
      Enqueuer enqueuer) {
    super(appView, subtypingInfo, null);
    this.enqueuer = enqueuer;
  }

  @Override
  void handleMatchedAnnotation(AnnotationMatchResult annotationMatchResult) {
    if (enqueuer.getMode().isInitialTreeShaking()
        && annotationMatchResult.isConcreteAnnotationMatchResult()) {
      enqueuer.retainAnnotationForFinalTreeShaking(
          annotationMatchResult.asConcreteAnnotationMatchResult().getMatchedAnnotations());
    }
  }
}
