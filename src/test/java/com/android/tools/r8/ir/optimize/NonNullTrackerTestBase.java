// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;

public abstract class NonNullTrackerTestBase extends TestBase {

  protected AppView<? extends AppInfoWithClassHierarchy> build(Class<?> mainClass)
      throws Exception {
    return computeAppViewWithSubtyping(buildAndroidApp(ToolHelper.getClassAsBytes(mainClass)));
  }
}
