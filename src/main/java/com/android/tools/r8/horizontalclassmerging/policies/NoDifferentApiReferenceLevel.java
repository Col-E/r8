// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import static com.android.tools.r8.utils.AndroidApiLevelUtils.getApiReferenceLevelForMerging;
import static com.android.tools.r8.utils.AndroidApiLevelUtils.getMembersApiReferenceLevelForMerging;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;

public class NoDifferentApiReferenceLevel extends MultiClassSameReferencePolicy<ComputedApiLevel> {

  private final AndroidApiLevelCompute apiLevelCompute;
  private final boolean enableApiCallerIdentification;
  private final boolean enableWholeProgramOptimization;
  private final ComputedApiLevel minApiLevel;

  public NoDifferentApiReferenceLevel(AppView<?> appView) {
    apiLevelCompute = appView.apiLevelCompute();
    enableApiCallerIdentification =
        appView.options().apiModelingOptions().isApiCallerIdentificationEnabled();
    enableWholeProgramOptimization = appView.enableWholeProgramOptimizations();
    minApiLevel = appView.computedMinApiLevel();
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
    ComputedApiLevel apiReferenceLevelForMerging =
        enableWholeProgramOptimization
            ? getApiReferenceLevelForMerging(apiLevelCompute, clazz)
            : getMembersApiReferenceLevelForMerging(clazz, minApiLevel);
    if (apiReferenceLevelForMerging.isUnknownApiLevel()) {
      return ineligibleForClassMerging();
    }
    return apiReferenceLevelForMerging;
  }
}
