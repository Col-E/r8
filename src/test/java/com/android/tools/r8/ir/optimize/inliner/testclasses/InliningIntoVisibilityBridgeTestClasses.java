// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner.testclasses;

import com.android.tools.r8.ForceInline;
import com.android.tools.r8.NeverMerge;

public class InliningIntoVisibilityBridgeTestClasses {

  public static Class<?> getClassA() {
    return InliningIntoVisibilityBridgeTestClassA.class;
  }

  @NeverMerge
  static class InliningIntoVisibilityBridgeTestClassA {

    @ForceInline
    public static void method() {
      System.out.println("Hello world");
    }
  }

  @NeverMerge
  public static class InliningIntoVisibilityBridgeTestClassB
      extends InliningIntoVisibilityBridgeTestClassA {}
}
