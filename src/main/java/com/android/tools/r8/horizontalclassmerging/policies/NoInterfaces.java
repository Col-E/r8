// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.utils.InternalOptions.HorizontalClassMergerOptions;

public class NoInterfaces extends SingleClassPolicy {

  private final Mode mode;
  private final HorizontalClassMergerOptions options;

  public NoInterfaces(AppView<?> appView, Mode mode) {
    this.mode = mode;
    this.options = appView.options().horizontalClassMergerOptions();
  }

  @Override
  public boolean canMerge(DexProgramClass clazz) {
    return !clazz.isInterface();
  }

  @Override
  public boolean shouldSkipPolicy() {
    return options.isInterfaceMergingEnabled(mode);
  }

  @Override
  public String getName() {
    return "NoInterfaces";
  }
}
