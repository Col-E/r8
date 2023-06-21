// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage.testclasses.repackagetest;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoHorizontalClassMerging;

@NoHorizontalClassMerging
public class AccessPackagePrivateMethodOnKeptClassIndirect {

  @NeverInline
  public static void test() {
    Helper.test();
  }

  @NoHorizontalClassMerging
  public static class Helper {

    @NeverInline
    @NoAccessModification
    static void test() {
      KeptClass.packagePrivateMethod();
    }
  }
}
