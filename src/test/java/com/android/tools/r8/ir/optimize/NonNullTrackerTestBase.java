// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppServices;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;

public abstract class NonNullTrackerTestBase extends TestBase {

  protected AppView<?> build(Class<?> mainClass) throws Exception {
    Timing timing = Timing.empty();
    AndroidApp app = buildAndroidApp(ToolHelper.getClassAsBytes(mainClass));
    InternalOptions options = new InternalOptions();
    DirectMappedDexApplication dexApplication =
        new ApplicationReader(app, options, timing).read().toDirect();
    AppView<?> appView = AppView.createForD8(new AppInfo(dexApplication), options);
    appView.setAppServices(AppServices.builder(appView).build());
    return appView;
  }
}
