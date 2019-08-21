// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.singletarget.interfacedefault;

public interface I {
  void confusing(int i);

  default void confusing() {
    System.out.println("I::confusing()");
    confusing(0);
  }
}
