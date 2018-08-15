// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugaring.interfacemethods.default0;

import java.util.Comparator;

public class TestMainDefault0 {
  public static void main(String[] args) {
    System.out.println("TestMainDefault0::test()");
    System.out.println(args[0]);
    if (args[0].equals("true")) {
      Comparator<String> comparator = String::compareTo;
      comparator = comparator.reversed();
      System.out.println(comparator.compare("A", "B"));
      System.out.println(comparator.compare("B", "B"));
      System.out.println(comparator.compare("B", "C"));
    }
  }
}
