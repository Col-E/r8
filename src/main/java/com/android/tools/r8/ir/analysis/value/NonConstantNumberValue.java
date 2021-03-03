// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

public abstract class NonConstantNumberValue extends AbstractValue
    implements ConstantOrNonConstantNumberValue {

  @Override
  public boolean isNonConstantNumberValue() {
    return true;
  }

  @Override
  public NonConstantNumberValue asNonConstantNumberValue() {
    return this;
  }

  @Override
  public boolean isConstantOrNonConstantNumberValue() {
    return true;
  }

  @Override
  public ConstantOrNonConstantNumberValue asConstantOrNonConstantNumberValue() {
    return this;
  }

  public abstract long getAbstractionSize();
}
