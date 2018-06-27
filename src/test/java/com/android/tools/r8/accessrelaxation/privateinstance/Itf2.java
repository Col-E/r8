// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.accessrelaxation.privateinstance;

public interface Itf2 {
  String foo2();

  default String foo2(int i) {
    return "Itf2::foo2(" + i + ") >> " + foo2();
  }
}
