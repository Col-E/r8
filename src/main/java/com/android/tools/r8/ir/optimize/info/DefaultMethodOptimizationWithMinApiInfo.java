// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.utils.AndroidApiLevel;

public class DefaultMethodOptimizationWithMinApiInfo extends DefaultMethodOptimizationInfo {

  private static final DefaultMethodOptimizationWithMinApiInfo DEFAULT_MIN_API_INSTANCE =
      new DefaultMethodOptimizationWithMinApiInfo();

  public static DefaultMethodOptimizationWithMinApiInfo getInstance() {
    return DEFAULT_MIN_API_INSTANCE;
  }

  @Override
  public boolean hasApiReferenceLevel() {
    return true;
  }

  @Override
  public AndroidApiLevel getApiReferenceLevel(AndroidApiLevel minApi) {
    return minApi;
  }

  @Override
  public UpdatableMethodOptimizationInfo mutableCopy() {
    UpdatableMethodOptimizationInfo updatableMethodOptimizationInfo = super.mutableCopy();
    // Use null to specify that the min api is set to minApi.
    updatableMethodOptimizationInfo.setApiReferenceLevel(null);
    return updatableMethodOptimizationInfo;
  }
}
