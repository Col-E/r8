// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging;

public class ProguardMethodMapTest {

  public static void main(String[] args) {
    B b = new B();
    b.method();
  }

  public static class A {

    public void method() {
      System.out.println("In A.method()");
    }
  }

  public static class B extends A {

    @Override
    public void method() {
      System.out.println("In B.method()");
      super.method();
    }
  }
}
