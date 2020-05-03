// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.List;
import kotlinx.metadata.KmTypeAlias;
import kotlinx.metadata.KmTypeAliasVisitor;

// Holds information about KmTypeAlias
public class KotlinTypeAliasInfo {

  private final int flags;
  private final String name;
  private final KotlinTypeInfo underlyingType;
  private final KotlinTypeInfo expandedType;
  private final List<KotlinTypeParameterInfo> typeParameters;
  private final List<KotlinAnnotationInfo> annotations;

  private KotlinTypeAliasInfo(
      int flags,
      String name,
      KotlinTypeInfo underlyingType,
      KotlinTypeInfo expandedType,
      List<KotlinTypeParameterInfo> typeParameters,
      List<KotlinAnnotationInfo> annotations) {
    this.flags = flags;
    this.name = name;
    assert underlyingType != null;
    assert expandedType != null;
    this.underlyingType = underlyingType;
    this.expandedType = expandedType;
    this.typeParameters = typeParameters;
    this.annotations = annotations;
  }

  public static KotlinTypeAliasInfo create(KmTypeAlias alias, AppView<?> appView) {
    return new KotlinTypeAliasInfo(
        alias.getFlags(),
        alias.getName(),
        KotlinTypeInfo.create(alias.underlyingType, appView),
        KotlinTypeInfo.create(alias.expandedType, appView),
        KotlinTypeParameterInfo.create(alias.getTypeParameters(), appView),
        KotlinAnnotationInfo.create(alias.getAnnotations(), appView));
  }

  void rewrite(
      KmVisitorProviders.KmTypeAliasVisitorProvider visitorProvider,
      AppView<AppInfoWithLiveness> appView,
      NamingLens namingLens) {
    KmTypeAliasVisitor kmTypeAliasVisitor = visitorProvider.get(flags, name);
    underlyingType.rewrite(kmTypeAliasVisitor::visitUnderlyingType, appView, namingLens);
    expandedType.rewrite(kmTypeAliasVisitor::visitExpandedType, appView, namingLens);
    for (KotlinTypeParameterInfo typeParameter : typeParameters) {
      typeParameter.rewrite(kmTypeAliasVisitor::visitTypeParameter, appView, namingLens);
    }
    for (KotlinAnnotationInfo annotation : annotations) {
      annotation.rewrite(kmTypeAliasVisitor::visitAnnotation, appView, namingLens);
    }
  }
}
