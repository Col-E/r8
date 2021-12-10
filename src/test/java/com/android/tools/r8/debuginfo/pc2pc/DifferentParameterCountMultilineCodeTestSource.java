// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo.pc2pc;

class DifferentParameterCountMultilineCodeTestSource {

  public static void args0() {
    if (System.nanoTime() < 0) {
      System.out.println("Not hit...");
    }
    throw new IllegalStateException("DONE!");
  }

  public static void args1(String arg1) {
    if (!arg1.equals("asdf")) {
      args0();
    } else {
      throw new ArithmeticException("WAT");
    }
  }

  public static void args2(String arg1, Object arg2) {
    if (!arg1.equals(arg2)) {
      args1(arg1);
    } else {
      throw new ArithmeticException("NO");
    }
  }

  public static void main(String[] args) {
    args2(System.nanoTime() < 0 ? args[0] : "foo", args.length > 0 ? args[0] : "bar");
    throw new ArithmeticException("NO AGAIN");
  }
}
