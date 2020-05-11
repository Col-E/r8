// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing.examplelib2;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;

public class JavaLibrary2 {

  @NeverClassInline
  public enum LibEnum2 {
    A,
    B
  }

  @NeverInline
  private static LibEnum2 getEnum() {
    return System.currentTimeMillis() > 0 ? LibEnum2.A : LibEnum2.B;
  }

  public static void libCall() {
    System.out.println(getEnum().ordinal());
    System.out.println(0);
  }
}
