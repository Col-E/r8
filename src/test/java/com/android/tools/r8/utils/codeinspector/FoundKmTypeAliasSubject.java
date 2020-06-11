// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import static com.android.tools.r8.utils.DescriptorUtils.DESCRIPTOR_PACKAGE_SEPARATOR;
import static com.android.tools.r8.utils.DescriptorUtils.getBinaryNameFromJavaType;

import java.util.List;
import java.util.stream.Collectors;
import kotlinx.metadata.KmAnnotation;
import kotlinx.metadata.KmTypeAlias;

public class FoundKmTypeAliasSubject extends KmTypeAliasSubject {

  private final KmTypeAlias kmTypeAlias;
  private final CodeInspector codeInspector;

  FoundKmTypeAliasSubject(CodeInspector codeInspector, KmTypeAlias kmTypeAlias) {
    this.codeInspector = codeInspector;
    this.kmTypeAlias = kmTypeAlias;
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
    return false;
  }

  @Override
  public String name() {
    return kmTypeAlias.getName();
  }

  @Override
  public List<KmTypeParameterSubject> typeParameters() {
    return kmTypeAlias.getTypeParameters().stream()
        .map(kmTypeParameter -> new FoundKmTypeParameterSubject(codeInspector, kmTypeParameter))
        .collect(Collectors.toList());
  }

  @Override
  public String descriptor(String pkg) {
    return "L" + getBinaryNameFromJavaType(pkg) + DESCRIPTOR_PACKAGE_SEPARATOR + name() + ";";
  }

  @Override
  public KmTypeSubject expandedType() {
    return new KmTypeSubject(codeInspector, kmTypeAlias.expandedType);
  }

  @Override
  public KmTypeSubject underlyingType() {
    return new KmTypeSubject(codeInspector, kmTypeAlias.underlyingType);
  }

  @Override
  public List<KmAnnotation> annotations() {
    return kmTypeAlias.getAnnotations();
  }
}
