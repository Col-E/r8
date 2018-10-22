// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging;

public class ArrayTypeCollisionTest {

  public static void main(String[] args) {
    method(new A[] {});
    method(new B[] {});
  }

  private static void method(A[] obj) {
    System.out.println("In method(A[])");
  }

  private static void method(B[] obj) {
    System.out.println("In method(B[])");
  }

  // A cannot be merged into B because that would lead to a collision.
  public static class A {}

  public static class B extends A {}
}
