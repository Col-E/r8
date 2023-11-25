// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.vertical.testclasses;

import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.NoVerticalClassMerging;

public class InterfaceAccessibleAfterVerticalClassMergingTestClasses {

  @NoAccessModification
  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface I {}

  public static class A implements I {}
}
