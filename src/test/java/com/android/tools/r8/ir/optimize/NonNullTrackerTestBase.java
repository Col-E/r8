// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public abstract class NonNullTrackerTestBase extends TestBase {

  protected AppView<AppInfoWithLiveness> build(Class<?> mainClass) throws Exception {
    return computeAppViewWithLiveness(buildAndroidApp(ToolHelper.getClassAsBytes(mainClass)));
  }
}
