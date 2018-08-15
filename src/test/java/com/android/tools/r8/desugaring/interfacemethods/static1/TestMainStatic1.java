// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugaring.interfacemethods.static1;

import java.util.Comparator;
import java.util.function.Supplier;

public class TestMainStatic1 {
  public static void main(String[] args) {
    System.out.println("TestMainStatic1::test()");
    System.out.println(args[0]);
    if (args[0].equals("true")) {
      Supplier<Comparator<String>> lambda = Comparator::naturalOrder;
      Comparator<String> comparator = lambda.get();
      System.out.println(comparator.compare("A", "B"));
      System.out.println(comparator.compare("B", "B"));
      System.out.println(comparator.compare("B", "C"));
    }
  }
}
