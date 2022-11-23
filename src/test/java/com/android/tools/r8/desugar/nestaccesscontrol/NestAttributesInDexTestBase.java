// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.nestaccesscontrol;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.TestRuntime.DexRuntime;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public abstract class NestAttributesInDexTestBase extends TestBase {

  protected boolean isRuntimeWithNestSupport(TestRuntime runtime) {
    if (runtime.isCf()) {
      return isRuntimeWithNestSupport(runtime.asCf());
    } else {
      return isRuntimeWithNestSupport(runtime.asDex());
    }
  }

  protected boolean isRuntimeWithNestSupport(CfRuntime runtime) {
    return runtime.isNewerThanOrEqual(CfVm.JDK11);
  }

  protected boolean isRuntimeWithNestSupport(DexRuntime runtime) {
    // No Art versions have support for nest attributes yet.
    return false;
  }
}
