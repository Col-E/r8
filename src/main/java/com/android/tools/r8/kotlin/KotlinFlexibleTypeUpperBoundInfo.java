// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.Reporter;
import kotlinx.metadata.KmFlexibleTypeUpperBound;

public class KotlinFlexibleTypeUpperBoundInfo implements EnqueuerMetadataTraceable {

  private static final String KOTLIN_JVM_PLATFORMTYPE = "kotlin.jvm.PlatformType";
  private static final KotlinFlexibleTypeUpperBoundInfo NO_FLEXIBLE_UPPER_BOUND =
      new KotlinFlexibleTypeUpperBoundInfo(KOTLIN_JVM_PLATFORMTYPE, null);

  private final String typeFlexibilityId;
  private final KotlinTypeInfo kotlinTypeInfo;

  private KotlinFlexibleTypeUpperBoundInfo(
      String typeFlexibilityId, KotlinTypeInfo kotlinTypeInfo) {
    this.typeFlexibilityId = typeFlexibilityId;
    this.kotlinTypeInfo = kotlinTypeInfo;
    assert KOTLIN_JVM_PLATFORMTYPE.equals(typeFlexibilityId);
  }

  static KotlinFlexibleTypeUpperBoundInfo create(
      KmFlexibleTypeUpperBound flexibleTypeUpperBound, DexItemFactory factory, Reporter reporter) {
    if (flexibleTypeUpperBound == null) {
      return NO_FLEXIBLE_UPPER_BOUND;
    }
    return new KotlinFlexibleTypeUpperBoundInfo(
        flexibleTypeUpperBound.getTypeFlexibilityId(),
        KotlinTypeInfo.create(flexibleTypeUpperBound.getType(), factory, reporter));
  }

  boolean rewrite(
      KmVisitorProviders.KmFlexibleUpperBoundVisitorProvider visitorProvider, AppView<?> appView) {
    if (this == NO_FLEXIBLE_UPPER_BOUND) {
      // Nothing to do.
      return false;
    }
    if (kotlinTypeInfo == null) {
      assert false;
      return false;
    }
    return kotlinTypeInfo.rewrite(flags -> visitorProvider.get(flags, typeFlexibilityId), appView);
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    if (this == NO_FLEXIBLE_UPPER_BOUND) {
      return;
    }
    if (kotlinTypeInfo == null) {
      assert false;
      return;
    }
    kotlinTypeInfo.trace(definitionSupplier);
  }
}
