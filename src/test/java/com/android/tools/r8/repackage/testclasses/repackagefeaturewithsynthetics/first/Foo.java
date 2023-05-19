// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage.testclasses.repackagefeaturewithsynthetics.first;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.repackage.RepackageFeatureWithSyntheticsTest.TestClass;

@NeverClassInline
public class Foo {

  @NeverInline
  public Foo() {
    TestClass.run(() -> PkgProtectedMethod.getStream().println("first.Foo"));
  }
}
