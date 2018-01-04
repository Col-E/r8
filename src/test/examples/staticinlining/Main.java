// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package staticinlining;

public class Main {
  public static String tag = null;

  public static void printMessage(String msg) {
    System.out.println(msg);
  }

  public static void test() {
    tag = "Sub1";
    Sub1.inlineMe();  // triggers class init of Sub1 (and its hierarchy)
    tag = "Sub2";
    Sub2.doNotInlineMe();  // triggers class init of Sub2 (and its hierarchy)
    printMessage("SuperClass.INIT_TAG=" + SuperClass.INIT_TAG);
  }

  public static void main(String[] args) {
    test();
  }

}
