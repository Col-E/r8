// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.b113138046;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR })
@Retention(RetentionPolicy.CLASS)
@interface Keep {
}

class Data {
}

class Handler {
}

class Outer {

  @Keep
  // Inner class should be private such that members are not single targeted.
  final private static class Inner {

    @Keep
    private final Data mData;

    public Inner() {
      mData = init();
    }

    // Keeping members based on annotation @Keep makes this method live.
    // But, to reproduce b/113138046, we need to make it just targeted, not live.
    @Keep
    private native void foo(Handler h);

    @Keep
    private static native Data init();
  }

  private Inner mInner;

  public Outer() {
    mInner = new Inner();
  }

  public void onEvent(Handler h) {
    // This will add synthetic foo(...) and make native foo(...) targeted.
    mInner.foo(h);
  }
}
