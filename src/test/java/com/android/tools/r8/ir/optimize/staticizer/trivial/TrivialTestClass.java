// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer.trivial;

import com.android.tools.r8.NeverInline;

public class TrivialTestClass {
  private static int ID = 0;

  private static String next() {
    return Integer.toString(ID++);
  }

  public static void main(String[] args) {
    TrivialTestClass test = new TrivialTestClass();
    test.testSimple();
    test.testSimpleWithPhi(args.length);
    test.testSimpleWithSideEffects();
    test.testSimpleWithParams();
    test.testSimpleWithGetter();
  }

  @NeverInline
  private void testSimple() {
    System.out.println(Simple.INSTANCE.foo());
    System.out.println(Simple.INSTANCE.bar(next()));
  }

  @NeverInline
  private void testSimpleWithPhi(int arg) {
    switch (arg) {
      case 0:
        System.out.println(SimpleWithPhi.foo() + " " + true);
        break;
      case 2:
        System.out.println(SimpleWithPhi.foo() + " " + false);
        break;
      default:
        System.out.println(SimpleWithPhi.bar(next()));
        break;
    }
  }

  @NeverInline
  private void testSimpleWithSideEffects() {
    System.out.println(SimpleWithSideEffects.INSTANCE.foo());
    System.out.println(SimpleWithSideEffects.INSTANCE.bar(next()));
  }

  @NeverInline
  private void testSimpleWithParams() {
    System.out.println(SimpleWithParams.INSTANCE.foo());
    System.out.println(SimpleWithParams.INSTANCE.bar(next()));
  }

  @NeverInline
  private void testSimpleWithGetter() {
    System.out.println(SimpleWithGetter.getInstance().foo());
    System.out.println(SimpleWithGetter.getInstance().bar(next()));
  }
}

