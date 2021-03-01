// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.analysis;

class BottomParameterUsage extends ParameterUsage {

  private static final BottomParameterUsage BOTTOM = new BottomParameterUsage();

  private BottomParameterUsage() {}

  static BottomParameterUsage getInstance() {
    return BOTTOM;
  }

  @Override
  public boolean isBottom() {
    return true;
  }

  @Override
  public boolean isParameterMutated() {
    return false;
  }

  @Override
  public boolean isParameterReturned() {
    return false;
  }

  @Override
  public boolean isParameterUsedAsLock() {
    return false;
  }
}
