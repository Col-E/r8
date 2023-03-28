// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.consume;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.rewriteIfNotNull;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.rewriteList;
import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Consumer;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmTypeProjection;
import kotlinx.metadata.jvm.JvmExtensionsKt;

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
  private final boolean isRaw;

  KotlinTypeInfo(
      int flags,
      KotlinClassifierInfo classifier,
      KotlinTypeInfo abbreviatedType,
      KotlinTypeInfo outerType,
      List<KotlinTypeProjectionInfo> arguments,
      List<KotlinAnnotationInfo> annotations,
      KotlinFlexibleTypeUpperBoundInfo flexibleTypeUpperBound,
      boolean isRaw) {
    this.flags = flags;
    this.classifier = classifier;
    this.abbreviatedType = abbreviatedType;
    this.outerType = outerType;
    this.arguments = arguments;
    this.annotations = annotations;
    this.flexibleTypeUpperBound = flexibleTypeUpperBound;
    this.isRaw = isRaw;
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
            kmType.getFlexibleTypeUpperBound(), factory, reporter),
        JvmExtensionsKt.isRaw(kmType));
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

  boolean rewrite(Consumer<KmType> consumer, AppView<?> appView) {
    // TODO(b/154348683): Check for correct flags
    KmType kmType = consume(new KmType(flags), consumer);
    boolean rewritten = classifier.rewrite(kmType, appView);
    rewritten |=
        rewriteIfNotNull(
            appView, abbreviatedType, kmType::setAbbreviatedType, KotlinTypeInfo::rewrite);
    rewritten |=
        rewriteIfNotNull(appView, outerType, kmType::setOuterType, KotlinTypeInfo::rewrite);
    rewritten |=
        rewriteList(appView, arguments, kmType.getArguments(), KotlinTypeProjectionInfo::rewrite);
    rewritten |= flexibleTypeUpperBound.rewrite(kmType::setFlexibleTypeUpperBound, appView);
    if (annotations.isEmpty() && !isRaw) {
      return rewritten;
    }
    rewritten |=
        rewriteList(
            appView,
            annotations,
            JvmExtensionsKt.getAnnotations(kmType),
            KotlinAnnotationInfo::rewrite);
    JvmExtensionsKt.setRaw(kmType, isRaw);
    return rewritten;
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

  public DexType rewriteType(GraphLens graphLens) {
    return classifier.rewriteType(graphLens);
  }
}
