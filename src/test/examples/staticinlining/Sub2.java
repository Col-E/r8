// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package staticinlining;

public class Sub2 extends SuperClass {

  public static void doNotInlineMe() {
    System.out.println("Do not inline me 1");
    System.out.println("Do not inline me 2");
    System.out.println("Do not inline me 3");
    System.out.println("Do not inline me 4");
    System.out.println("Do not inline me 5");
    System.out.println("Do not inline me 6");
    System.out.println("Do not inline me 7");
    System.out.println("Do not inline me 8");
    System.out.println("Do not inline me 9");
    System.out.println("Do not inline me 10");
  }

}
