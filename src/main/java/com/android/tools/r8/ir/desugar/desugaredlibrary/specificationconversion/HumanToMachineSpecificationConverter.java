// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.ProgramResourceProvider;
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
import java.util.List;
import java.util.concurrent.ExecutorService;

public class HumanToMachineSpecificationConverter {

  public MachineDesugaredLibrarySpecification convert(
      HumanDesugaredLibrarySpecification humanSpec,
      List<ProgramResourceProvider> desugaredJDKLib,
      List<ClassFileResourceProvider> library,
      InternalOptions options)
      throws IOException {
    assert !humanSpec.isLibraryCompilation() || desugaredJDKLib != null;
    AndroidApp.Builder builder = AndroidApp.builder();
    for (ClassFileResourceProvider classFileResourceProvider : library) {
      builder.addLibraryResourceProvider(classFileResourceProvider);
    }
    if (humanSpec.isLibraryCompilation()) {
      for (ProgramResourceProvider programResourceProvider : desugaredJDKLib) {
        builder.addProgramResourceProvider(programResourceProvider);
      }
    }
    return internalConvert(humanSpec, builder.build(), options);
  }

  public MachineDesugaredLibrarySpecification convert(
      HumanDesugaredLibrarySpecification humanSpec,
      Path desugaredJDKLib,
      Path androidLib,
      InternalOptions options)
      throws IOException {
    assert !humanSpec.isLibraryCompilation() || desugaredJDKLib != null;
    AndroidApp.Builder builder = AndroidApp.builder();
    if (humanSpec.isLibraryCompilation()) {
      builder.addProgramFile(desugaredJDKLib);
    }
    AndroidApp inputApp = builder.addLibraryFile(androidLib).build();
    return internalConvert(humanSpec, inputApp, options);
  }

  public MachineDesugaredLibrarySpecification internalConvert(
      HumanDesugaredLibrarySpecification humanSpec, AndroidApp inputApp, InternalOptions options)
      throws IOException {
    DexApplication app = readApp(inputApp, options);
    AppView<?> appView = AppView.createForD8(AppInfo.createInitialAppInfo(app));
    LibraryValidator.validate(app, humanSpec.getTopLevelFlags().getRequiredCompilationAPILevel());
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
    new HumanToMachinePrefixConverter(appInfo, builder, synthesizedPrefix)
        .convertPrefixFlags(rewritingFlags);
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

  private DexApplication readApp(AndroidApp inputApp, InternalOptions options) throws IOException {
    ApplicationReader applicationReader = new ApplicationReader(inputApp, options, Timing.empty());
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    return applicationReader.read(executorService).toDirect();
  }
}
