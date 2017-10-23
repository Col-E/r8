// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

public class Minified {
  private static class Inner {
    public static int innerTest() {
      System.out.println("innerTest");
      return 0;
    }
  }

  private static void test() {
    System.out.println("test");
    Inner.innerTest();
  }

  private static void test(int i) {
    System.out.println("test" + i);
  }

  public static void main(String[] args) {
    test();
  }
}
