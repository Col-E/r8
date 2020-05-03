// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
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
  // TODO(b/154351125): Extend with flexible upper bounds

  private KotlinTypeInfo(
      int flags,
      KotlinClassifierInfo classifier,
      KotlinTypeInfo abbreviatedType,
      KotlinTypeInfo outerType,
      List<KotlinTypeProjectionInfo> arguments,
      List<KotlinAnnotationInfo> annotations) {
    this.flags = flags;
    this.classifier = classifier;
    this.abbreviatedType = abbreviatedType;
    this.outerType = outerType;
    this.arguments = arguments;
    this.annotations = annotations;
  }

  public List<KotlinTypeProjectionInfo> getArguments() {
    return arguments;
  }

  static KotlinTypeInfo create(KmType kmType, AppView<?> appView) {
    if (kmType == null) {
      return null;
    }
    return new KotlinTypeInfo(
        kmType.getFlags(),
        KotlinClassifierInfo.create(kmType.classifier, appView),
        KotlinTypeInfo.create(kmType.getAbbreviatedType(), appView),
        KotlinTypeInfo.create(kmType.getOuterType(), appView),
        getArguments(kmType.getArguments(), appView),
        KotlinAnnotationInfo.create(JvmExtensionsKt.getAnnotations(kmType), appView));
  }

  private static List<KotlinTypeProjectionInfo> getArguments(
      List<KmTypeProjection> projections, AppView<?> appView) {
    if (projections.isEmpty()) {
      return EMPTY_ARGUMENTS;
    }
    ImmutableList.Builder<KotlinTypeProjectionInfo> arguments = ImmutableList.builder();
    for (KmTypeProjection projection : projections) {
      arguments.add(KotlinTypeProjectionInfo.create(projection, appView));
    }
    return arguments.build();
  }

  public void rewrite(
      KmVisitorProviders.KmTypeVisitorProvider visitorProvider,
      AppView<AppInfoWithLiveness> appView,
      NamingLens namingLens) {
    // The type may have been pruned, so check first that the classifier is still live.
    if (!classifier.isLive(appView)) {
      return;
    }
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
