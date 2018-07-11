// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.invokesuper2;

public class C2 extends C0 implements I3, I4 {
  public int m() {
    // super.m() becomes: invokespecial com/android/tools/r8/graph/invokesuper2/C0.m()I
    System.out.println(super.m());
    // I1.super.m() becomes: invokespecial com/android/tools/r8/graph/invokesuper2/I3.m:()I
    System.out.println(I3.super.m());
    // I2.super.m() becomes: invokespecial com/android/tools/r8/graph/invokesuper2/I4.m:()I
    System.out.println(I4.super.m());
    return 3;
  }
}
