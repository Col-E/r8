// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package inlining;

class Nullability {
  private final int f;

  Nullability(int f) {
    this.f = f;
  }

  int inlinable(A a) {
    // NPE is preserved when the receiver is null.
    return this.f + a.a();
  }

  int notInlinable(A a) {
    // a.a() is still invoked even though the receiver could be null.
    return a.a() + this.f;
  }

  int conditionalOperator(A a) {
    // a is not null when a.a() is invoked.
    return a != null ? a.a() : -1;
  }

  enum Factor {
    ONE, TWO, THREE, SIX
  }

  int moreControlFlows(A a, Factor b) {
    int result;
    switch (b) {
      case ONE:
        result = 1;
        break;
      case TWO:
        result = 2;
        break;
      case THREE:
        result = 3;
        break;
      case SIX:
        result = 6;
        break;
      default:
        result = 0;
        break;
    }
    // When reaching here, the nullability analysis should know that all possible paths do not have
    // instructions with side effects.
    if (a != null && result != 0) {
      // Thus, the invocation below is the first instruction with side effect.
      // Also, a is not null here, hence a.a() is inlinable.
      result *= a.a();
    }
    return result;
  }
}
