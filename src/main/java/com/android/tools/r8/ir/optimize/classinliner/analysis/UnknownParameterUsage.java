// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.analysis;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;

class UnknownParameterUsage extends ParameterUsage {

  private static final UnknownParameterUsage TOP = new UnknownParameterUsage();

  private UnknownParameterUsage() {}

  public static UnknownParameterUsage getInstance() {
    return TOP;
  }

  @Override
  ParameterUsage addCastWithParameter(DexType castType) {
    return this;
  }

  @Override
  UnknownParameterUsage addFieldReadFromParameter(DexField field) {
    return this;
  }

  @Override
  UnknownParameterUsage addMethodCallWithParameterAsReceiver(InvokeMethodWithReceiver invoke) {
    return this;
  }

  @Override
  ParameterUsage externalize() {
    return this;
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

  @Override
  UnknownParameterUsage setParameterMutated() {
    return this;
  }

  @Override
  UnknownParameterUsage setParameterReturned() {
    return this;
  }

  @Override
  UnknownParameterUsage setParameterUsedAsLock() {
    return this;
  }
}
