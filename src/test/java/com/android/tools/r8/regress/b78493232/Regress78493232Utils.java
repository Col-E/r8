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

  public static void printByteArray(byte[] array) {
    List<String> strings = new ArrayList<>(array.length);
    for (byte b : array) {
      strings.add(Byte.toString(b));
    }
    System.out.println(String.join(",", strings));
  }

  public static int getHash(int a, int b, byte[] c) {
    return a + 7 * b + 13 * Arrays.hashCode(c);
  }

  public static void printHash(int a, int b, byte[] c) {
    System.out.println(getHash(a, b, c));
  }
}
