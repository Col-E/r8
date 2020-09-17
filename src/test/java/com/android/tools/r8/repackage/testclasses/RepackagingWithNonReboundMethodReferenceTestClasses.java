// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage.testclasses;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;

public class RepackagingWithNonReboundMethodReferenceTestClasses {

  @NeverClassInline
  @NoVerticalClassMerging
  static class A {

    @NeverInline
    public void greet() {
      System.out.println("Hello world!");
    }
  }

  @NeverClassInline
  public static class B extends A {}
}
