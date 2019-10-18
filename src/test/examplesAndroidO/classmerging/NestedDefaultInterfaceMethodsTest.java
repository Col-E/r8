// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging;

public class NestedDefaultInterfaceMethodsTest {

  public static void main(String[] args) {
    C obj = new C();
    obj.m();

    // Ensure that the instantiations are not dead code eliminated.
    escape(obj);
  }

  @NeverInline
  static void escape(Object o) {
    if (System.currentTimeMillis() < 0) {
      System.out.println(o);
    }
  }

  public interface A {

    default void m() {
      System.out.println("In A.m()");
    }
  }

  public interface B extends A {

    @Override
    default void m() {
      System.out.println("In B.m()");
      A.super.m();
    }
  }

  public static class C implements B {

    @Override
    public void m() {
      System.out.println("In C.m()");
      B.super.m();
    }
  }
}
