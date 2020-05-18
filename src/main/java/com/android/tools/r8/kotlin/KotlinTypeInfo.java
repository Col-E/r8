// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.util.List;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmTypeProjection;
import kotlinx.metadata.KmTypeVisitor;
import kotlinx.metadata.jvm.JvmExtensionsKt;
import kotlinx.metadata.jvm.JvmTypeExtensionVisitor;

// Provides access to Kotlin information about a kotlin type.
public class KotlinTypeInfo {

  private static final List<KotlinTypeProjectionInfo> EMPTY_ARGUMENTS = ImmutableList.of();

  private final int flags;
  private final KotlinClassifierInfo classifier;
  private final KotlinTypeInfo abbreviatedType;
  private final KotlinTypeInfo outerType;
  private final List<KotlinTypeProjectionInfo> arguments;
  private final List<KotlinAnnotationInfo> annotations;
  private final KotlinFlexibleTypeUpperBoundInfo flexibleTypeUpperBoundInfo;

  KotlinTypeInfo(
      int flags,
      KotlinClassifierInfo classifier,
      KotlinTypeInfo abbreviatedType,
      KotlinTypeInfo outerType,
      List<KotlinTypeProjectionInfo> arguments,
      List<KotlinAnnotationInfo> annotations,
      KotlinFlexibleTypeUpperBoundInfo flexibleTypeUpperBoundInfo) {
    this.flags = flags;
    this.classifier = classifier;
    this.abbreviatedType = abbreviatedType;
    this.outerType = outerType;
    this.arguments = arguments;
    this.annotations = annotations;
    this.flexibleTypeUpperBoundInfo = flexibleTypeUpperBoundInfo;
  }

  static KotlinTypeInfo create(
      KmType kmType, DexDefinitionSupplier definitionSupplier, Reporter reporter) {
    if (kmType == null) {
      return null;
    }
    return new KotlinTypeInfo(
        kmType.getFlags(),
        KotlinClassifierInfo.create(kmType.classifier, definitionSupplier, reporter),
        KotlinTypeInfo.create(kmType.getAbbreviatedType(), definitionSupplier, reporter),
        KotlinTypeInfo.create(kmType.getOuterType(), definitionSupplier, reporter),
        getArguments(kmType.getArguments(), definitionSupplier, reporter),
        KotlinAnnotationInfo.create(JvmExtensionsKt.getAnnotations(kmType), definitionSupplier),
        KotlinFlexibleTypeUpperBoundInfo.create(
            kmType.getFlexibleTypeUpperBound(), definitionSupplier, reporter));
  }

  static List<KotlinTypeProjectionInfo> getArguments(
      List<KmTypeProjection> projections,
      DexDefinitionSupplier definitionSupplier,
      Reporter reporter) {
    if (projections.isEmpty()) {
      return EMPTY_ARGUMENTS;
    }
    ImmutableList.Builder<KotlinTypeProjectionInfo> arguments = ImmutableList.builder();
    for (KmTypeProjection projection : projections) {
      arguments.add(KotlinTypeProjectionInfo.create(projection, definitionSupplier, reporter));
    }
    return arguments.build();
  }

  public void rewrite(
      KmVisitorProviders.KmTypeVisitorProvider visitorProvider,
      AppView<AppInfoWithLiveness> appView,
      NamingLens namingLens) {
    // TODO(b/154348683): Check for correct flags
    KmTypeVisitor kmTypeVisitor = visitorProvider.get(flags);
    classifier.rewrite(kmTypeVisitor, appView, namingLens);
    if (abbreviatedType != null) {
      abbreviatedType.rewrite(kmTypeVisitor::visitAbbreviatedType, appView, namingLens);
    }
    if (outerType != null) {
      outerType.rewrite(kmTypeVisitor::visitOuterType, appView, namingLens);
    }
    for (KotlinTypeProjectionInfo argument : arguments) {
      argument.rewrite(
          kmTypeVisitor::visitArgument, kmTypeVisitor::visitStarProjection, appView, namingLens);
    }
    flexibleTypeUpperBoundInfo.rewrite(
        kmTypeVisitor::visitFlexibleTypeUpperBound, appView, namingLens);
    if (annotations.isEmpty()) {
      return;
    }
    JvmTypeExtensionVisitor extensionVisitor =
        (JvmTypeExtensionVisitor) kmTypeVisitor.visitExtensions(JvmTypeExtensionVisitor.TYPE);
    if (extensionVisitor != null) {
      for (KotlinAnnotationInfo annotation : annotations) {
        annotation.rewrite(extensionVisitor::visitAnnotation, appView, namingLens);
      }
    }
  }
}
