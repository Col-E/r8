// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding.testclasses;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.memberrebinding.MemberRebindingConflictTest;

public class MemberRebindingConflictTestClasses {

  @NeverClassInline
  public static class C extends MemberRebindingConflictTest.B {

    @NeverInline
    public void baz() {
      super.foo();
      System.out.println("baz");
    }
  }
}
