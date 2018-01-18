// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.forceproguardcompatibility.defaultmethods;

public interface InterfaceWithDefaultMethods {
  default int method() {
    return 42;
  }
  default void method2(String x, int y) {
    System.out.println(y + x);
  }
}
