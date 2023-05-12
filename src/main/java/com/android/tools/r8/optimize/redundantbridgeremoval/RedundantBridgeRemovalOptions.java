// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.redundantbridgeremoval;

public class RedundantBridgeRemovalOptions {

  private boolean enableRetargetingOfConstructorBridgeCalls = true;

  public boolean isRetargetingOfConstructorBridgeCallsEnabled() {
    return enableRetargetingOfConstructorBridgeCalls;
  }

  public void setEnableRetargetingOfConstructorBridgeCalls(
      boolean enableRetargetingOfConstructorBridgeCalls) {
    this.enableRetargetingOfConstructorBridgeCalls = enableRetargetingOfConstructorBridgeCalls;
  }
}
