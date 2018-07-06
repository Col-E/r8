// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package shaking19;

public class Shaking {

  public static void main(String[] args) {
    A obj = new B();
    obj.m();
  }

  public static class A {

    // Since A is never instantiated and B overrides method m(), this is dead code.
    public void m() {
      System.out.println("In A.m()");
    }
  }

  public static class B extends A {

    @Override
    public void m() {
      System.out.println("In B.m()");
    }
  }
}
