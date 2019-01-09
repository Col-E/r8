// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import com.android.tools.r8.NeverInline;

public class TryRangeTest {

  @NeverInline
  public static float doSomething(int x) throws Exception {
    if (x == 42) {
      throw new Exception("is 42");
    } else {
      return 1;
    }
  }

  @NeverInline
  public static void test(int count) {
    int x = count;
    float y;
    if (x == 7) {
      try {
        y = doSomething(x);
      } catch (Exception e) {
        System.out.println(x);
        return;
      }
    } else {
      System.out.println(x);
      y = 7;
    }
    System.out.println(y);
  }

  public static void main(String[] args) {
    test(10);
  }
}
