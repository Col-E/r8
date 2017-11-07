// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

public class StackValue extends Value {

  private final int height;

  public StackValue(ValueType type, int height) {
    super(Value.UNDEFINED_NUMBER, type, null);
    this.height = height;
    assert height >= 0;
  }

  @Override
  public String toString() {
    return "s" + height;
  }
}
