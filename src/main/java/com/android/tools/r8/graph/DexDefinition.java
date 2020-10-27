// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.shaking.AnnotationRemover;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A common interface for {@link DexClass}, {@link DexEncodedField}, and {@link DexEncodedMethod}.
 */
public abstract class DexDefinition extends DexItem {

  private DexAnnotationSet annotations;

  public DexDefinition(DexAnnotationSet annotations) {
    assert annotations != null : "Should use DexAnnotationSet.THE_EMPTY_ANNOTATIONS_SET";
    this.annotations = annotations;
  }

  public DexAnnotationSet annotations() {
    return annotations;
  }

  public abstract AccessFlags<?> getAccessFlags();

  public DexAnnotationSet liveAnnotations(AppView<AppInfoWithLiveness> appView) {
    return annotations.keepIf(
        annotation -> AnnotationRemover.shouldKeepAnnotation(appView, this, annotation));
  }

  public void clearAnnotations() {
    setAnnotations(DexAnnotationSet.empty());
  }

  public void setAnnotations(DexAnnotationSet annotations) {
    this.annotations = annotations;
  }

  public boolean isDexClass() {
    return false;
  }

  public DexClass asDexClass() {
    return null;
  }

  public boolean isProgramClass() {
    return false;
  }

  public DexProgramClass asProgramClass() {
    return null;
  }

  public boolean isDexEncodedMember() {
    return false;
  }

  public DexEncodedMember<?, ?> asDexEncodedMember() {
    return null;
  }

  public boolean isDexEncodedField() {
    return false;
  }

  public DexEncodedField asDexEncodedField() {
    return null;
  }

  public boolean isDexEncodedMethod() {
    return false;
  }

  public DexEncodedMethod asDexEncodedMethod() {
    return null;
  }

  public abstract DexReference getReference();

  private static <T extends DexDefinition> Stream<T> filter(
      Stream<DexDefinition> stream,
      Predicate<DexDefinition> pred,
      Function<DexDefinition, T> f) {
    return stream.filter(pred).map(f);
  }

  public static Stream<DexEncodedField> filterDexEncodedField(Stream<DexDefinition> stream) {
    return filter(stream, DexDefinition::isDexEncodedField, DexDefinition::asDexEncodedField);
  }

  public static Stream<DexEncodedMethod> filterDexEncodedMethod(Stream<DexDefinition> stream) {
    return filter(stream, DexDefinition::isDexEncodedMethod, DexDefinition::asDexEncodedMethod);
  }

  public abstract boolean isStatic();

  public abstract boolean isStaticMember();
}
