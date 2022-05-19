// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class DexAnnotationDirectory extends DexItem {

  private final DexProgramClass clazz;
  private final List<DexEncodedMethod> methodAnnotations;
  private final List<DexEncodedMethod> parameterAnnotations;
  private final List<DexEncodedField> fieldAnnotations;
  private final boolean classHasOnlyInternalizableAnnotations;

  public DexAnnotationDirectory(DexProgramClass clazz) {
    this.clazz = clazz;
    this.classHasOnlyInternalizableAnnotations = clazz.hasOnlyInternalizableAnnotations();
    methodAnnotations = new ArrayList<>();
    parameterAnnotations = new ArrayList<>();
    fieldAnnotations = new ArrayList<>();
    clazz
        .getMethodCollection()
        .forEachMethod(
            method -> {
              if (!method.annotations().isEmpty()) {
                methodAnnotations.add(method);
              }
              if (!method.parameterAnnotationsList.isEmpty()) {
                parameterAnnotations.add(method);
              }
            });
    for (DexEncodedField field : clazz.fields()) {
      if (!field.annotations().isEmpty()) {
        fieldAnnotations.add(field);
      }
    }
  }

  public void visitAnnotations(
      Consumer<DexAnnotation> annotationConsumer,
      Consumer<DexAnnotationSet> annotationSetConsumer,
      Consumer<ParameterAnnotationsList> parameterAnnotationsListConsumer) {
    visitAnnotationSet(clazz.annotations(), annotationConsumer, annotationSetConsumer);
    clazz.forEachField(
        field ->
            visitAnnotationSet(field.annotations(), annotationConsumer, annotationSetConsumer));
    clazz.forEachMethod(
        method -> {
          visitAnnotationSet(method.annotations(), annotationConsumer, annotationSetConsumer);
          visitParameterAnnotationsList(
              method.getParameterAnnotations(),
              annotationConsumer,
              annotationSetConsumer,
              parameterAnnotationsListConsumer);
        });
  }

  private void visitAnnotationSet(
      DexAnnotationSet annotationSet,
      Consumer<DexAnnotation> annotationConsumer,
      Consumer<DexAnnotationSet> annotationSetConsumer) {
    annotationSetConsumer.accept(annotationSet);
    for (DexAnnotation annotation : annotationSet.getAnnotations()) {
      annotationConsumer.accept(annotation);
    }
  }

  private void visitParameterAnnotationsList(
      ParameterAnnotationsList parameterAnnotationsList,
      Consumer<DexAnnotation> annotationConsumer,
      Consumer<DexAnnotationSet> annotationSetConsumer,
      Consumer<ParameterAnnotationsList> parameterAnnotationsListConsumer) {
    parameterAnnotationsListConsumer.accept(parameterAnnotationsList);
    for (DexAnnotationSet annotationSet : parameterAnnotationsList.getAnnotationSets()) {
      visitAnnotationSet(annotationSet, annotationConsumer, annotationSetConsumer);
    }
  }

  public DexAnnotationSet getClazzAnnotations() {
    return clazz.annotations();
  }

  public List<DexEncodedMethod> sortMethodAnnotations(CompareToVisitor visitor) {
    methodAnnotations.sort((a, b) -> a.getReference().acceptCompareTo(b.getReference(), visitor));
    return methodAnnotations;
  }

  public List<DexEncodedMethod> sortParameterAnnotations(CompareToVisitor visitor) {
    parameterAnnotations.sort(
        (a, b) -> a.getReference().acceptCompareTo(b.getReference(), visitor));
    return parameterAnnotations;
  }

  public List<DexEncodedField> sortFieldAnnotations(CompareToVisitor visitor) {
    fieldAnnotations.sort((a, b) -> a.getReference().acceptCompareTo(b.getReference(), visitor));
    return fieldAnnotations;
  }

  /**
   * DexAnnotationDirectory of a class can be canonicalized only if a class has annotations and
   * does not contains annotations for its fields, methods or parameters. Indeed, if a field, method
   * or parameter has annotations in this case, the DexAnnotationDirectory can not be shared since
   * it will contains information about field, method and parameters that are only related to only
   * one class.
   */
  @Override
  public final boolean equals(Object obj) {
    if (!(obj instanceof DexAnnotationDirectory)) {
      return false;
    }
    if (classHasOnlyInternalizableAnnotations) {
      DexAnnotationDirectory other = (DexAnnotationDirectory) obj;
      if (!other.clazz.hasOnlyInternalizableAnnotations()) {
        return false;
      }
      return clazz.annotations().equals(other.clazz.annotations());
    }
    return super.equals(obj);
  }

  @Override
  public final int hashCode() {
    if (classHasOnlyInternalizableAnnotations) {
      return clazz.annotations().hashCode();
    }
    return super.hashCode();
  }

  @Override
  public void collectMixedSectionItems(MixedSectionCollection collection) {
    throw new Unreachable();
  }
}
