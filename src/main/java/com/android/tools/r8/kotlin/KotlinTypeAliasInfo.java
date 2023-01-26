// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.consume;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.rewriteList;
import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.Reporter;
import java.util.List;
import java.util.function.Consumer;
import kotlinx.metadata.KmTypeAlias;

// Holds information about KmTypeAlias
public class KotlinTypeAliasInfo implements EnqueuerMetadataTraceable {

  private final int flags;
  private final String name;
  private final KotlinTypeInfo underlyingType;
  private final KotlinTypeInfo expandedType;
  private final List<KotlinTypeParameterInfo> typeParameters;
  private final List<KotlinAnnotationInfo> annotations;
  private final KotlinVersionRequirementInfo versionRequirements;

  private KotlinTypeAliasInfo(
      int flags,
      String name,
      KotlinTypeInfo underlyingType,
      KotlinTypeInfo expandedType,
      List<KotlinTypeParameterInfo> typeParameters,
      List<KotlinAnnotationInfo> annotations,
      KotlinVersionRequirementInfo versionRequirements) {
    this.flags = flags;
    this.name = name;
    assert underlyingType != null;
    assert expandedType != null;
    this.underlyingType = underlyingType;
    this.expandedType = expandedType;
    this.typeParameters = typeParameters;
    this.annotations = annotations;
    this.versionRequirements = versionRequirements;
  }

  public static KotlinTypeAliasInfo create(
      KmTypeAlias alias, DexItemFactory factory, Reporter reporter) {
    return new KotlinTypeAliasInfo(
        alias.getFlags(),
        alias.getName(),
        KotlinTypeInfo.create(alias.underlyingType, factory, reporter),
        KotlinTypeInfo.create(alias.expandedType, factory, reporter),
        KotlinTypeParameterInfo.create(alias.getTypeParameters(), factory, reporter),
        KotlinAnnotationInfo.create(alias.getAnnotations(), factory),
        KotlinVersionRequirementInfo.create(alias.getVersionRequirements()));
  }

  boolean rewrite(Consumer<KmTypeAlias> consumer, AppView<?> appView) {
    KmTypeAlias kmTypeAlias = consume(new KmTypeAlias(flags, name), consumer);
    boolean rewritten = underlyingType.rewrite(kmTypeAlias::setUnderlyingType, appView);
    rewritten |= expandedType.rewrite(kmTypeAlias::setExpandedType, appView);
    rewritten |=
        rewriteList(
            appView,
            typeParameters,
            kmTypeAlias.getTypeParameters(),
            KotlinTypeParameterInfo::rewrite);
    rewritten |=
        rewriteList(
            appView, annotations, kmTypeAlias.getAnnotations(), KotlinAnnotationInfo::rewrite);
    rewritten |= versionRequirements.rewrite(kmTypeAlias.getVersionRequirements()::addAll);
    return rewritten;
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    underlyingType.trace(definitionSupplier);
    expandedType.trace(definitionSupplier);
    forEachApply(typeParameters, typeParam -> typeParam::trace, definitionSupplier);
    forEachApply(annotations, annotation -> annotation::trace, definitionSupplier);
  }
}
