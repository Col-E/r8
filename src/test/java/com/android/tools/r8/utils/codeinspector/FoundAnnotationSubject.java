// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexTypeAnnotation;
import com.android.tools.r8.utils.ListUtils;
import java.util.List;

public class FoundAnnotationSubject extends AnnotationSubject {

  private final DexAnnotation annotation;
  private final CodeInspector codeInspector;

  FoundAnnotationSubject(DexAnnotation annotation, CodeInspector codeInspector) {
    this.annotation = annotation;
    this.codeInspector = codeInspector;
  }

  public static List<FoundAnnotationSubject> listFromDex(
      DexAnnotationSet annotations, CodeInspector codeInspector) {
    return ListUtils.map(
        annotations.annotations,
        annotation -> new FoundAnnotationSubject(annotation, codeInspector));
  }

  public TypeSubject getType() {
    return new TypeSubject(codeInspector, annotation.getAnnotationType());
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    return false;
  }

  @Override
  public boolean isSynthetic() {
    throw new Unreachable("Cannot determine if an annotation is synthetic");
  }

  @Override
  public DexEncodedAnnotation getAnnotation() {
    return annotation.annotation;
  }

  @Override
  public int isVisible() {
    return annotation.getVisibility();
  }

  @Override
  public boolean isTypeAnnotation() {
    return annotation.isTypeAnnotation();
  }

  @Override
  public DexTypeAnnotation asDexTypeAnnotation() {
    return annotation.asTypeAnnotation();
  }
}
