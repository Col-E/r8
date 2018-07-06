// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging;

public class MethodCollisionTest {

  public static void main(String[] args) {
    new B().m();
    new D().m();
  }

  public static class A {

    // After class merging, this method will have the same signature as the method B.m,
    // unless we handle the collision.
    private A m() {
      System.out.println("A.m");
      return null;
    }

    public void invokeM() {
      m();
    }
  }

  public static class B extends A {

    private B m() {
      System.out.println("B.m");
      invokeM();
      return null;
    }
  }

  public static class C {

    // After class merging, this method will have the same signature as the method D.m,
    // unless we handle the collision.
    public C m() {
      System.out.println("C.m");
      return null;
    }
  }

  public static class D extends C {

    public D m() {
      System.out.println("D.m");
      super.m();
      return null;
    }
  }


}
