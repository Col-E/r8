// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b122887884;

public class Regress122887884 {

  public static int[] foo(String[] args) {
    if (args.length == 0) {
      return new int[] {0, 0};
    }
    String first = args[0];
    try {
      String[] split = first.split("");
      if (split.length != 2) {
        // This results in a new-array v2 v2 int[] at which point the exception handler must split.
        return new int[] {0, 0};
      }
      return new int[] {1, 1};
    } catch (Throwable t) {
      return new int[] {0, 0};
    }
  }

  public static void main(String[] args) {
    System.out.println(foo(args)[0]);
  }
}
