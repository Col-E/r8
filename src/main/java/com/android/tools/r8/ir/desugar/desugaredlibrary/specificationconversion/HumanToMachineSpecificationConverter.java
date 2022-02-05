// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanTopLevelFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineTopLevelFlags;
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
        convertRewritingFlags(
            humanSpec.getSynthesizedLibraryClassesPackagePrefix(),
            humanSpec.getRewritingFlags(),
            appView.appInfoForDesugaring());
    MachineTopLevelFlags topLevelFlags = convertTopLevelFlags(humanSpec.getTopLevelFlags());
    return new MachineDesugaredLibrarySpecification(
        humanSpec.isLibraryCompilation(), topLevelFlags, machineRewritingFlags);
  }

  private MachineTopLevelFlags convertTopLevelFlags(HumanTopLevelFlags topLevelFlags) {
    return new MachineTopLevelFlags(
        topLevelFlags.getRequiredCompilationAPILevel(),
        topLevelFlags.getSynthesizedLibraryClassesPackagePrefix(),
        topLevelFlags.getIdentifier(),
        topLevelFlags.getJsonSource(),
        topLevelFlags.supportAllCallbacksFromLibrary(),
        topLevelFlags.getExtraKeepRules());
  }

  private MachineRewritingFlags convertRewritingFlags(
      String synthesizedPrefix,
      HumanRewritingFlags rewritingFlags,
      AppInfoWithClassHierarchy appInfo) {
    MachineRewritingFlags.Builder builder = MachineRewritingFlags.builder();
    new HumanToMachineRetargetConverter(appInfo).convertRetargetFlags(rewritingFlags, builder);
    new HumanToMachineEmulatedInterfaceConverter(appInfo)
        .convertEmulatedInterfaces(rewritingFlags, appInfo, builder);
    new HumanToMachinePrefixConverter(appInfo)
        .convertPrefixFlags(rewritingFlags, builder, synthesizedPrefix);
    new HumanToMachineWrapperConverter(appInfo).convertWrappers(rewritingFlags, builder);
    rewritingFlags
        .getCustomConversions()
        .forEach(
            (type, conversionType) ->
                builder.putCustomConversion(
                    type, conversionType, appInfo.dexItemFactory().convertMethodName));
    for (DexType type : rewritingFlags.getDontRetargetLibMember()) {
      builder.addDontRetarget(type);
    }
    rewritingFlags.getBackportCoreLibraryMember().forEach(builder::putLegacyBackport);
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
