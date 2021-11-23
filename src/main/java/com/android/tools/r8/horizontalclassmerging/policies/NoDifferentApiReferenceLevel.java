// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;

public class NoDifferentApiReferenceLevel extends MultiClassSameReferencePolicy<ComputedApiLevel> {

  private final AndroidApiLevelCompute apiLevelCompute;
  private final AppView<?> appView;
  // TODO(b/188388130): Remove when stabilized.
  private final boolean enableApiCallerIdentification;

  public NoDifferentApiReferenceLevel(AppView<?> appView) {
    apiLevelCompute = AndroidApiLevelCompute.create(appView);
    this.appView = appView;
    enableApiCallerIdentification =
        appView.options().apiModelingOptions().enableApiCallerIdentification;
  }

  @Override
  public boolean shouldSkipPolicy() {
    return !enableApiCallerIdentification;
  }

  @Override
  public String getName() {
    return "NoDifferentApiReferenceLevel";
  }

  @Override
  public ComputedApiLevel getMergeKey(DexProgramClass clazz) {
    assert enableApiCallerIdentification;
    return clazz.getApiReferenceLevel(appView, apiLevelCompute);
  }
}
