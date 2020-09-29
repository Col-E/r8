// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package horizontalclassmerging;

import annotations.NeverClassInline;
import annotations.NeverInline;

public class BasicNestHostHorizontalClassMerging2 {
  @NeverInline
  public static void main(String[] args) {
    A a = new A();
    B b = new B();
    if (System.currentTimeMillis() < 0) {
      System.out.println(a);
      System.out.println(b);
    }
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
