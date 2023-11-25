// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotation.AnnotatedKind;
import com.android.tools.r8.graph.ProgramDefinition;
import java.util.List;
import java.util.Objects;

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

    private final List<MatchedAnnotation> matchedAnnotations;

    public ConcreteAnnotationMatchResult(List<MatchedAnnotation> matchedAnnotations) {
      this.matchedAnnotations = matchedAnnotations;
    }

    public List<MatchedAnnotation> getMatchedAnnotations() {
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

  static class MatchedAnnotation {

    private final ProgramDefinition annotatedItem;
    private final DexAnnotation annotation;
    private final AnnotatedKind annotatedKind;

    MatchedAnnotation(
        ProgramDefinition annotatedItem, DexAnnotation annotation, AnnotatedKind annotatedKind) {
      this.annotatedItem = annotatedItem;
      this.annotation = annotation;
      this.annotatedKind = annotatedKind;
    }

    public ProgramDefinition getAnnotatedItem() {
      return annotatedItem;
    }

    public DexAnnotation getAnnotation() {
      return annotation;
    }

    public AnnotatedKind getAnnotatedKind() {
      return annotatedKind;
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof MatchedAnnotation) {
        MatchedAnnotation annotationToRetain = (MatchedAnnotation) obj;
        return annotatedItem == annotationToRetain.annotatedItem
            && annotation == annotationToRetain.annotation
            && annotatedKind == annotationToRetain.annotatedKind;
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(annotatedItem, annotation, annotatedKind);
    }
  }
}
