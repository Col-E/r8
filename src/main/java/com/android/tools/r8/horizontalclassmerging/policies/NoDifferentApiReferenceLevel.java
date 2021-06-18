// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.androidapi.AndroidApiReferenceLevelCache;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;
import com.android.tools.r8.utils.AndroidApiLevel;

public class NoDifferentApiReferenceLevel extends MultiClassSameReferencePolicy<AndroidApiLevel> {

  private final AndroidApiReferenceLevelCache apiReferenceLevelCache;
  private final AndroidApiLevel minApi;
  // TODO(b/188388130): Remove when stabilized.
  private final boolean enableApiCallerIdentification;

  public NoDifferentApiReferenceLevel(AppView<?> appView) {
    apiReferenceLevelCache = AndroidApiReferenceLevelCache.create(appView);
    minApi = appView.options().minApiLevel;
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
  public AndroidApiLevel getMergeKey(DexProgramClass clazz) {
    assert enableApiCallerIdentification;
    return clazz.getApiReferenceLevel(minApi, apiReferenceLevelCache::lookupMax);
  }
}
