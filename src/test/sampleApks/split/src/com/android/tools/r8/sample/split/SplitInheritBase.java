// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.sample.split;

public class SplitInheritBase extends BaseClass {

  public SplitInheritBase(int initialValue) {
    super(initialValue);
    initialValue = calculate(initialValue);
  }

  public int calculate(int x) {
    return super.calculate(x) * 2;
  }
}
