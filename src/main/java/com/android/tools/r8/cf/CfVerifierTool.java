// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppServices;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidApp.Builder;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.nio.file.Paths;

/** Tool to verify various aspects of class file inputs. */
public class CfVerifierTool {

  @SuppressWarnings("BadImport")
  public static void main(String[] args) throws IOException {
    Builder builder = AndroidApp.builder();
    for (String arg : args) {
      builder.addProgramFile(Paths.get(arg));
    }
    InternalOptions options = new InternalOptions();
    options.testing.verifyInputs = true;
    DexApplication dexApplication =
        new ApplicationReader(builder.build(), options, Timing.empty()).read();
    AppView<AppInfo> appView =
        AppView.createForD8(
            AppInfo.createInitialAppInfo(
                dexApplication, GlobalSyntheticsStrategy.forNonSynthesizing()));
    appView.setAppServices(AppServices.builder(appView).build());
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz.forEachProgramMethod(
          method ->
              method
                  .getDefinition()
                  .getCode()
                  .asCfCode()
                  .getOrComputeStackMapStatus(method, appView));
    }
  }
}
