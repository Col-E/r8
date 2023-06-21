// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal.testclasses;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoParameterTypeStrengthening;
import com.android.tools.r8.NoVerticalClassMerging;

public class InterfacesVisibilityTestClasses {
  public static class Invoker {
    @NeverInline
    public static void invokeFoo(@NoParameterTypeStrengthening PackagePrivateInterface i) {
      i.foo();
    }
  }

  @NoVerticalClassMerging
  @NeverClassInline
  public static class ImplementingPackagePrivateInterface implements PackagePrivateInterface {
    @Override
    public void foo() {
      System.out.println("foo");
    }
  }

  @NoAccessModification
  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  interface PackagePrivateInterface {
    void foo();
  }
}
