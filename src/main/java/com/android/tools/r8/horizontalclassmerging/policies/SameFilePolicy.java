// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;
import com.android.tools.r8.utils.InternalOptions.HorizontalClassMergerOptions;

public class SameFilePolicy extends MultiClassSameReferencePolicy<String> {

  private final HorizontalClassMergerOptions options;

  public SameFilePolicy(AppView<?> appView) {
    this.options = appView.options().horizontalClassMergerOptions();
  }

  @Override
  public String getMergeKey(DexProgramClass clazz) {
    return clazz.getType().toDescriptorString().replaceAll("^([^$]+)\\$.*", "$1");
  }

  @Override
  public String getName() {
    return "SameFilePolicy";
  }

  @Override
  public boolean shouldSkipPolicy() {
    return !options.isSameFilePolicyEnabled();
  }
}
