// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.packageprivate;

import com.android.tools.r8.resolution.packageprivate.a.AbstractWidening;
import com.android.tools.r8.resolution.packageprivate.a.I;

public class NonAbstractWidening extends AbstractWidening implements I {

  @Override
  public void foo() {
    System.out.println("Method declaration will be removed");
  }
}
