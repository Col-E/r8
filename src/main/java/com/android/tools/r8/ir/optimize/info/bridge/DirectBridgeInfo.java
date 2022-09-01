// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info.bridge;

import com.android.tools.r8.graph.DexMethod;

/**
 * Optimization info computed for bridge methods that use an invoke-direct instruction.
 *
 * <p>If the method is {@code String A.m(Object)} and {@link #invokedMethod} is {@code Object
 * B.m(String)}, then the bridge is implemented as:
 *
 * <pre>
 *   java.lang.String A.m(java.lang.Object o) {
 *     v0 <- Argument
 *     v1 <- CheckCast v0, java.lang.String
 *     v2 <- InvokeDirect { v0, v1 }, java.lang.Object B.m(java.lang.String)
 *     v3 <- CheckCast v2, java.lang.String
 *     Return v3
 *   }
 * </pre>
 *
 * <p>This currently does not allow any permutation of the argument order, and it also does not
 * allow constants to be passed as arguments.
 */
public class DirectBridgeInfo extends BridgeInfo {

  // The targeted method.
  private final DexMethod invokedMethod;

  public DirectBridgeInfo(DexMethod invokedMethod) {
    this.invokedMethod = invokedMethod;
  }

  public DexMethod getInvokedMethod() {
    return invokedMethod;
  }

  @Override
  public boolean isDirectBridgeInfo() {
    return true;
  }

  @Override
  public DirectBridgeInfo asDirectBridgeInfo() {
    return this;
  }
}
