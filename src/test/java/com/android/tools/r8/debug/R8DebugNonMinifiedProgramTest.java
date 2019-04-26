// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;

public class R8DebugNonMinifiedProgramTest {

  public static void main(String[] args) {
    System.out.println("Hello, world: " + new A().foo());
  }

  @NeverClassInline
  public static class A {

    @NeverInline
    String foo() {
      return "Class A";
    }
  }

  @NeverClassInline
  public static class B {

    @NeverInline
    String bar() {
      return "Class B";
    }
  }
}
