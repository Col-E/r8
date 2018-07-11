// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.lambdas;

public class LambdasTestClass {
  private static int ID = 0;

  private static int nextInt() {
    return ID++;
  }

  private static String next() {
    return Integer.toString(nextInt());
  }

  public interface Iface {
    String foo();
  }

  public static class IfaceUtil {
    public static void act(Iface iface) {
      System.out.println("" + next() + "> " + iface.foo());
    }
  }

  public static void main(String[] args) {
    LambdasTestClass test = new LambdasTestClass();
    test.testStatelessLambda();
    test.testStatefulLambda(next(), next());
  }

  public static String exact() {
    return next();
  }

  public static String almost(String... s) {
    return next();
  }

  private synchronized void testStatelessLambda() {
    IfaceUtil.act(() -> next());
    IfaceUtil.act(LambdasTestClass::next);
    IfaceUtil.act(LambdasTestClass::exact);
    IfaceUtil.act(LambdasTestClass::almost);
  }

  private synchronized void testStatefulLambda(String a, String b) {
    IfaceUtil.act(() -> a);
    IfaceUtil.act(() -> a + b);
    IfaceUtil.act((a + b)::toLowerCase);
  }
}
