// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package horizontalclassmerging;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;

public class BasicNestHostHorizontalClassMerging2 {
  @NeverInline
  public static void main(String[] args) {
    new A();
    new B();
  }

  @NeverInline
  private static void print(String v) {
    System.out.println("2: " + v);
  }

  @NeverClassInline
  public static class A {
    public A() {
      print("a");
    }
  }

  @NeverClassInline
  public static class B {
    public B() {
      print("b");
    }
  }
}
