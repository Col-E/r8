// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.shaking.RuntimeTypeCheckInfo;
import com.android.tools.r8.utils.InternalOptions;

public class NoDirectRuntimeTypeChecks extends SingleClassPolicy {

  private final InternalOptions options;
  private final RuntimeTypeCheckInfo runtimeTypeCheckInfo;

  public NoDirectRuntimeTypeChecks(AppView<?> appView, RuntimeTypeCheckInfo runtimeTypeCheckInfo) {
    this.options = appView.options();
    this.runtimeTypeCheckInfo = runtimeTypeCheckInfo;
  }

  @Override
  public boolean canMerge(DexProgramClass clazz) {
    return !runtimeTypeCheckInfo.isRuntimeCheckType(clazz);
  }

  @Override
  public String getName() {
    return "NoDirectRuntimeTypeChecks";
  }

  @Override
  public boolean shouldSkipPolicy() {
    return options.horizontalClassMergerOptions().isIgnoreRuntimeTypeChecksForTestingEnabled();
  }
}
