// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bridgeremoval.hoisting.testclasses;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoVerticalClassMerging;

public class NonReboundBridgeHoistingTestClasses {

  public static Class<A> getClassA() {
    return A.class;
  }

  @NoAccessModification
  @NoVerticalClassMerging
  static class A {

    @NeverInline
    public void m() {
      System.out.println("Hello world!");
    }
  }

  @NoVerticalClassMerging
  public static class B extends A {}
}
