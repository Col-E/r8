// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b158429654.innerpackage;

import com.android.tools.r8.regress.b158429654.OuterAbstract;

public class InnerClass {

  public void foobar() {
    OuterAbstract.getInstance().theMethod();
  }
}
