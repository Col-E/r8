// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppServices;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardClassFilter;
import com.android.tools.r8.shaking.ProguardKeepRule;
import com.android.tools.r8.shaking.RootSetBuilder;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.ExecutorService;

public abstract class NonNullTrackerTestBase extends TestBase {

  protected AppView<AppInfoWithLiveness> build(Class<?> mainClass) throws Exception {
    Timing timing = new Timing(getClass().getSimpleName());
    AndroidApp app = buildAndroidApp(ToolHelper.getClassAsBytes(mainClass));
    InternalOptions options = new InternalOptions();
    DexApplication dexApplication = new ApplicationReader(app, options, timing).read().toDirect();
    AppView<? extends AppInfoWithSubtyping> appView =
        AppView.createForR8(new AppInfoWithSubtyping(dexApplication), options);
    appView.setAppServices(AppServices.builder(appView).build());
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    RootSet rootSet =
        new RootSetBuilder(
                appView,
                dexApplication,
                ImmutableList.of(ProguardKeepRule.defaultKeepAllRule(unused -> {})),
                options)
            .run(executorService);
    Enqueuer enqueuer = new Enqueuer(appView, options, null);
    return AppView.createForR8(
        enqueuer.traceApplication(rootSet, ProguardClassFilter.empty(), executorService, timing),
        options);
  }
}
