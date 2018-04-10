// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

interface InterfaceWithDefaultAndStaticMethods {
  default void doSomething(String msg) {
    String name = getClass().getName();
    System.out.println(name + ": " + msg);
  }

  static void printString(String msg) {
    System.out.println(msg);
  }
}
