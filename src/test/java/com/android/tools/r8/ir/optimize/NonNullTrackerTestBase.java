// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;

public abstract class NonNullTrackerTestBase extends TestBase {
  protected static final InternalOptions TEST_OPTIONS = new InternalOptions();

  protected AppInfo build(Class<?> mainClass) throws Exception {
    Timing timing = new Timing(getClass().getSimpleName());
    AndroidApp app = buildAndroidApp(ToolHelper.getClassAsBytes(mainClass));
    DexApplication dexApplication =
        new ApplicationReader(app, TEST_OPTIONS, timing).read().toDirect();
    return new AppInfo(dexApplication);
  }

}
