// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.ApiLevelRange;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags.HumanEmulatedInterfaceDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanTopLevelFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.MultiAPILevelHumanDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.MultiAPILevelHumanDesugaredLibrarySpecificationFlagDeduplicator;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyTopLevelFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.MultiAPILevelLegacyDesugaredLibrarySpecification;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LegacyToHumanSpecificationConverter {

  private static final String WRAPPER_PREFIX = "__wrapper__.";
  private static final AndroidApiLevel LEGACY_HACK_LEVEL = AndroidApiLevel.N_MR1;
  private final Timing timing;
  private final Set<String> missingClasses = new HashSet<>();
  private final Set<String> missingMethods = new HashSet<>();

  public LegacyToHumanSpecificationConverter(Timing timing) {
    this.timing = timing;
  }

  public MultiAPILevelHumanDesugaredLibrarySpecification convertAllAPILevels(
      MultiAPILevelLegacyDesugaredLibrarySpecification legacySpec, DexApplication app) {
    timing.begin("Legacy to human all API convert");
    Origin origin = legacySpec.getOrigin();

    HumanTopLevelFlags humanTopLevelFlags = convertTopLevelFlags(legacySpec.getTopLevelFlags());
    Map<ApiLevelRange, HumanRewritingFlags> commonFlags =
        convertRewritingFlagMap(legacySpec.getCommonFlags(), app, origin);
    Map<ApiLevelRange, HumanRewritingFlags> programFlags =
        convertRewritingFlagMap(legacySpec.getProgramFlags(), app, origin);
    Map<ApiLevelRange, HumanRewritingFlags> libraryFlags =
        convertRewritingFlagMap(legacySpec.getLibraryFlags(), app, origin);

    legacyLibraryFlagHacks(humanTopLevelFlags.getIdentifier(), libraryFlags, app, origin);
    reportWarnings(app.options.reporter);

    MultiAPILevelHumanDesugaredLibrarySpecification humanSpec =
        new MultiAPILevelHumanDesugaredLibrarySpecification(
            origin, humanTopLevelFlags, commonFlags, libraryFlags, programFlags);
    MultiAPILevelHumanDesugaredLibrarySpecificationFlagDeduplicator.deduplicateFlags(
        humanSpec, app.options.reporter);
    timing.end();
    return humanSpec;
  }

  public HumanDesugaredLibrarySpecification convert(
      LegacyDesugaredLibrarySpecification legacySpec, DexApplication app) {
    timing.begin("Legacy to Human convert");
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
    if (legacySpec.isLibraryCompilation()) {
      timing.begin("Legacy hacks");
      HumanRewritingFlags.Builder builder =
          humanRewritingFlags.newBuilder(app.options.reporter, origin);
      legacyLibraryFlagHacks(
          legacySpec.getIdentifier(), app.dexItemFactory(), app.options.getMinApiLevel(), builder);
      humanRewritingFlags = builder.build();
      timing.end();
    }
    reportWarnings(app.options.reporter);
    timing.end();
    return new HumanDesugaredLibrarySpecification(
        humanTopLevelFlags, humanRewritingFlags, legacySpec.isLibraryCompilation());
  }

  private void reportWarnings(Reporter reporter) {
    String errorSdk = "This usually means that the compilation SDK is absent or too old.";
    if (!missingClasses.isEmpty()) {
      reporter.warning(
          "Cannot retarget core lib member for missing classes: "
              + missingClasses
              + ". "
              + errorSdk);
    }
    if (!missingMethods.isEmpty()) {
      reporter.warning(
          "Should have found a method (library specifications) for "
              + missingMethods
              + ". "
              + errorSdk);
    }
  }

  private void legacyLibraryFlagHacks(
      String identifier,
      Map<ApiLevelRange, HumanRewritingFlags> libraryFlags,
      DexApplication app,
      Origin origin) {
    ApiLevelRange range = new ApiLevelRange(LEGACY_HACK_LEVEL.getLevel());
    HumanRewritingFlags humanRewritingFlags = libraryFlags.get(range);
    if (humanRewritingFlags == null) {
      // Skip CHM only configuration.
      return;
    }
    HumanRewritingFlags.Builder builder =
        humanRewritingFlags.newBuilder(app.options.reporter, origin);
    legacyLibraryFlagHacks(identifier, app.dexItemFactory(), LEGACY_HACK_LEVEL, builder);
    libraryFlags.put(range, builder.build());
  }

  private void legacyLibraryFlagHacks(
      String identifier,
      DexItemFactory itemFactory,
      AndroidApiLevel apiLevel,
      HumanRewritingFlags.Builder builder) {

    if (apiLevel.isLessThanOrEqualTo(LEGACY_HACK_LEVEL)) {
      // TODO(b/177977763): This is only a workaround rewriting invokes of j.u.Arrays.deepEquals0
      // to j.u.DesugarArrays.deepEquals0.
      DexString name = itemFactory.createString("deepEquals0");
      DexProto proto =
          itemFactory.createProto(
              itemFactory.booleanType, itemFactory.objectType, itemFactory.objectType);
      DexMethod source =
          itemFactory.createMethod(
              itemFactory.createType(itemFactory.arraysDescriptor), proto, name);
      DexType target = itemFactory.createType("Ljava/util/DesugarArrays;");
      builder.retargetMethodToType(source, target);

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
      source =
          itemFactory.createMethod(itemFactory.createType("Ljava/util/TimeZone;"), proto, name);
      target = itemFactory.createType("Ljava/util/DesugarTimeZone;");
      builder.retargetMethodToType(source, target);
    }
    // Required by
    // https://github.com/google/desugar_jdk_libs/commit/485071cd09a3691549d065ba9e323d07edccf085.
    if (identifier.contains(":1.2")) {
      builder.putRewriteDerivedPrefix(
          "sun.misc.Desugar", "jdk.internal.misc.", "j$.sun.misc.Desugar");
    }
  }

  private Map<ApiLevelRange, HumanRewritingFlags> convertRewritingFlagMap(
      Int2ObjectMap<LegacyRewritingFlags> libFlags, DexApplication app, Origin origin) {
    Map<ApiLevelRange, HumanRewritingFlags> map = new HashMap<>();
    libFlags.forEach(
        (key, flags) -> map.put(new ApiLevelRange(key), convertRewritingFlags(flags, app, origin)));
    return map;
  }

  private HumanRewritingFlags convertRewritingFlags(
      LegacyRewritingFlags flags, DexApplication app, Origin origin) {
    timing.begin("Convert rewriting flags");
    HumanRewritingFlags.Builder builder = HumanRewritingFlags.builder(app.options.reporter, origin);
    flags
        .getRewritePrefix()
        .forEach((prefix, rewritten) -> rewritePrefix(builder, prefix, rewritten));
    flags
        .getEmulateLibraryInterface()
        .forEach(
            (type, rewrittenType) ->
                convertEmulatedInterface(
                    builder, app, type, rewrittenType, flags.getDontRewriteInvocation()));
    flags.getBackportCoreLibraryMember().forEach(builder::putLegacyBackport);
    flags.getCustomConversions().forEach(builder::putCustomConversion);
    flags.getDontRetargetLibMember().forEach(builder::addDontRetargetLibMember);
    flags.getWrapperConversions().forEach(builder::addWrapperConversion);
    flags
        .getRetargetCoreLibMember()
        .forEach((name, typeMap) -> convertRetargetCoreLibMember(builder, app, name, typeMap));
    HumanRewritingFlags humanFlags = builder.build();
    timing.end();
    return humanFlags;
  }

  private void convertEmulatedInterface(
      HumanRewritingFlags.Builder builder,
      DexApplication app,
      DexType type,
      DexType rewrittenType,
      List<Pair<DexType, DexString>> dontRewriteInvocation) {
    DexClass dexClass = app.definitionFor(type);
    Set<DexMethod> emulatedMethods = Sets.newIdentityHashSet();
    Set<DexMethod> dontRewrite = convertDontRewriteInvocation(builder, app, dontRewriteInvocation);
    dexClass
        .virtualMethods(m -> m.isDefaultMethod() && !dontRewrite.contains(m.getReference()))
        .forEach(m -> emulatedMethods.add(m.getReference()));
    builder.putSpecifiedEmulatedInterface(
        type, new HumanEmulatedInterfaceDescriptor(rewrittenType, emulatedMethods));
  }

  private void rewritePrefix(HumanRewritingFlags.Builder builder, String prefix, String rewritten) {
    // Legacy hacks: The human specification matches on class' types so we need different
    // rewritings.
    if (prefix.startsWith("j$")) {
      assert rewritten.startsWith("java");
      builder.putRewriteDerivedPrefix(rewritten, prefix, rewritten);
      return;
    }
    if (prefix.equals(WRAPPER_PREFIX)) {
      // We hard code here this applies to java.nio and java.io only.
      ImmutableMap<String, String> map =
          ImmutableMap.of("java.nio.", "j$.nio.", "java.io.", "j$.io.");
      map.forEach(
          (k, v) -> {
            builder.putRewriteDerivedPrefix(k, WRAPPER_PREFIX + k, k);
            builder.putRewriteDerivedPrefix(k, WRAPPER_PREFIX + v, v);
          });
      return;
    }
    builder.putRewritePrefix(prefix, rewritten);
  }

  private Set<DexMethod> convertDontRewriteInvocation(
      HumanRewritingFlags.Builder builder,
      DexApplication app,
      List<Pair<DexType, DexString>> dontRewriteInvocation) {
    Set<DexMethod> result = Sets.newIdentityHashSet();
    for (Pair<DexType, DexString> pair : dontRewriteInvocation) {
      DexClass dexClass = app.definitionFor(pair.getFirst());
      assert dexClass != null;
      List<DexClassAndMethod> methodsWithName =
          findMethodsWithName(pair.getSecond(), dexClass, builder, app);
      for (DexClassAndMethod dexClassAndMethod : methodsWithName) {
        result.add(dexClassAndMethod.getReference());
      }
    }
    return result;
  }

  @SuppressWarnings("MixedMutabilityReturnType")
  private void convertRetargetCoreLibMember(
      HumanRewritingFlags.Builder builder,
      DexApplication app,
      DexString name,
      Map<DexType, DexType> typeMap) {
    typeMap.forEach(
        (type, rewrittenType) -> {
          DexClass dexClass = app.definitionFor(type);
          if (dexClass == null) {
            assert false : "Cannot retarget core lib member for missing class " + type;
            missingClasses.add(type.toSourceString());
            return;
          }
          List<DexClassAndMethod> methodsWithName =
              findMethodsWithName(name, dexClass, builder, app);
          for (DexClassAndMethod dexClassAndMethod : methodsWithName) {
            DexEncodedMethod definition = dexClassAndMethod.getDefinition();
            if (definition.isStatic()
                || definition.isFinal()
                || dexClassAndMethod.getHolder().isFinal()) {
              builder.retargetMethodToType(dexClassAndMethod.getReference(), rewrittenType);
            } else {
              builder.retargetMethodEmulatedDispatchToType(
                  dexClassAndMethod.getReference(), rewrittenType);
            }
          }
        });
  }

  @SuppressWarnings({"MixedMutabilityReturnType", "ReferenceEquality"})
  private List<DexClassAndMethod> findMethodsWithName(
      DexString methodName,
      DexClass clazz,
      HumanRewritingFlags.Builder builder,
      DexApplication app) {
    List<DexClassAndMethod> found = new ArrayList<>();
    clazz.forEachClassMethodMatching(definition -> definition.getName() == methodName, found::add);
    if (found.isEmpty()
        && methodName.toString().equals("transferTo")
        && clazz.type.toString().equals("java.io.InputStream")) {
      // Special hack for JDK11 java.io.InputStream#transferTo which could not be specified
      // correctly.
      DexItemFactory factory = app.dexItemFactory();
      DexProto proto =
          factory.createProto(factory.longType, factory.createType("Ljava/io/OutputStream;"));
      DexMethod method = factory.createMethod(clazz.type, proto, methodName);
      MethodAccessFlags flags =
          MethodAccessFlags.fromSharedAccessFlags(Constants.ACC_PUBLIC, false);
      builder.amendLibraryMethod(method, flags);
      DexEncodedMethod build =
          DexEncodedMethod.builder().setMethod(method).setAccessFlags(flags).build();
      return ImmutableList.of(DexClassAndMethod.create(clazz, build));
    }
    if (found.isEmpty()) {
      String warning = clazz.toSourceString() + "." + methodName;
      assert false : "Should have found a method (library specifications) for " + warning;
      missingMethods.add(warning);
    }
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
