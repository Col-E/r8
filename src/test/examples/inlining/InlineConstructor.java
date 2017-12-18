// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package inlining;

class InlineConstructor {

  int a;

  @CheckDiscarded
  InlineConstructor(int a) {
    this.a = a;
  }

  InlineConstructor(long a) {
    this((int) a);
  }

  InlineConstructor(int a, int loopy) {
    this.a = a;
    // Make this too big to inline.
    if (loopy > 10) {
      throw new RuntimeException("Too big!");
    }
    for (int i = 1; i < loopy; i++) {
      this.a = this.a * i;
    }
  }

  // TODO(b/65355452): This should be inlined.
  // @CheckDiscarded
  InlineConstructor() {
    this(42, 9);
  }

  static InlineConstructor create() {
    return new InlineConstructor(10L);
  }

  static InlineConstructor createMore() {
    new InlineConstructor(0, 0);
    new InlineConstructor(0, 0);
    new InlineConstructor(0, 0);
    new InlineConstructor(0, 0);
    new InlineConstructor(0, 0);
    new InlineConstructor(0, 0);
    return new InlineConstructor();
  }
}
