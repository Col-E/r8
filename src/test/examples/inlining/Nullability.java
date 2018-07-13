// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package inlining;

class Nullability {
  private final int f;
  public final int publicField;

  Nullability(int f) {
    this.f = f;
    this.publicField = f;
  }

  int inlinable(A a) {
    // NPE is preserved when the receiver is null.
    return this.f + a.a();
  }

  int inlinableWithPublicField(A a) {
    // NPE is preserved when the receiver is null.
    return this.publicField + a.a();
  }

  int inlinableWithControlFlow(A a) {
    // NPE is always preserved when the receiver is null.
    return a != null ? this.f : this.publicField;
  }

  int notInlinableDueToMissingNpe(A a) {
    // NPE is not preserved when the receiver is null and 'a' is null.
    return a != null ? this.f : -1;
  }

  int notInlinableDueToSideEffect(A a) {
    // NPE is not preserved when the receiver is null and a is not null.
    return a != null ? a.a() : this.f;
  }

  int notInlinable(A a) {
    // a.a() is still invoked even though the receiver could be null.
    return a.a() + this.f;
  }

  int conditionalOperator(A a) {
    // a is not null when a.b() is invoked.
    return a != null ? a.b() : -1;
  }

  int notInlinableOnThrow(Throwable t) throws Throwable {
    // NPE is not preserved if t is not a NullPointerException.
    throw t;
  }

  int notInlinableBecauseHidesNpe() {
    try {
      return publicField;
    } catch (NullPointerException e) {
      return -1;
    }
  }

  public int notInlinableDueToMissingNpeBeforeThrow(Throwable t) throws Throwable {
    try {
      throw t;
    } catch (UnusedException e) {
      return this.publicField;
    }
  }

  static class UnusedException extends Throwable {}

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
      // Also, a is not null here, hence a.b() is inlinable.
      result *= a.b();
    }
    return result;
  }
}
