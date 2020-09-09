// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bridgeremoval.hoisting.testclasses;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.bridgeremoval.hoisting.BridgeHoistingAccessibilityTest;
import com.android.tools.r8.bridgeremoval.hoisting.BridgeHoistingAccessibilityTest.CWithRangedInvoke;

public class BridgeHoistingAccessibilityTestClasses {

  @NoVerticalClassMerging
  public static class A {

    @NeverInline
    public Object m(String arg) {
      return System.currentTimeMillis() > 0 ? arg : null;
    }
  }

  public static class User {

    @NeverInline
    public static String invokeBridgeC(BridgeHoistingAccessibilityTest.C instance) {
      return instance.bridgeC(" world!");
    }
  }

  @NoVerticalClassMerging
  public static class AWithRangedInvoke {

    @NeverInline
    public Object m(String arg, int a, int b, int c, int d, int e) {
      return System.currentTimeMillis() > 0 ? arg + a + b + c + d + e : null;
    }
  }

  public static class UserWithRangedInvoke {

    @NeverInline
    public static String invokeBridgeC(CWithRangedInvoke instance) {
      return instance.bridgeC(" world! ", 1, 2, 3, 4, 5);
    }
  }
}
