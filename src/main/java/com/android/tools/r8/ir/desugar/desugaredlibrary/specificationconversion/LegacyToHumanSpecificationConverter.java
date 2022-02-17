// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanTopLevelFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.MultiAPILevelHumanDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.MultiAPILevelHumanDesugaredLibrarySpecificationFlagDeduplicator;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.MultiAPILevelHumanDesugaredLibrarySpecificationJsonExporter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyTopLevelFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.MultiAPILevelLegacyDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.MultiAPILevelLegacyDesugaredLibrarySpecificationParser;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class LegacyToHumanSpecificationConverter {

  private static final String wrapperPrefix = "__wrapper__.";
  private AndroidApiLevel legacyHackLevel = AndroidApiLevel.N_MR1;

  public void convertAllAPILevels(
      StringResource inputSpecification, Path androidLib, StringConsumer output)
      throws IOException {
    InternalOptions options = new InternalOptions();
    MultiAPILevelLegacyDesugaredLibrarySpecification legacySpec =
        new MultiAPILevelLegacyDesugaredLibrarySpecificationParser(
                options.dexItemFactory(), options.reporter)
            .parseMultiLevelConfiguration(inputSpecification);
    MultiAPILevelHumanDesugaredLibrarySpecification humanSpec =
        convertAllAPILevels(legacySpec, androidLib, options);
    MultiAPILevelHumanDesugaredLibrarySpecificationJsonExporter.export(humanSpec, output);
  }

  public MultiAPILevelHumanDesugaredLibrarySpecification convertAllAPILevels(
      MultiAPILevelLegacyDesugaredLibrarySpecification legacySpec,
      Path androidLib,
      InternalOptions options)
      throws IOException {
    Origin origin = legacySpec.getOrigin();
    AndroidApp androidApp = AndroidApp.builder().addLibraryFile(androidLib).build();
    DexApplication app = readApp(androidApp, options);
    HumanTopLevelFlags humanTopLevelFlags = convertTopLevelFlags(legacySpec.getTopLevelFlags());
    Int2ObjectArrayMap<HumanRewritingFlags> commonFlags =
        convertRewritingFlagMap(legacySpec.getCommonFlags(), app, origin);
    Int2ObjectArrayMap<HumanRewritingFlags> programFlags =
        convertRewritingFlagMap(legacySpec.getProgramFlags(), app, origin);
    Int2ObjectArrayMap<HumanRewritingFlags> libraryFlags =
        convertRewritingFlagMap(legacySpec.getLibraryFlags(), app, origin);

    legacyLibraryFlagHacks(libraryFlags, app, origin);

    MultiAPILevelHumanDesugaredLibrarySpecification humanSpec =
        new MultiAPILevelHumanDesugaredLibrarySpecification(
            origin, humanTopLevelFlags, commonFlags, libraryFlags, programFlags);
    MultiAPILevelHumanDesugaredLibrarySpecificationFlagDeduplicator.deduplicateFlags(
        humanSpec, options.reporter);
    return humanSpec;
  }

  public HumanDesugaredLibrarySpecification convert(
      LegacyDesugaredLibrarySpecification legacySpec,
      List<ClassFileResourceProvider> library,
      InternalOptions options)
      throws IOException {
    AndroidApp.Builder builder = AndroidApp.builder();
    for (ClassFileResourceProvider classFileResourceProvider : library) {
      builder.addLibraryResourceProvider(classFileResourceProvider);
    }
    return internalConvert(legacySpec, builder.build(), options);
  }

  public HumanDesugaredLibrarySpecification convert(
      LegacyDesugaredLibrarySpecification legacySpec, Path androidLib, InternalOptions options)
      throws IOException {
    AndroidApp androidApp = AndroidApp.builder().addLibraryFile(androidLib).build();
    return internalConvert(legacySpec, androidApp, options);
  }

  public HumanDesugaredLibrarySpecification internalConvert(
      LegacyDesugaredLibrarySpecification legacySpec, AndroidApp inputApp, InternalOptions options)
      throws IOException {
    DexApplication app = readApp(inputApp, options);
    LibraryValidator.validate(
        app,
        legacySpec.isLibraryCompilation(),
        legacySpec.getTopLevelFlags().getRequiredCompilationAPILevel());
    HumanTopLevelFlags humanTopLevelFlags = convertTopLevelFlags(legacySpec.getTopLevelFlags());
    // The origin is not maintained in non multi-level specifications.
    // It should not matter since the origin is used to report invalid specifications, and
    // converting non multi-level specifications should be performed only with *valid*
    // specifications in practical cases.
    Origin origin = Origin.unknown();
    HumanRewritingFlags humanRewritingFlags =
        convertRewritingFlags(legacySpec.getRewritingFlags(), app, origin);
    if (options.getMinApiLevel().isLessThanOrEqualTo(legacyHackLevel)
        && legacySpec.isLibraryCompilation()) {
      HumanRewritingFlags.Builder builder =
          humanRewritingFlags.newBuilder(app.options.reporter, origin);
      legacyLibraryFlagHacks(app.dexItemFactory(), builder);
      humanRewritingFlags = builder.build();
    }
    return new HumanDesugaredLibrarySpecification(
        humanTopLevelFlags, humanRewritingFlags, legacySpec.isLibraryCompilation());
  }

  private void legacyLibraryFlagHacks(
      Int2ObjectArrayMap<HumanRewritingFlags> libraryFlags, DexApplication app, Origin origin) {
    int level = legacyHackLevel.getLevel();
    HumanRewritingFlags humanRewritingFlags = libraryFlags.get(level);
    if (humanRewritingFlags == null) {
      return;
    }
    HumanRewritingFlags.Builder builder =
        humanRewritingFlags.newBuilder(app.options.reporter, origin);
    legacyLibraryFlagHacks(app.dexItemFactory(), builder);
    libraryFlags.put(level, builder.build());
  }

  private void legacyLibraryFlagHacks(
      DexItemFactory itemFactory, HumanRewritingFlags.Builder builder) {

    // TODO(b/177977763): This is only a workaround rewriting invokes of j.u.Arrays.deepEquals0
    // to j.u.DesugarArrays.deepEquals0.
    DexString name = itemFactory.createString("deepEquals0");
    DexProto proto =
        itemFactory.createProto(
            itemFactory.booleanType, itemFactory.objectType, itemFactory.objectType);
    DexMethod source =
        itemFactory.createMethod(itemFactory.createType(itemFactory.arraysDescriptor), proto, name);
    DexType target = itemFactory.createType("Ljava/util/DesugarArrays;");
    builder.retargetMethod(source, target);

    builder.amendLibraryMethod(
        source,
        MethodAccessFlags.fromSharedAccessFlags(
            Constants.ACC_PRIVATE | Constants.ACC_STATIC, false));

    // TODO(b/181629049): This is only a workaround rewriting invokes of
    //  j.u.TimeZone.getTimeZone taking a java.time.ZoneId.
    name = itemFactory.createString("getTimeZone");
    proto =
        itemFactory.createProto(
            itemFactory.createType("Ljava/util/TimeZone;"),
            itemFactory.createType("Ljava/time/ZoneId;"));
    source = itemFactory.createMethod(itemFactory.createType("Ljava/util/TimeZone;"), proto, name);
    target = itemFactory.createType("Ljava/util/DesugarTimeZone;");
    builder.retargetMethod(source, target);
  }

  private DexApplication readApp(AndroidApp inputApp, InternalOptions options) throws IOException {
    ApplicationReader applicationReader = new ApplicationReader(inputApp, options, Timing.empty());
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    return applicationReader.read(executorService).toDirect();
  }

  private Int2ObjectArrayMap<HumanRewritingFlags> convertRewritingFlagMap(
      Int2ObjectMap<LegacyRewritingFlags> libFlags, DexApplication app, Origin origin) {
    Int2ObjectArrayMap<HumanRewritingFlags> map = new Int2ObjectArrayMap<>();
    libFlags.forEach((key, flags) -> map.put((int) key, convertRewritingFlags(flags, app, origin)));
    return map;
  }

  private HumanRewritingFlags convertRewritingFlags(
      LegacyRewritingFlags flags, DexApplication app, Origin origin) {
    HumanRewritingFlags.Builder builder = HumanRewritingFlags.builder(app.options.reporter, origin);

    flags
        .getRewritePrefix()
        .forEach((prefix, rewritten) -> rewritePrefix(builder, prefix, rewritten));
    flags.getEmulateLibraryInterface().forEach(builder::putEmulatedInterface);
    flags.getBackportCoreLibraryMember().forEach(builder::putLegacyBackport);
    flags.getCustomConversions().forEach(builder::putCustomConversion);
    flags.getDontRetargetLibMember().forEach(builder::addDontRetargetLibMember);
    flags.getWrapperConversions().forEach(builder::addWrapperConversion);

    flags
        .getRetargetCoreLibMember()
        .forEach((name, typeMap) -> convertRetargetCoreLibMember(builder, app, name, typeMap));
    flags
        .getDontRewriteInvocation()
        .forEach(pair -> convertDontRewriteInvocation(builder, app, pair));

    return builder.build();
  }

  private void rewritePrefix(HumanRewritingFlags.Builder builder, String prefix, String rewritten) {
    // Legacy hacks: The human specification matches on class' types so we need different
    // rewritings.
    if (prefix.startsWith("j$")) {
      assert rewritten.startsWith("java");
      builder.putRewriteDerivedPrefix(rewritten, prefix, rewritten);
      return;
    }
    if (prefix.equals(wrapperPrefix)) {
      // We hard code here this applies to java.nio and java.io only.
      ImmutableMap<String, String> map =
          ImmutableMap.of("java.nio.", "j$.nio.", "java.io.", "j$.io.");
      map.forEach(
          (k, v) -> {
            builder.putRewriteDerivedPrefix(k, wrapperPrefix + k, k);
            builder.putRewriteDerivedPrefix(k, wrapperPrefix + v, v);
          });
      return;
    }
    builder.putRewritePrefix(prefix, rewritten);
  }

  private void convertDontRewriteInvocation(
      HumanRewritingFlags.Builder builder, DexApplication app, Pair<DexType, DexString> pair) {
    DexClass dexClass = app.definitionFor(pair.getFirst());
    assert dexClass != null;
    List<DexClassAndMethod> methodsWithName = findMethodsWithName(pair.getSecond(), dexClass);
    for (DexClassAndMethod dexClassAndMethod : methodsWithName) {
      builder.addDontRewriteInvocation(dexClassAndMethod.getReference());
    }
  }

  private void convertRetargetCoreLibMember(
      HumanRewritingFlags.Builder builder,
      DexApplication app,
      DexString name,
      Map<DexType, DexType> typeMap) {
    typeMap.forEach(
        (type, rewrittenType) -> {
          DexClass dexClass = app.definitionFor(type);
          assert dexClass != null;
          List<DexClassAndMethod> methodsWithName = findMethodsWithName(name, dexClass);
          for (DexClassAndMethod dexClassAndMethod : methodsWithName) {
            builder.retargetMethod(dexClassAndMethod.getReference(), rewrittenType);
          }
        });
  }

  private List<DexClassAndMethod> findMethodsWithName(DexString methodName, DexClass clazz) {
    List<DexClassAndMethod> found = new ArrayList<>();
    clazz.forEachClassMethodMatching(definition -> definition.getName() == methodName, found::add);
    assert !found.isEmpty()
        : "Should have found a method (library specifications) for "
            + clazz.toSourceString()
            + "."
            + methodName
            + ". Maybe the library used for the compilation should be newer.";
    return found;
  }

  private HumanTopLevelFlags convertTopLevelFlags(LegacyTopLevelFlags topLevelFlags) {
    return HumanTopLevelFlags.builder()
        .setDesugaredLibraryIdentifier(topLevelFlags.getIdentifier())
        .setExtraKeepRules(topLevelFlags.getExtraKeepRules())
        .setJsonSource(topLevelFlags.getJsonSource())
        .setRequiredCompilationAPILevel(topLevelFlags.getRequiredCompilationAPILevel())
        .setSupportAllCallbacksFromLibrary(topLevelFlags.supportAllCallbacksFromLibrary())
        .setSynthesizedLibraryClassesPackagePrefix(
            topLevelFlags.getSynthesizedLibraryClassesPackagePrefix())
        .build();
  }
}
