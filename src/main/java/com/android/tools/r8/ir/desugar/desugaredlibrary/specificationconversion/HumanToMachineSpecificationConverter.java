// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineRewritingFlags;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

public class HumanToMachineSpecificationConverter {

  public MachineDesugaredLibrarySpecification convert(
      HumanDesugaredLibrarySpecification humanSpec, Path androidLib, InternalOptions options)
      throws IOException {
    DexApplication app = readApp(androidLib, options);
    AppView<?> appView = AppView.createForD8(AppInfo.createInitialAppInfo(app));
    MachineRewritingFlags machineRewritingFlags =
        convertRewritingFlags(humanSpec.getRewritingFlags(), appView.appInfoForDesugaring());
    return new MachineDesugaredLibrarySpecification(
        humanSpec.isLibraryCompilation(), machineRewritingFlags);
  }

  private MachineRewritingFlags convertRewritingFlags(
      HumanRewritingFlags rewritingFlags, AppInfoWithClassHierarchy appInfo) {
    MachineRewritingFlags.Builder builder = MachineRewritingFlags.builder();
    new HumanToMachineRetargetConverter(appInfo).convertRetargetFlags(rewritingFlags, builder);
    new HumanToMachineEmulatedInterfaceConverter(appInfo)
        .convertEmulatedInterfaces(rewritingFlags, appInfo, builder);
    return builder.build();
  }

  private DexApplication readApp(Path androidLib, InternalOptions options) throws IOException {
    AndroidApp androidApp = AndroidApp.builder().addProgramFile(androidLib).build();
    ApplicationReader applicationReader =
        new ApplicationReader(androidApp, options, Timing.empty());
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    return applicationReader.read(executorService).toDirect();
  }
}
