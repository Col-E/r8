// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package inlining;

class InlineConstructorOfInner {

  class Inner {

    int a;

    @CheckDiscarded
    Inner(int a) {
      this.a = a;
    }

    // This is not inlined, even though it is only called once, as it is only called from a
    // non-constructor, and will set a field (the outer object) before calling the other
    // constructor.
    Inner(long a) {
      this((int) a);
    }

    public Inner create() {
      return new Inner(10L);
    }
  }

  Inner inner;

  InlineConstructorOfInner() {
    inner = new Inner(10L).create();
  }
}
