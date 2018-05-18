// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.annotations.version3;

import com.android.tools.r8.ir.desugar.annotations.A;

public class B extends A {
  @Override
  public B method() {
    return new B();
  }
}
