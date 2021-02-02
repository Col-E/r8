// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions.HorizontalClassMergerOptions;

public class NoClassesOrMembersWithAnnotations extends SingleClassPolicy {

  private final HorizontalClassMergerOptions options;

  public NoClassesOrMembersWithAnnotations(AppView<AppInfoWithLiveness> appView) {
    this.options = appView.options().horizontalClassMergerOptions();
  }

  @Override
  public boolean canMerge(DexProgramClass program) {
    return !program.hasClassOrMemberAnnotations();
  }

  @Override
  public boolean shouldSkipPolicy() {
    // TODO(b/179019716): Add support for merging in presence of annotations.
    return options.skipNoClassesOrMembersWithAnnotationsPolicyForTesting;
  }
}
