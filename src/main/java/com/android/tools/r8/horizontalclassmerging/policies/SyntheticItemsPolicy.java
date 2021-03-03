// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;
import com.android.tools.r8.horizontalclassmerging.policies.SyntheticItemsPolicy.ClassKind;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;

public class SyntheticItemsPolicy extends MultiClassSameReferencePolicy<ClassKind> {

  enum ClassKind {
    SYNTHETIC,
    NOT_SYNTHETIC
  }

  private final AppView<AppInfoWithLiveness> appView;

  public SyntheticItemsPolicy(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  @Override
  public ClassKind getMergeKey(DexProgramClass clazz) {
    SyntheticItems syntheticItems = appView.getSyntheticItems();

    // Allow merging non-synthetics with non-synthetics.
    if (!syntheticItems.isSyntheticClass(clazz)) {
      return ClassKind.NOT_SYNTHETIC;
    }

    // Do not allow merging synthetics that are not lambdas.
    if (!syntheticItems.isNonLegacySynthetic(clazz)
        || syntheticItems.getNonLegacySyntheticKind(clazz) != SyntheticKind.LAMBDA) {
      return ineligibleForClassMerging();
    }

    // Allow merging Java lambdas with Java lambdas.
    if (appView.options().horizontalClassMergerOptions().isJavaLambdaMergingEnabled()) {
      return ClassKind.SYNTHETIC;
    }

    // Java lambda merging is disabled.
    return ineligibleForClassMerging();
  }
}
