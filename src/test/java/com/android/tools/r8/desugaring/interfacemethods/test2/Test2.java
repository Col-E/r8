// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugaring.interfacemethods.test2;

public interface Test2 extends LeftTest, RightTest {
  default String bar(String a) {
    return "Test2::bar(" + LeftTest.super.foo(a) + " + " + RightTest.super.foo(a) + ")";
  }
}
