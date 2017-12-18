// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package inlining;

class A {

  int a;

  A(int a) {
    this.a = a;
  }

  int a() {
    return a;
  }

  int cannotInline(int v) {
    // Cannot inline due to recursion.
    if (v > 0) {
      return cannotInline(v - 1);
    }
    return 42;
  }
}
