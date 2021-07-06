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
  public boolean hasApiReferenceLevelForDefinition() {
    return true;
  }

  @Override
  public AndroidApiLevel getApiReferenceLevelForDefinition(AndroidApiLevel minApi) {
    return minApi;
  }

  @Override
  public boolean hasApiReferenceLevelForCode() {
    return true;
  }

  @Override
  public AndroidApiLevel getApiReferenceLevelForCode(AndroidApiLevel minApi) {
    return minApi;
  }

  @Override
  public MutableMethodOptimizationInfo toMutableOptimizationInfo() {
    MutableMethodOptimizationInfo updatableMethodOptimizationInfo =
        super.toMutableOptimizationInfo();
    // Use null to specify that the min api is set to minApi.
    updatableMethodOptimizationInfo.setMinApiReferenceLevel();
    return updatableMethodOptimizationInfo;
  }
}
