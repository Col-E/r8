// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.analysis;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;

class BottomParameterUsage extends ParameterUsage {

  private static final BottomParameterUsage BOTTOM = new BottomParameterUsage();

  private BottomParameterUsage() {}

  static BottomParameterUsage getInstance() {
    return BOTTOM;
  }

  @Override
  ParameterUsage addCastWithParameter(DexType castType) {
    return InternalNonEmptyParameterUsage.builder().addCastWithParameter(castType).build();
  }

  @Override
  ParameterUsage addFieldReadFromParameter(DexField field) {
    return InternalNonEmptyParameterUsage.builder().addFieldReadFromParameter(field).build();
  }

  @Override
  ParameterUsage addMethodCallWithParameterAsReceiver(InvokeMethodWithReceiver invoke) {
    return InternalNonEmptyParameterUsage.builder()
        .addMethodCallWithParameterAsReceiver(invoke)
        .build();
  }

  @Override
  ParameterUsage externalize() {
    return this;
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

  @Override
  ParameterUsage setParameterMutated() {
    return InternalNonEmptyParameterUsage.builder().setParameterMutated().build();
  }

  @Override
  ParameterUsage setParameterReturned() {
    return InternalNonEmptyParameterUsage.builder().setParameterReturned().build();
  }

  @Override
  ParameterUsage setParameterUsedAsLock() {
    return InternalNonEmptyParameterUsage.builder().setParameterUsedAsLock().build();
  }
}
