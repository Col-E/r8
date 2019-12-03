// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.testclasses;

import com.android.tools.r8.NeverMerge;

public class ForceInlineConstructorWithMultiPackageAccessesTestClasses {

  @NeverMerge
  public static class A {

    protected A() {}
  }
}
