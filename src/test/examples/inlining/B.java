// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package inlining;

class B extends A {

  B(int a) {
    super(a);
  }

  int cannotInline(int v) {
    return -1;
  }

  int callMethodInSuper() {
    return super.cannotInline(10);
  }
}
