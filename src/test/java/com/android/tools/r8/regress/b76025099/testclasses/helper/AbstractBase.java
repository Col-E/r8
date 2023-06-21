// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b76025099.testclasses.helper;

import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.regress.b76025099.testclasses.Logger;

@NoAccessModification
@NoVerticalClassMerging
abstract class AbstractBase implements Logger {

  @NoAccessModification protected String name;

  @Override
  public String getName() {
    return name;
  }
}
