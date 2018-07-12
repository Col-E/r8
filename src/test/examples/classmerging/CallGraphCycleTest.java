// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging;

public class CallGraphCycleTest {

  public static void main(String[] args) {
    new B(true);
  }

  public static class A {

    public A(boolean instantiateB) {
      if (instantiateB) {
        new B(false);
      }
      System.out.println("A(" + instantiateB + ")");
    }
  }

  public static class B extends A {

    public B(boolean instantiateBinA) {
      super(instantiateBinA);
      System.out.println("B(" + instantiateBinA + ")");
    }
  }
}
