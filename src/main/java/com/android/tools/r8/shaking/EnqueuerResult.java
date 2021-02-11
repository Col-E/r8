// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.ir.desugar.lambda.LambdaDesugaringLens;

public class EnqueuerResult {

  private final AppInfoWithLiveness appInfo;
  private final LambdaDesugaringLens lambdaDesugaringLens;

  EnqueuerResult(AppInfoWithLiveness appInfo, LambdaDesugaringLens lambdaDesugaringLens) {
    this.appInfo = appInfo;
    this.lambdaDesugaringLens = lambdaDesugaringLens;
  }

  public AppInfoWithLiveness getAppInfo() {
    return appInfo;
  }

  public boolean hasLambdaDesugaringLens() {
    return lambdaDesugaringLens != null;
  }

  public LambdaDesugaringLens getLambdaDesugaringLens() {
    return lambdaDesugaringLens;
  }
}
