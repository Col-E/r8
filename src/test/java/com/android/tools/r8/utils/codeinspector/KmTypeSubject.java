// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import static com.android.tools.r8.utils.DescriptorUtils.getDescriptorFromKotlinClassifier;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.Box;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import kotlinx.metadata.KmAnnotation;
import kotlinx.metadata.KmFlexibleTypeUpperBound;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmTypeVisitor;
import kotlinx.metadata.jvm.JvmExtensionsKt;

public class KmTypeSubject extends Subject {
  private final CodeInspector codeInspector;
  private final KmType kmType;

  KmTypeSubject(CodeInspector codeInspector, KmType kmType) {
    assert kmType != null;
    this.codeInspector = codeInspector;
    this.kmType = kmType;
  }

  // TODO(b/151195430): This is a dup of DescriptorUtils#getDescriptorFromKmType
  static String getDescriptorFromKmType(KmType kmType) {
    if (kmType == null) {
      return null;
    }
    Box<String> descriptor = new Box<>(null);
    kmType.accept(new KmTypeVisitor() {
      @Override
      public void visitClass(String name) {
        // We don't check Kotlin types in tests, but be aware of the relocation issue.
        // See b/70169921#comment25 for more details.
        assert descriptor.get() == null;
        descriptor.set(getDescriptorFromKotlinClassifier(name));
      }

      @Override
      public void visitTypeAlias(String name) {
        assert descriptor.get() == null;
        descriptor.set(getDescriptorFromKotlinClassifier(name));
      }
    });
    return descriptor.get();
  }

  public String descriptor() {
    return getDescriptorFromKmType(kmType);
  }

  public List<KmTypeProjectionSubject> typeArguments() {
    return kmType.getArguments().stream()
        .map(kmTypeProjection -> new KmTypeProjectionSubject(codeInspector, kmTypeProjection))
        .collect(Collectors.toList());
  }

  public KmClassifierSubject classifier() {
    return new KmClassifierSubject(kmType.classifier);
  }

  public KmFlexibleTypeUpperBound getFlexibleUpperBound() {
    return kmType.getFlexibleTypeUpperBound();
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    ClassSubject classSubject = codeInspector.clazz(Reference.classFromDescriptor(descriptor()));
    return classSubject.isRenamed();
  }

  @Override
  public boolean isSynthetic() {
    throw new Unreachable("Cannot determine if a type is synthetic");
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof KmTypeSubject)) {
      return false;
    }
    return areEqual(this.kmType, ((KmTypeSubject) obj).kmType, true);
  }

  public boolean equalUpToAbbreviatedType(KmTypeSubject other) {
    if (other == null) {
      return false;
    }
    return areEqual(this.kmType, other.kmType, false);
  }

  public static boolean areEqual(KmType one, KmType other, boolean checkInnerTypeReferences) {
    if (one == null && other == null) {
      return true;
    }
    if (one == null || other == null) {
      return false;
    }
    if (one.getFlags() != other.getFlags()) {
      return false;
    }
    if (!one.classifier.toString().equals(other.classifier.toString())) {
      return false;
    }
    if (one.getArguments().size() != other.getArguments().size()) {
      return false;
    }
    for (int i = 0; i < one.getArguments().size(); i++) {
      if (!KmTypeProjectionSubject.areEqual(
          one.getArguments().get(i), other.getArguments().get(i))) {
        return false;
      }
    }
    if (checkInnerTypeReferences
        && !areEqual(
            one.getAbbreviatedType(), other.getAbbreviatedType(), checkInnerTypeReferences)) {
      return false;
    }
    if (!areEqual(one.getOuterType(), other.getOuterType(), checkInnerTypeReferences)) {
      return false;
    }
    if ((one.getFlexibleTypeUpperBound() == null) != (other.getFlexibleTypeUpperBound() == null)
        && checkInnerTypeReferences) {
      return false;
    }
    if (one.getFlexibleTypeUpperBound() != null && checkInnerTypeReferences) {
      if (!Objects.equals(
          one.getFlexibleTypeUpperBound().getTypeFlexibilityId(),
          other.getFlexibleTypeUpperBound().getTypeFlexibilityId())) {
        return false;
      }
      if (!areEqual(
          one.getFlexibleTypeUpperBound().getType(),
          other.getFlexibleTypeUpperBound().getType(),
          checkInnerTypeReferences)) {
        return false;
      }
    }
    if (JvmExtensionsKt.isRaw(one) != JvmExtensionsKt.isRaw(other)) {
      return false;
    }
    List<KmAnnotation> annotationsOne = JvmExtensionsKt.getAnnotations(one);
    List<KmAnnotation> annotationsOther = JvmExtensionsKt.getAnnotations(other);
    if (annotationsOne.size() != annotationsOther.size()) {
      return false;
    }
    for (int i = 0; i < annotationsOne.size(); i++) {
      KmAnnotation kmAnnotationOne = annotationsOne.get(i);
      KmAnnotation kmAnnotationOther = annotationsOther.get(i);
      if (!kmAnnotationOne.getClassName().equals(kmAnnotationOther.getClassName())) {
        return false;
      }
      if (!kmAnnotationOne
          .getArguments()
          .keySet()
          .equals(kmAnnotationOther.getArguments().keySet())) {
        return false;
      }
      assert false : "Not defined how to compare kmAnnotationArguments";
    }
    return true;
  }
}
