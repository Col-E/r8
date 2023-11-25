// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package nesthostexample;

public class NestHierachy {
  abstract static class InnerSuper {
    public void m1() {
      System.out.println("m1");
    }

    private void m2() {
      System.out.println("m2");
    }

    private void m3() {
      System.out.println("m3");
    }

    public static void s1() {
      System.out.println("s1");
    }

    private static void s2() {
      System.out.println("s2");
    }
  }

  static class InnerSub extends InnerSuper {
    public void m1() {
      super.m1();
    }

    public void m2() {
      super.m2();
    }

    private void m3() {
      super.m3();
    }

    public static void s1() {
      InnerSuper.s1();
    }

    private static void s2() {
      InnerSuper.s2();
    }
  }

  public static void callOnInnerSuper(InnerSuper innerSuper) {
    innerSuper.m1();
    innerSuper.m2();
    innerSuper.m3();
    innerSuper.s1();
    innerSuper.s2();
  }

  public static void callOnInnerSub(InnerSub innerSub) {
    innerSub.m1();
    innerSub.m2();
    innerSub.m3();
    innerSub.s1();
    innerSub.s2();
  }

  public static void main(String[] args) {
    callOnInnerSuper(new InnerSub());
    callOnInnerSub(new InnerSub());
    InnerSuper.s1();
    InnerSuper.s2();
    InnerSub.s1();
    InnerSub.s2();
  }
}
