// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.definitelynull;

public class TestClass {

  // Reflectively allocate A such that its instantiation cannot be identified.
  public static A getInstance() throws ReflectiveOperationException {
    try {
      return (A)
          Class.forName(
                  TestClass.class.getPackage().getName() + (System.nanoTime() > 0 ? ".A" : ".B"))
              .getConstructor()
              .newInstance();
    } catch (ClassCastException e) {
      return null;
    }
  }

  public static void main(String[] args) throws ReflectiveOperationException {
    // There are no visible instantiations of A, so the value of 'a' is concluded to be 'null'.
    A a = getInstance();
    System.out.println("value: " + a);
    System.out.println("null: " + (a == null)); // This is expected to print 'true'.
    try {
      System.out.println("call: " + a.foo()); // This is expected to throw due to null receiver.
    } catch (NullPointerException e) {
      System.out.println("call: NPE"); // This will be hit and printed.
    }
  }
}
