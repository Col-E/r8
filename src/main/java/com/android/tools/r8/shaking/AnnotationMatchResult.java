// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexAnnotation;
import java.util.List;

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

    private final List<DexAnnotation> matchedAnnotations;

    public ConcreteAnnotationMatchResult(List<DexAnnotation> matchedAnnotations) {
      this.matchedAnnotations = matchedAnnotations;
    }

    public List<DexAnnotation> getMatchedAnnotations() {
      return matchedAnnotations;
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
