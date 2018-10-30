// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import com.android.tools.r8.utils.InternalOptions;
import java.util.function.Consumer;
import org.junit.Ignore;
import org.junit.Test;

public class KotlinLambdaMergingWithFailedInliningTest extends AbstractR8KotlinTestBase {
  private Consumer<InternalOptions> optionsModifier =
      o -> {
        o.enableTreeShaking = true;
        o.enableMinification = true;
        o.enableInlining = true;
        o.enableClassInlining = true;
        o.enableLambdaMerging = true;
        o.inliningInstructionAllowance = 3;
      };

  @Ignore("b/118067353")
  @Test(timeout=8000)
  public void testJStyleRunnable() throws Exception {
    final String mainClassName = "lambdas_jstyle_runnable.MainKt";
    runTest("lambdas_jstyle_runnable", mainClassName, optionsModifier, null);
  }

}
