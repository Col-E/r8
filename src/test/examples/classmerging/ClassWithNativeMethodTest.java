// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package classmerging;

public class ClassWithNativeMethodTest {

  public static void main(String[] args) {
    B obj = new B();

    // Make sure that A.method is not removed by tree shaking.
    if (args.length == 42) {
      obj.method();
    }
  }

  public static class A {
    public native void method();
  }

  public static class B extends A {}
}
