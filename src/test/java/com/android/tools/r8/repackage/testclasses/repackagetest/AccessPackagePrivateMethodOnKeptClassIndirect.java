// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage.testclasses.repackagetest;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoStaticClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;

@NoStaticClassMerging
public class AccessPackagePrivateMethodOnKeptClassIndirect {

  @NeverInline
  public static void test() {
    Helper.test();
  }

  @NoVerticalClassMerging
  public static class Helper {

    @NeverInline
    static void test() {
      KeptClass.packagePrivateMethod();
    }
  }
}
