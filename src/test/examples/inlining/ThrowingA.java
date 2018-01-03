// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package inlining;

class ThrowingA extends A {

  ThrowingA(int a) {
    super(a);
  }

  @Override
  int a() {
    throw new AssertionError("Tests should catch other exceptions prior to reaching here.");
  }
}
