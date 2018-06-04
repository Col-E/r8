// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b78493232;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Regress78493232Utils {

  public static void println(String msg) {
    System.out.println(msg);
  }

  public static void println(int msg) {
    System.out.println(msg);
  }

  private static void printByteArray(byte[] array) {
    List<String> strings = new ArrayList<>(array.length);
    for (byte b : array) {
      strings.add(Byte.toString(b));
    }
    System.out.println(String.join(",", strings));
  }

  public static int getHash(int a, int b, byte[] c) {
    return a + 7 * b + 13 * Arrays.hashCode(c);
  }

  public static void compare(String output, int iterations) {
    String expected = "java.security.SecureRandom";
    if (expected.equals(output)) {
      return;
    }
    System.out.println(
        "After " + iterations + " iterations, expected \"" +
        expected + "\", but got \"" + output + "\"");
    // Exit with code 0 to allow test to use ensureSameOutput().
    System.exit(0);
  }

  public static void compareHash(int a, int b, byte[] c, int iterations) {
    int expected = 419176645;
    int output = getHash(a, b, c);
    if (output == expected) {
      return;
    }
    System.out.println(
        "After " + iterations + " iterations, expected hash " +
        expected + ", but got " + output);
    System.out.println("staticIntA: " + a);
    System.out.println("staticIntB: " + b);
    System.out.print("staticIntByteArray: ");
    printByteArray(c);
    // Exit with code 0 to allow test to use ensureSameOutput().
    System.exit(0);
  }
}
