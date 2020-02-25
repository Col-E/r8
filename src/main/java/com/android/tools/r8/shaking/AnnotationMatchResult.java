// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexAnnotation;

public abstract class AnnotationMatchResult {

  public boolean isConcreteAnnotationMatchResult() {
    return false;
  }

  public ConcreteAnnotationMatchResult asConcreteAnnotationMatchResult() {
    return null;
  }

  static class AnnotationsIgnoredMatchResult extends AnnotationMatchResult {

    private static final AnnotationsIgnoredMatchResult INSTANCE =
        new AnnotationsIgnoredMatchResult();

    private AnnotationsIgnoredMatchResult() {}

    public static AnnotationsIgnoredMatchResult getInstance() {
      return INSTANCE;
    }
  }

  static class ConcreteAnnotationMatchResult extends AnnotationMatchResult {

    private final DexAnnotation matchedAnnotation;

    public ConcreteAnnotationMatchResult(DexAnnotation matchedAnnotation) {
      this.matchedAnnotation = matchedAnnotation;
    }

    public DexAnnotation getMatchedAnnotation() {
      return matchedAnnotation;
    }

    @Override
    public boolean isConcreteAnnotationMatchResult() {
      return true;
    }

    @Override
    public ConcreteAnnotationMatchResult asConcreteAnnotationMatchResult() {
      return this;
    }
  }
}
