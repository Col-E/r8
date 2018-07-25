// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer.trivial;

public class TrivialTestClass {
  private static int ID = 0;

  private static String next() {
    return Integer.toString(ID++);
  }

  public static void main(String[] args) {
    TrivialTestClass test = new TrivialTestClass();
    test.testSimple();
    test.testSimpleWithSideEffects();
    test.testSimpleWithParams();
    test.testSimpleWithGetter();
  }

  private synchronized void testSimple() {
    System.out.println(Simple.INSTANCE.foo());
    System.out.println(Simple.INSTANCE.bar(next()));
  }

  private synchronized void testSimpleWithSideEffects() {
    System.out.println(SimpleWithSideEffects.INSTANCE.foo());
    System.out.println(SimpleWithSideEffects.INSTANCE.bar(next()));
  }

  private synchronized void testSimpleWithParams() {
    System.out.println(SimpleWithParams.INSTANCE.foo());
    System.out.println(SimpleWithParams.INSTANCE.bar(next()));
  }

  private synchronized void testSimpleWithGetter() {
    System.out.println(SimpleWithGetter.getInstance().foo());
    System.out.println(SimpleWithGetter.getInstance().bar(next()));
  }
}

