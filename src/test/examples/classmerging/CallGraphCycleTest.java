// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging;

public class CallGraphCycleTest {

  public static void main(String[] args) {
    new B(args.length == 0, args.length == 1);
  }

  public static class A {

    public A(boolean instantiateB, boolean alwaysFalse) {
      if (instantiateB) {
        new B(alwaysFalse, alwaysFalse);
      }
      System.out.println("A(" + instantiateB + ")");
    }
  }

  public static class B extends A {

    public B(boolean instantiateBinA, boolean alwaysFalse) {
      super(instantiateBinA, alwaysFalse);
      System.out.println("B(" + instantiateBinA + ")");
    }
  }
}
