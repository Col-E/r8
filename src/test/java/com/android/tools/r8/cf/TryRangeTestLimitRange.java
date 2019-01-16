// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import com.android.tools.r8.NeverInline;

public class TryRangeTestLimitRange {

  @NeverInline
  public static float doSomething(int x) throws Exception {
    if (x == 42) {
      throw new Exception("is 42");
    } else {
      return 1;
    }
  }

  public static void main(String[] args) {
    int x = args.length;
    int y = x + 1;
    try {
      doSomething(y);
    } catch (Exception ex) {
      System.out.println(x + ": " + y);
    }
  }
}
