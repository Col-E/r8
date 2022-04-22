// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

public class BottomCfFrameState extends CfFrameState {

  private static final BottomCfFrameState INSTANCE = new BottomCfFrameState();

  private BottomCfFrameState() {}

  static BottomCfFrameState getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean equals(Object other) {
    return this == other;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }
}
