// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bridgeremoval.hoisting.testclasses;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.bridgeremoval.hoisting.BridgeHoistingAccessibilityTest;

public class BridgeHoistingAccessibilityTestClasses {

  @NeverMerge
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
}
