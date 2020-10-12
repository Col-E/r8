// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal.testclasses;

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.classmerging.horizontal.NonReboundFieldAccessWithMergedTypeTest.WorldGreeting;

public class NonReboundFieldAccessWithMergedTypeTestClasses {

  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  static class A {

    public WorldGreeting greeting;

    A(WorldGreeting greeting) {
      this.greeting = greeting;
    }
  }

  @NoVerticalClassMerging
  public static class B extends A {

    public B(WorldGreeting greeting) {
      super(greeting);
    }
  }
}
