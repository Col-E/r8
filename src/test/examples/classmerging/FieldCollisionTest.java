// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging;

public class FieldCollisionTest {

  private static final B SENTINEL_A = new B("A");
  private static final B SENTINEL_B = new B("B");

  public static void main(String[] args) {
    B obj = new B();
    System.out.println(obj.toString());
  }

  // Will be merged into B.
  public static class A {

    // After class merging, this field will have the same name and type as the field B.obj,
    // unless we handle the collision.
    protected final A obj = SENTINEL_A;
  }

  public static class B extends A {

    protected final String message;
    protected final B obj = SENTINEL_B;

    public B() {
      this(null);
    }

    public B(String message) {
      this.message = message;
    }

    @Override
    public String toString() {
      return obj.message + System.lineSeparator() + ((B) super.obj).message;
    }
  }
}
