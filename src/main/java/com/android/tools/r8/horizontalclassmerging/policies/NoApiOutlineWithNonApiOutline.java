// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;
import com.android.tools.r8.horizontalclassmerging.policies.NoApiOutlineWithNonApiOutline.SyntheticKindForMerging;

public class NoApiOutlineWithNonApiOutline
    extends MultiClassSameReferencePolicy<SyntheticKindForMerging> {

  public enum SyntheticKindForMerging {
    API_MODEL,
    NOT_API_MODEL
  }

  private final AppView<?> appView;

  public NoApiOutlineWithNonApiOutline(AppView<?> appView) {
    this.appView = appView;
  }

  @Override
  public String getName() {
    return "NoApiOutlineWithNonApiOutline";
  }

  @Override
  public SyntheticKindForMerging getMergeKey(DexProgramClass clazz) {
    if (appView
        .getSyntheticItems()
        .isSyntheticOfKind(clazz.getType(), kinds -> kinds.API_MODEL_OUTLINE)) {
      return SyntheticKindForMerging.API_MODEL;
    } else {
      return SyntheticKindForMerging.NOT_API_MODEL;
    }
  }
}
