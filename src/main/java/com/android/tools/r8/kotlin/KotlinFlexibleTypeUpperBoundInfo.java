// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.Reporter;
import java.util.List;
import kotlinx.metadata.KmFlexibleTypeUpperBound;
import kotlinx.metadata.KmType;
import kotlinx.metadata.jvm.JvmExtensionsKt;

public class KotlinFlexibleTypeUpperBoundInfo extends KotlinTypeInfo {

  private static final String KOTLIN_JVM_PLATFORMTYPE = "kotlin.jvm.PlatformType";
  private static final KotlinFlexibleTypeUpperBoundInfo NO_FLEXIBLE_UPPER_BOUND =
      new KotlinFlexibleTypeUpperBoundInfo(
          0, null, null, null, null, null, null, KOTLIN_JVM_PLATFORMTYPE);

  private final String typeFlexibilityId;

  private KotlinFlexibleTypeUpperBoundInfo(
      int flags,
      KotlinClassifierInfo classifier,
      KotlinTypeInfo abbreviatedType,
      KotlinTypeInfo outerType,
      List<KotlinTypeProjectionInfo> arguments,
      List<KotlinAnnotationInfo> annotations,
      KotlinFlexibleTypeUpperBoundInfo flexibleTypeUpperBoundInfo,
      String typeFlexibilityId) {
    super(
        flags,
        classifier,
        abbreviatedType,
        outerType,
        arguments,
        annotations,
        flexibleTypeUpperBoundInfo);
    this.typeFlexibilityId = typeFlexibilityId;
    assert KOTLIN_JVM_PLATFORMTYPE.equals(typeFlexibilityId);
  }

  static KotlinFlexibleTypeUpperBoundInfo create(
      KmFlexibleTypeUpperBound flexibleTypeUpperBound, DexItemFactory factory, Reporter reporter) {
    if (flexibleTypeUpperBound == null) {
      return NO_FLEXIBLE_UPPER_BOUND;
    }
    KmType kmType = flexibleTypeUpperBound.getType();
    return new KotlinFlexibleTypeUpperBoundInfo(
        kmType.getFlags(),
        KotlinClassifierInfo.create(kmType.classifier, factory, reporter),
        KotlinTypeInfo.create(kmType.getAbbreviatedType(), factory, reporter),
        KotlinTypeInfo.create(kmType.getOuterType(), factory, reporter),
        getArguments(kmType.getArguments(), factory, reporter),
        KotlinAnnotationInfo.create(JvmExtensionsKt.getAnnotations(kmType), factory),
        KotlinFlexibleTypeUpperBoundInfo.create(
            kmType.getFlexibleTypeUpperBound(), factory, reporter),
        flexibleTypeUpperBound.getTypeFlexibilityId());
  }

  public void rewrite(
      KmVisitorProviders.KmFlexibleUpperBoundVisitorProvider visitorProvider,
      AppView<?> appView,
      NamingLens namingLens) {
    if (this == NO_FLEXIBLE_UPPER_BOUND) {
      // Nothing to do.
      return;
    }
    super.rewrite(flags -> visitorProvider.get(flags, typeFlexibilityId), appView, namingLens);
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    if (this == NO_FLEXIBLE_UPPER_BOUND) {
      return;
    }
    super.trace(definitionSupplier);
  }
}
