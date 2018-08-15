// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugaring.interfacemethods.default1;

public class TestMainDefault1 {
  static boolean shouldCallStaticOnInterface;

  public static void main(String[] args) {
    System.out.println("TestMainDefault1::test()");
    System.out.println(args[0]);
    shouldCallStaticOnInterface = args[0].equals("true");
    Derived1 comparator = new Derived1();
    System.out.println(comparator.compare("A", "B"));
    System.out.println(comparator.reversed().compare("B", "B"));
    System.out.println(comparator.doubleReversed().compare("B", "C"));
  }
}
