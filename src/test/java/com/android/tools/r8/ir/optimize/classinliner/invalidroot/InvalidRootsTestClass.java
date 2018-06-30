// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.invalidroot;

public class InvalidRootsTestClass {
  private static int ID = 0;

  private static String next() {
    return Integer.toString(ID++);
  }

  public static void main(String[] args) {
    InvalidRootsTestClass test = new InvalidRootsTestClass();
    test.testExtraNeverReturnsNormally();
    test.testDirectNeverReturnsNormally();
    test.testInitNeverReturnsNormally();
    test.testRootInvalidatesAfterInlining();
  }

  private synchronized void testExtraNeverReturnsNormally() {
    testExtraNeverReturnsNormallyA();
    testExtraNeverReturnsNormallyB();

    try {
      NeverReturnsNormally a = new NeverReturnsNormally();
      neverReturnsNormallyExtra(next(), a);
    } catch (RuntimeException re) {
      System.out.println(re.toString());
    }
  }

  private synchronized void testExtraNeverReturnsNormallyA() {
    try {
      neverReturnsNormallyExtra(next(), null);
    } catch (RuntimeException re) {
      System.out.println(re.toString());
    }
  }

  private synchronized void testExtraNeverReturnsNormallyB() {
    try {
      neverReturnsNormallyExtra(next(), null);
    } catch (RuntimeException re) {
      System.out.println(re.toString());
    }
  }

  private synchronized void testDirectNeverReturnsNormally() {
    try {
      NeverReturnsNormally a = new NeverReturnsNormally();
      System.out.println(a.foo());
    } catch (RuntimeException re) {
      System.out.println(re.toString());
    }
  }

  private synchronized void testInitNeverReturnsNormally() {
    try {
      new InitNeverReturnsNormally();
    } catch (RuntimeException re) {
      System.out.println(re.toString());
    }
  }

  private void neverReturnsNormallyExtra(String prefix, NeverReturnsNormally a) {
    throw new RuntimeException("neverReturnsNormallyExtra(" +
        prefix + ", " + (a == null ? "null" : a.foo()) + "): " + next());
  }

  public static class NeverReturnsNormally {
    public String foo() {
      throw new RuntimeException("NeverReturnsNormally::foo(): " + next());
    }
  }

  public static class InitNeverReturnsNormally {
    public InitNeverReturnsNormally() {
      throw new RuntimeException("InitNeverReturnsNormally::init(): " + next());
    }

    public String foo() {
      return "InitNeverReturnsNormally::foo(): " + next();
    }
  }

  private synchronized void testRootInvalidatesAfterInlining() {
    A a = new A();
    try {
      notInlinedExtraMethod(next(), a);
      System.out.println(new B().foo() + " " + next());
      testRootInvalidatesAfterInliningA(a);
      testRootInvalidatesAfterInliningB(a);
    } catch (RuntimeException re) {
      System.out.println(re.toString());
    }
  }

  private void notInlinedExtraMethod(String prefix, A a) {
    System.out.println("notInlinedExtraMethod(" +
        prefix + ", " + (a == null ? "null" : a.foo()) + "): " + next());
    if (a != null) {
      throw new RuntimeException(
          "notInlinedExtraMethod(" + prefix + ", " + a.foo() + "): " + next());
    }
    System.out.println("notInlinedExtraMethod(" + prefix + ", null): " + next());
  }

  private void testRootInvalidatesAfterInliningA(A a) {
    notInlinedExtraMethod(next(), a);
  }

  private void testRootInvalidatesAfterInliningB(A a) {
    notInlinedExtraMethod(next(), a);
  }

  public static class A {
    public String foo() {
      return "B::foo(" + next() + ")";
    }
  }

  public static class B {
    public String foo() {
      return "B::foo(" + next() + ")";
    }
  }
}
