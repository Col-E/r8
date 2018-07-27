// Copyright (c) 2018 the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package assumevalues6;

public class Assumevalues {
  public static int field = 0;
  public static int field2 = 2;
  public static int field3 = 2;

  public static void main(String[] args) {
    if (0 > field) {
      System.out.println("NOPE1");
    }
    if (field < 0) {
      System.out.println("NOPE2");
    }
    if (field3 == 0) {
      System.out.println("NOPE3");
    }
    if (field3 != 0) {
      System.out.println("YUP1");
    }
    if (2 < field) {
      System.out.println("NOPE4");
    }
    if (field > 2) {
      System.out.println("NOPE5");
    }
    if (field2 < field) {
      System.out.println("NOPE6");
    }
    if (field > field2) {
      System.out.println("NOPE7");
    }
    if (field <= field2) {
      System.out.println("YUP2");
    }
    if (field2 >= field) {
      System.out.println("YUP3");
    }
    System.out.println("OK");
  }
}
