// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.SubtypingInfo;
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
import java.util.function.BiConsumer;

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
    SubtypingInfo subtypingInfo = new SubtypingInfo(appInfo);
    rewritingFlags
        .getRetargetCoreLibMember()
        .forEach(
            (method, type) ->
                convertRetargetCoreLibMemberFlag(builder, method, type, appInfo, subtypingInfo));
    return builder.build();
  }

  private void convertRetargetCoreLibMemberFlag(
      MachineRewritingFlags.Builder builder,
      DexMethod method,
      DexType type,
      AppInfoWithClassHierarchy appInfo,
      SubtypingInfo subtypingInfo) {
    DexClass holder = appInfo.definitionFor(method.holder);
    DexEncodedMethod foundMethod = holder.lookupMethod(method);
    assert foundMethod != null;
    if (foundMethod.isStatic()) {
      convertStaticRetarget(builder, foundMethod, type, appInfo, subtypingInfo);
      return;
    }
    if (holder.isFinal() || foundMethod.isFinal()) {
      convertNonEmulatedVirtualRetarget(builder, foundMethod, type, appInfo, subtypingInfo);
      return;
    }
    convertEmulatedVirtualRetarget(builder, foundMethod, type, appInfo, subtypingInfo);
  }

  private void convertEmulatedVirtualRetarget(
      MachineRewritingFlags.Builder builder,
      DexEncodedMethod foundMethod,
      DexType type,
      AppInfoWithClassHierarchy appInfo,
      SubtypingInfo subtypingInfo) {
    // TODO(b/184026720): To implement.
  }

  private void convertNonEmulatedRetarget(
      DexEncodedMethod foundMethod,
      DexType type,
      AppInfoWithClassHierarchy appInfo,
      SubtypingInfo subtypingInfo,
      BiConsumer<DexMethod, DexMethod> consumer) {
    DexMethod src = foundMethod.getReference();
    DexMethod dest = src.withHolder(type, appInfo.dexItemFactory());
    consumer.accept(src, dest);
    for (DexType subtype : subtypingInfo.subtypes(foundMethod.getHolderType())) {
      DexClass subclass = appInfo.definitionFor(subtype);
      MethodResolutionResult resolutionResult = appInfo.resolveMethodOn(subclass, src);
      if (resolutionResult.isSuccessfulMemberResolutionResult()
          && resolutionResult.getResolvedMethod().getReference() == src) {
        consumer.accept(src.withHolder(subtype, appInfo.dexItemFactory()), dest);
      }
    }
  }

  private void convertNonEmulatedVirtualRetarget(
      MachineRewritingFlags.Builder builder,
      DexEncodedMethod foundMethod,
      DexType type,
      AppInfoWithClassHierarchy appInfo,
      SubtypingInfo subtypingInfo) {
    convertNonEmulatedRetarget(
        foundMethod,
        type,
        appInfo,
        subtypingInfo,
        (src, dest) ->
            builder.putNonEmulatedVirtualRetarget(
                src,
                dest.withExtraArgumentPrepended(
                    foundMethod.getHolderType(), appInfo.dexItemFactory())));
  }

  private void convertStaticRetarget(
      MachineRewritingFlags.Builder builder,
      DexEncodedMethod foundMethod,
      DexType type,
      AppInfoWithClassHierarchy appInfo,
      SubtypingInfo subtypingInfo) {
    convertNonEmulatedRetarget(
        foundMethod, type, appInfo, subtypingInfo, builder::putStaticRetarget);
  }

  private DexApplication readApp(Path androidLib, InternalOptions options) throws IOException {
    AndroidApp androidApp = AndroidApp.builder().addProgramFile(androidLib).build();
    ApplicationReader applicationReader =
        new ApplicationReader(androidApp, options, Timing.empty());
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    return applicationReader.read(executorService).toDirect();
  }
}
