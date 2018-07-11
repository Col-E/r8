// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.invokesuper2;

public class C1 extends C0 implements I1, I2 {
  public int m() {
    // super.m() becomes: invokespecial com/android/tools/r8/graph/invokesuper2/C0.m()I
    System.out.println(super.m());
    // I1.super.m() becomes: invokespecial com/android/tools/r8/graph/invokesuper2/I1.m:()I
    System.out.println(I1.super.m());
    // I2.super.m() becomes: invokespecial com/android/tools/r8/graph/invokesuper2/I2.m:()I
    System.out.println(I2.super.m());
    return 3;
  }
}
