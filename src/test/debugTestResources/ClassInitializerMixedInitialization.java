// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

public class ClassInitializerMixedInitialization {

  static boolean b;
  static int x = 1;
  static int y;

  static {
    x = 2;
    if (b) {
      y = 1;
    } else {
      y = 2;
    }
    x = 3;
  }

  public static void main(String[] args) {
    System.out.println("x=" + x);
    System.out.println("y=" + y);
  }
}
