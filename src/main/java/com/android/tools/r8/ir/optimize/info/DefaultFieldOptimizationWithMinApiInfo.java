// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.utils.AndroidApiLevel;

public class DefaultFieldOptimizationWithMinApiInfo extends DefaultFieldOptimizationInfo {

  private static final DefaultFieldOptimizationWithMinApiInfo INSTANCE =
      new DefaultFieldOptimizationWithMinApiInfo();

  public static DefaultFieldOptimizationWithMinApiInfo getInstance() {
    return INSTANCE;
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
  public MutableFieldOptimizationInfo toMutableOptimizationInfo() {
    MutableFieldOptimizationInfo updatableFieldOptimizationInfo = super.toMutableOptimizationInfo();
    // Use null to specify that the min api is set to minApi.
    updatableFieldOptimizationInfo.setMinApiReferenceLevel();
    return updatableFieldOptimizationInfo;
  }
}
