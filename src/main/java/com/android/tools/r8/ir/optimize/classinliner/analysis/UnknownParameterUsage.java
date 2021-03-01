// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.analysis;

class UnknownParameterUsage extends ParameterUsage {

  private static final UnknownParameterUsage TOP = new UnknownParameterUsage();

  private UnknownParameterUsage() {}

  public static UnknownParameterUsage getInstance() {
    return TOP;
  }

  @Override
  public boolean isParameterMutated() {
    return true;
  }

  @Override
  public boolean isParameterReturned() {
    return true;
  }

  @Override
  public boolean isParameterUsedAsLock() {
    return true;
  }

  @Override
  public boolean isTop() {
    return true;
  }
}
