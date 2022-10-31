// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.ApiLevelRange;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryAmender;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanTopLevelFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.MultiAPILevelHumanDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.CustomConversionDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineTopLevelFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MultiAPILevelMachineDesugaredLibrarySpecification;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HumanToMachineSpecificationConverter {

  private AppInfoWithClassHierarchy appInfo;
  private Reporter reporter;
  private final Set<DexType> missingCustomConversions = Sets.newIdentityHashSet();
  private final Timing timing;

  public HumanToMachineSpecificationConverter(Timing timing) {
    this.timing = timing;
  }

  public MultiAPILevelMachineDesugaredLibrarySpecification convertAllAPILevels(
      MultiAPILevelHumanDesugaredLibrarySpecification humanSpec, DexApplication app)
      throws IOException {
    timing.begin("Legacy to human all API convert");
    reporter = app.options.reporter;
    appInfo =
        AppInfoWithClassHierarchy.createForDesugaring(
            AppInfo.createInitialAppInfo(app, GlobalSyntheticsStrategy.forNonSynthesizing()));

    MachineTopLevelFlags machineTopLevelFlags = convertTopLevelFlags(humanSpec.getTopLevelFlags());
    String synthesizedPrefix = machineTopLevelFlags.getSynthesizedLibraryClassesPackagePrefix();
    String identifier = machineTopLevelFlags.getIdentifier();
    Map<ApiLevelRange, MachineRewritingFlags> commonFlags =
        convertRewritingFlagMap(humanSpec.getCommonFlags(), synthesizedPrefix, true, identifier);
    Map<ApiLevelRange, MachineRewritingFlags> programFlags =
        convertRewritingFlagMap(humanSpec.getProgramFlags(), synthesizedPrefix, false, identifier);
    Map<ApiLevelRange, MachineRewritingFlags> libraryFlags =
        convertRewritingFlagMap(humanSpec.getLibraryFlags(), synthesizedPrefix, true, identifier);

    MultiAPILevelMachineDesugaredLibrarySpecification machineSpec =
        new MultiAPILevelMachineDesugaredLibrarySpecification(
            humanSpec.getOrigin(), machineTopLevelFlags, commonFlags, libraryFlags, programFlags);
    timing.end();
    return machineSpec;
  }

  private Map<ApiLevelRange, MachineRewritingFlags> convertRewritingFlagMap(
      Map<ApiLevelRange, HumanRewritingFlags> libFlags,
      String synthesizedPrefix,
      boolean interpretAsLibraryCompilation,
      String identifier) {
    Map<ApiLevelRange, MachineRewritingFlags> map = new HashMap<>();
    libFlags.forEach(
        (range, flags) ->
            map.put(
                range,
                convertRewritingFlags(
                    flags, synthesizedPrefix, interpretAsLibraryCompilation, identifier)));
    return map;
  }

  public MachineDesugaredLibrarySpecification convert(
      HumanDesugaredLibrarySpecification humanSpec, DexApplication app) {
    timing.begin("Human to machine convert");
    reporter = app.options.reporter;
    appInfo =
        AppInfoWithClassHierarchy.createForDesugaring(
            AppInfo.createInitialAppInfo(app, GlobalSyntheticsStrategy.forNonSynthesizing()));
    LibraryValidator.validate(
        app,
        humanSpec.isLibraryCompilation(),
        humanSpec.getTopLevelFlags().getRequiredCompilationAPILevel());
    MachineRewritingFlags machineRewritingFlags =
        convertRewritingFlags(
            humanSpec.getRewritingFlags(),
            humanSpec.getSynthesizedLibraryClassesPackagePrefix(),
            humanSpec.isLibraryCompilation(),
            humanSpec.getIdentifier());
    MachineTopLevelFlags topLevelFlags = convertTopLevelFlags(humanSpec.getTopLevelFlags());
    timing.end();
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
      HumanRewritingFlags rewritingFlags,
      String synthesizedPrefix,
      boolean libraryCompilation,
      String identifier) {
    timing.begin("convert rewriting flags");
    MachineRewritingFlags.Builder builder = MachineRewritingFlags.builder();
    DesugaredLibraryAmender.run(
        rewritingFlags.getAmendLibraryMethod(),
        rewritingFlags.getAmendLibraryField(),
        appInfo,
        reporter,
        ComputedApiLevel.unknown());
    rewritingFlags.getAmendLibraryMethod().forEach(builder::amendLibraryMethod);
    rewritingFlags.getAmendLibraryField().forEach(builder::amendLibraryField);
    rewritingFlags.getApiGenericConversion().forEach(builder::addApiGenericTypesConversion);
    rewritingFlags.getNeverOutlineApi().forEach(builder::neverOutlineApi);
    new HumanToMachineRetargetConverter(appInfo)
        .convertRetargetFlags(rewritingFlags, builder, this::warnMissingReferences);
    new HumanToMachineEmulatedInterfaceConverter(appInfo)
        .convertEmulatedInterfaces(rewritingFlags, appInfo, builder, this::warnMissingReferences);
    new HumanToMachinePrefixConverter(
            appInfo, builder, synthesizedPrefix, libraryCompilation, identifier, rewritingFlags)
        .convertPrefixFlags(rewritingFlags, this::warnMissingDexString);
    new HumanToMachineWrapperConverter(appInfo)
        .convertWrappers(rewritingFlags, builder, this::warnMissingReferences);
    rewritingFlags
        .getCustomConversions()
        .forEach(
            (type, conversionType) ->
                convertCustomConversion(appInfo, builder, type, conversionType));
    warnMissingReferences(
        "Cannot register custom conversion due to missing type: ", missingCustomConversions);
    rewritingFlags.getDontRetarget().forEach(builder::addDontRetarget);
    rewritingFlags.getLegacyBackport().forEach(builder::putLegacyBackport);
    MachineRewritingFlags machineFlags = builder.build();
    timing.end();
    return machineFlags;
  }

  private void convertCustomConversion(
      AppInfoWithClassHierarchy appInfo,
      MachineRewritingFlags.Builder builder,
      DexType type,
      DexType conversionType) {
    DexType rewrittenType = builder.getRewrittenType(type);
    if (rewrittenType == null) {
      missingCustomConversions.add(type);
      return;
    }
    DexProto fromProto = appInfo.dexItemFactory().createProto(rewrittenType, type);
    DexMethod fromMethod =
        appInfo
            .dexItemFactory()
            .createMethod(conversionType, fromProto, appInfo.dexItemFactory().convertMethodName);
    DexProto toProto = appInfo.dexItemFactory().createProto(type, rewrittenType);
    DexMethod toMethod =
        appInfo
            .dexItemFactory()
            .createMethod(conversionType, toProto, appInfo.dexItemFactory().convertMethodName);
    builder.putCustomConversion(type, new CustomConversionDescriptor(toMethod, fromMethod));
  }

  void warnMissingReferences(String message, Set<? extends DexReference> missingReferences) {
    List<DexReference> memberList = new ArrayList<>(missingReferences);
    memberList.sort(DexReference::compareTo);
    warn(message, memberList);
  }

  void warnMissingDexString(String message, Set<DexString> missingDexString) {
    List<DexString> memberList = new ArrayList<>(missingDexString);
    memberList.sort(DexString::compareTo);
    warn(message, memberList);
  }

  private void warn(String message, List<?> memberList) {
    if (memberList.isEmpty()) {
      return;
    }
    reporter.warning("Specification conversion: " + message + memberList);
  }
}
