// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.kotlin.KotlinMetadataWriter;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import kotlinx.metadata.KmClass;
import kotlinx.metadata.KmDeclarationContainer;
import kotlinx.metadata.KmTypeParameter;

public class FoundKmClassSubject extends KmClassSubject
    implements FoundKmDeclarationContainerSubject {

  private final CodeInspector codeInspector;
  private final DexClass clazz;
  private final KmClass kmClass;

  FoundKmClassSubject(CodeInspector codeInspector, DexClass clazz, KmClass kmClass) {
    this.codeInspector = codeInspector;
    this.clazz = clazz;
    this.kmClass = kmClass;
  }

  @Override
  public String getName() {
    return kmClass.getName();
  }

  @Override
  public DexClass getDexClass() {
    return clazz;
  }

  @Override
  public List<KmConstructorSubject> getConstructors() {
    return kmClass.getConstructors().stream()
        .map(constructor -> new FoundKmConstructorSubject(codeInspector, constructor))
        .collect(Collectors.toList());
  }

  @Override
  public CodeInspector codeInspector() {
    return codeInspector;
  }

  @Override
  public KmDeclarationContainer getKmDeclarationContainer() {
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
    // TODO(b/151194785): This should return `true` conditionally if we start synthesizing @Metadata
    //   from scratch.
    return false;
  }

  @Override
  public List<String> getSuperTypeDescriptors() {
    return kmClass.getSupertypes().stream()
        .map(KmTypeSubject::getDescriptorFromKmType)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Override
  public List<ClassSubject> getSuperTypes() {
    return kmClass.getSupertypes().stream()
        .map(this::getClassSubjectFromKmType)
        .filter(ClassSubject::isPresent)
        .collect(Collectors.toList());
  }

  private String nestClassDescriptor(String nestClassName) {
    return DescriptorUtils.getDescriptorFromClassBinaryName(
        kmClass.name + DescriptorUtils.INNER_CLASS_SEPARATOR + nestClassName);
  }

  @Override
  public List<String> getNestedClassDescriptors() {
    return kmClass.getNestedClasses().stream()
        .map(this::nestClassDescriptor)
        .collect(Collectors.toList());
  }

  @Override
  public List<ClassSubject> getNestedClasses() {
    return kmClass.getNestedClasses().stream()
        .map(this::nestClassDescriptor)
        .map(this::getClassSubjectFromDescriptor)
        .collect(Collectors.toList());
  }

  @Override
  public List<String> getSealedSubclassDescriptors() {
    return kmClass.getSealedSubclasses().stream()
        .map(DescriptorUtils::getDescriptorFromKotlinClassifier)
        .collect(Collectors.toList());
  }

  @Override
  public List<ClassSubject> getSealedSubclasses() {
    return kmClass.getSealedSubclasses().stream()
        .map(DescriptorUtils::getDescriptorFromKotlinClassifier)
        .map(this::getClassSubjectFromDescriptor)
        .collect(Collectors.toList());
  }

  @Override
  public String getCompanionObject() {
    return kmClass.getCompanionObject();
  }

  @Override
  public List<String> getEnumEntries() {
    return kmClass.getEnumEntries();
  }

  @Override
  public List<KmTypeParameter> getKmTypeParameters() {
    return kmClass.getTypeParameters();
  }

  @Override
  public CodeInspector getCodeInspector() {
    return codeInspector;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    KotlinMetadataWriter.appendKmClass("", sb, kmClass);
    return sb.toString();
  }
}
