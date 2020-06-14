// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.util.List;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmTypeProjection;
import kotlinx.metadata.KmTypeVisitor;
import kotlinx.metadata.jvm.JvmExtensionsKt;
import kotlinx.metadata.jvm.JvmTypeExtensionVisitor;

// Provides access to Kotlin information about a kotlin type.
public class KotlinTypeInfo implements EnqueuerMetadataTraceable {

  private static final List<KotlinTypeProjectionInfo> EMPTY_ARGUMENTS = ImmutableList.of();

  private final int flags;
  private final KotlinClassifierInfo classifier;
  private final KotlinTypeInfo abbreviatedType;
  private final KotlinTypeInfo outerType;
  private final List<KotlinTypeProjectionInfo> arguments;
  private final List<KotlinAnnotationInfo> annotations;
  private final KotlinFlexibleTypeUpperBoundInfo flexibleTypeUpperBound;

  KotlinTypeInfo(
      int flags,
      KotlinClassifierInfo classifier,
      KotlinTypeInfo abbreviatedType,
      KotlinTypeInfo outerType,
      List<KotlinTypeProjectionInfo> arguments,
      List<KotlinAnnotationInfo> annotations,
      KotlinFlexibleTypeUpperBoundInfo flexibleTypeUpperBound) {
    this.flags = flags;
    this.classifier = classifier;
    this.abbreviatedType = abbreviatedType;
    this.outerType = outerType;
    this.arguments = arguments;
    this.annotations = annotations;
    this.flexibleTypeUpperBound = flexibleTypeUpperBound;
  }

  static KotlinTypeInfo create(KmType kmType, DexItemFactory factory, Reporter reporter) {
    if (kmType == null) {
      return null;
    }
    return new KotlinTypeInfo(
        kmType.getFlags(),
        KotlinClassifierInfo.create(kmType.classifier, factory, reporter),
        KotlinTypeInfo.create(kmType.getAbbreviatedType(), factory, reporter),
        KotlinTypeInfo.create(kmType.getOuterType(), factory, reporter),
        getArguments(kmType.getArguments(), factory, reporter),
        KotlinAnnotationInfo.create(JvmExtensionsKt.getAnnotations(kmType), factory),
        KotlinFlexibleTypeUpperBoundInfo.create(
            kmType.getFlexibleTypeUpperBound(), factory, reporter));
  }

  static List<KotlinTypeProjectionInfo> getArguments(
      List<KmTypeProjection> projections, DexItemFactory factory, Reporter reporter) {
    if (projections.isEmpty()) {
      return EMPTY_ARGUMENTS;
    }
    ImmutableList.Builder<KotlinTypeProjectionInfo> arguments = ImmutableList.builder();
    for (KmTypeProjection projection : projections) {
      arguments.add(KotlinTypeProjectionInfo.create(projection, factory, reporter));
    }
    return arguments.build();
  }

  public void rewrite(
      KmVisitorProviders.KmTypeVisitorProvider visitorProvider,
      AppView<?> appView,
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
    flexibleTypeUpperBound.rewrite(kmTypeVisitor::visitFlexibleTypeUpperBound, appView, namingLens);
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

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    classifier.trace(definitionSupplier);
    if (abbreviatedType != null) {
      abbreviatedType.trace(definitionSupplier);
    }
    if (outerType != null) {
      outerType.trace(definitionSupplier);
    }
    forEachApply(arguments, argument -> argument::trace, definitionSupplier);
    flexibleTypeUpperBound.trace(definitionSupplier);
    forEachApply(annotations, annotation -> annotation::trace, definitionSupplier);
  }
}
