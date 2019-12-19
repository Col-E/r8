// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import static com.android.tools.r8.utils.DescriptorUtils.getDescriptorFromKotlinClassifier;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.kotlin.Kotlin;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.Box;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import kotlinx.metadata.KmClass;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmTypeVisitor;

public class FoundKmClassSubject extends KmClassSubject {
  private final CodeInspector codeInspector;
  private final DexClass clazz;
  private final KmClass kmClass;

  FoundKmClassSubject(CodeInspector codeInspector, DexClass clazz, KmClass kmClass) {
    this.codeInspector = codeInspector;
    this.clazz = clazz;
    this.kmClass = kmClass;
  }

  @Override
  public DexClass getDexClass() {
    return clazz;
  }

  @Override
  public KmClass getKmClass(Kotlin kotlin) {
    return kmClass;
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    return !clazz.type.getInternalName().equals(kmClass.name);
  }

  @Override
  public boolean isSynthetic() {
    // TODO(b/70169921): This should return `true` conditionally if we start synthesizing @Metadata
    //   from scratch.
    return false;
  }

  // TODO(b/145824437): This is a dup of DescriptorUtils#getDescriptorFromKmType
  private String getDescriptorFromKmType(KmType kmType) {
    if (kmType == null) {
      return null;
    }
    Box<String> descriptor = new Box<>(null);
    kmType.accept(new KmTypeVisitor() {
      @Override
      public void visitClass(String name) {
        descriptor.set(getDescriptorFromKotlinClassifier(name));
      }

      @Override
      public void visitTypeAlias(String name) {
        descriptor.set(getDescriptorFromKotlinClassifier(name));
      }
    });
    return descriptor.get();
  }

  @Override
  public List<String> getSuperTypeDescriptors() {
    return kmClass.getSupertypes().stream()
        .map(this::getDescriptorFromKmType)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Override
  public List<String> getParameterTypeDescriptorsInFunctions() {
    return kmClass.getFunctions().stream()
        .flatMap(kmFunction ->
            kmFunction.getValueParameters().stream()
                .map(kmValueParameter -> getDescriptorFromKmType(kmValueParameter.getType()))
                .filter(Objects::nonNull))
        .collect(Collectors.toList());
  }

  @Override
  public List<String> getReturnTypeDescriptorsInFunctions() {
    return kmClass.getFunctions().stream()
        .map(kmFunction -> getDescriptorFromKmType(kmFunction.getReturnType()))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Override
  public List<String> getReturnTypeDescriptorsInProperties() {
    return kmClass.getProperties().stream()
        .map(kmProperty -> getDescriptorFromKmType(kmProperty.getReturnType()))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private ClassSubject getClassSubjectFromKmType(KmType kmType) {
    String descriptor = getDescriptorFromKmType(kmType);
    if (descriptor == null) {
      return new AbsentClassSubject();
    }
    return codeInspector.clazz(Reference.classFromDescriptor(descriptor));
  }

  @Override
  public List<ClassSubject> getSuperTypes() {
    return kmClass.getSupertypes().stream()
        .map(this::getClassSubjectFromKmType)
        .filter(ClassSubject::isPresent)
        .collect(Collectors.toList());
  }

  @Override
  public List<ClassSubject> getParameterTypesInFunctions() {
    return kmClass.getFunctions().stream()
        .flatMap(kmFunction ->
            kmFunction.getValueParameters().stream()
                .map(kmValueParameter -> getClassSubjectFromKmType(kmValueParameter.getType()))
                .filter(ClassSubject::isPresent))
        .collect(Collectors.toList());
  }

  @Override
  public List<ClassSubject> getReturnTypesInFunctions() {
    return kmClass.getFunctions().stream()
        .map(kmFunction -> getClassSubjectFromKmType(kmFunction.getReturnType()))
        .filter(ClassSubject::isPresent)
        .collect(Collectors.toList());
  }

  @Override
  public List<ClassSubject> getReturnTypesInProperties() {
    return kmClass.getProperties().stream()
        .map(kmProperty -> getClassSubjectFromKmType(kmProperty.getReturnType()))
        .filter(ClassSubject::isPresent)
        .collect(Collectors.toList());
  }
}
