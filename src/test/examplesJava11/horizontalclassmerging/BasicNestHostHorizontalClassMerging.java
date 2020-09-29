// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package horizontalclassmerging;

import annotations.NeverClassInline;
import annotations.NeverInline;

public class BasicNestHostHorizontalClassMerging {
  // Prevent merging with BasicNestHostHorizontalClassMerging2.
  private String name;

  private BasicNestHostHorizontalClassMerging(String name) {
    this.name = name;
  }

  @NeverInline
  private void print(String v) {
    /* if (System.currentTimeMillis() < 0) {
      name = "never";
    } */
    System.out.println(name + ": " + v);
  }

  public static void main(String[] args) {
    BasicNestHostHorizontalClassMerging host = new BasicNestHostHorizontalClassMerging("1");
    A a = new A(host);
    B b = new B(host);
    BasicNestHostHorizontalClassMerging2.main(args);
  }

  @NeverClassInline
  public static class A {
    public A(BasicNestHostHorizontalClassMerging parent) {
      parent.print("a");
    }
  }

  @NeverClassInline
  public static class B {
    public B(BasicNestHostHorizontalClassMerging parent) {
      parent.print("b");
    }
  }
}
