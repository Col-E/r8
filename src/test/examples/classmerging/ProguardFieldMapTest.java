// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging;

public class ProguardFieldMapTest {

  public static void main(String[] args) {
    B b = new B();
    b.test();
  }

  public static class A {

    public String f = "A.f";
  }

  public static class B extends A {

    public void test() {
      System.out.println(f);
    }
  }
}
