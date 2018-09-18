// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging;

public class SyntheticBridgeSignaturesTest {

  // If A is merged into ASub first, then the synthetic bridge for "void A.m(B)" will originally
  // get the signature "void ASub.m(B)". Otherwise, if B is merged into BSub first, then the
  // synthetic bridge will get the signature "void BSub.m(A)". In either case, it is important that
  // the signatures of the bridge methods are updated after all classes have been merged vertically.
  public static void main(String[] args) {
    ASub a = new ASub();
    BSub b = new BSub();
    a.m(b);
    b.m(a);
  }

  private static class A {

    public void m(B object) {
      System.out.println("In A.m()");
    }
  }

  private static class ASub extends A {}

  private static class B {

    public void m(A object) {
      System.out.println("In B.m()");
    }
  }

  private static class BSub extends B {}
}
