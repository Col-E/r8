// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.contexts.CompilationContext;
import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.errors.dontwarn.DontWarnConfiguration;
import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.analysis.InitializedClassesInInstanceMethodsAnalysis.InitializedClassesInInstanceMethods;
import com.android.tools.r8.graph.classmerging.MergedClassesCollection;
import com.android.tools.r8.graph.classmerging.VerticallyMergedClasses;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.InitClassLens;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger;
import com.android.tools.r8.horizontalclassmerging.HorizontallyMergedClasses;
import com.android.tools.r8.ir.analysis.inlining.SimpleInliningConstraintFactory;
import com.android.tools.r8.ir.analysis.proto.EnumLiteProtoShrinker;
import com.android.tools.r8.ir.analysis.proto.GeneratedExtensionRegistryShrinker;
import com.android.tools.r8.ir.analysis.proto.GeneratedMessageLiteBuilderShrinker;
import com.android.tools.r8.ir.analysis.proto.GeneratedMessageLiteShrinker;
import com.android.tools.r8.ir.analysis.proto.ProtoShrinker;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.desugar.TypeRewriter;
import com.android.tools.r8.ir.optimize.enums.EnumDataMap;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoFactory;
import com.android.tools.r8.ir.optimize.library.LibraryMemberOptimizer;
import com.android.tools.r8.ir.optimize.library.LibraryMethodSideEffectModelCollection;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.naming.SeedMapper;
import com.android.tools.r8.optimize.argumentpropagation.ArgumentPropagator;
import com.android.tools.r8.optimize.interfaces.collection.OpenClosedInterfacesCollection;
import com.android.tools.r8.profile.art.ArtProfileCollection;
import com.android.tools.r8.profile.startup.profile.StartupProfile;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.AssumeInfoCollection;
import com.android.tools.r8.shaking.KeepClassInfo;
import com.android.tools.r8.shaking.KeepFieldInfo;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.shaking.LibraryModeledPredicate;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.shaking.ProguardCompatibilityActions;
import com.android.tools.r8.shaking.RootSetUtils.MainDexRootSet;
import com.android.tools.r8.shaking.RootSetUtils.RootSet;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.TestingOptions;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class AppView<T extends AppInfo> implements DexDefinitionSupplier, LibraryModeledPredicate {

  private enum WholeProgramOptimizations {
    ON,
    OFF
  }

  private T appInfo;
  private AppInfoWithClassHierarchy appInfoForDesugaring;
  private AppServices appServices;
  private ArtProfileCollection artProfileCollection;
  private AssumeInfoCollection assumeInfoCollection = AssumeInfoCollection.builder().build();
  private final DontWarnConfiguration dontWarnConfiguration;
  private final WholeProgramOptimizations wholeProgramOptimizations;
  private GraphLens codeLens = GraphLens.getIdentityLens();
  private GraphLens graphLens = GraphLens.getIdentityLens();
  private InitClassLens initClassLens;
  private GraphLens kotlinMetadataLens = GraphLens.getIdentityLens();
  private NamingLens namingLens = NamingLens.getIdentityLens();
  private ProguardCompatibilityActions proguardCompatibilityActions;
  private RootSet rootSet;
  private MainDexRootSet mainDexRootSet = null;
  private StartupProfile startupProfile;

  // This should preferably always be obtained via AppInfoWithLiveness.
  // Currently however the liveness may be downgraded thus loosing the computed keep info.
  private KeepInfoCollection keepInfo = null;

  private final AbstractValueFactory abstractValueFactory = new AbstractValueFactory();
  private final InstanceFieldInitializationInfoFactory instanceFieldInitializationInfoFactory =
      new InstanceFieldInitializationInfoFactory();
  private final SimpleInliningConstraintFactory simpleInliningConstraintFactory =
      new SimpleInliningConstraintFactory();

  // Desugaring.
  public final TypeRewriter typeRewriter;

  // Modeling.
  private final LibraryMethodSideEffectModelCollection libraryMethodSideEffectModelCollection;

  // Optimizations.
  private final ArgumentPropagator argumentPropagator;
  private final LibraryMemberOptimizer libraryMemberOptimizer;
  private final ProtoShrinker protoShrinker;

  // Optimization results.
  private boolean allCodeProcessed = false;
  private Predicate<DexType> classesEscapingIntoLibrary = Predicates.alwaysTrue();
  private InitializedClassesInInstanceMethods initializedClassesInInstanceMethods;
  private HorizontallyMergedClasses horizontallyMergedClasses = HorizontallyMergedClasses.empty();
  private VerticallyMergedClasses verticallyMergedClasses;
  private EnumDataMap unboxedEnums = null;
  private OpenClosedInterfacesCollection openClosedInterfacesCollection =
      OpenClosedInterfacesCollection.getDefault();
  // TODO(b/169115389): Remove
  private Set<DexMethod> cfByteCodePassThrough = ImmutableSet.of();
  private final Map<DexType, DexValueString> sourceDebugExtensions = new IdentityHashMap<>();
  private final Map<DexType, String> sourceFileForPrunedTypes = new IdentityHashMap<>();

  private SeedMapper applyMappingSeedMapper;

  // When input has been (partially) desugared these are the classes which has been library
  // desugared. This information is populated in the IR converter.
  private Set<DexType> alreadyLibraryDesugared = null;

  private final CompilationContext context;

  private final Thread mainThread = Thread.currentThread();

  private final AndroidApiLevelCompute apiLevelCompute;
  private final ComputedApiLevel computedMinApiLevel;

  private AppView(
      T appInfo,
      ArtProfileCollection artProfileCollection,
      StartupProfile startupProfile,
      WholeProgramOptimizations wholeProgramOptimizations,
      TypeRewriter mapper) {
    this(
        appInfo,
        artProfileCollection,
        startupProfile,
        wholeProgramOptimizations,
        mapper,
        Timing.empty());
  }

  private AppView(
      T appInfo,
      ArtProfileCollection artProfileCollection,
      StartupProfile startupProfile,
      WholeProgramOptimizations wholeProgramOptimizations,
      TypeRewriter mapper,
      Timing timing) {
    assert appInfo != null;
    this.appInfo = appInfo;
    this.context =
        timing.time(
            "Compilation context", () -> CompilationContext.createInitialContext(options()));
    this.artProfileCollection = artProfileCollection;
    this.startupProfile = startupProfile;
    this.dontWarnConfiguration =
        timing.time(
            "Dont warn config",
            () -> DontWarnConfiguration.create(options().getProguardConfiguration()));
    this.wholeProgramOptimizations = wholeProgramOptimizations;
    this.initClassLens = timing.time("Init class lens", InitClassLens::getThrowingInstance);
    this.typeRewriter = mapper;
    timing.begin("Create argument propagator");
    if (enableWholeProgramOptimizations() && options().callSiteOptimizationOptions().isEnabled()) {
      this.argumentPropagator = new ArgumentPropagator(withLiveness());
    } else {
      this.argumentPropagator = null;
    }
    timing.end();
    this.libraryMethodSideEffectModelCollection =
        timing.time("Library side-effects", () -> new LibraryMethodSideEffectModelCollection(this));
    this.libraryMemberOptimizer =
        timing.time("Library optimizer", () -> new LibraryMemberOptimizer(this, timing));
    this.protoShrinker = timing.time("Proto shrinker", () -> ProtoShrinker.create(withLiveness()));
    this.apiLevelCompute =
        timing.time("ApiLevel compute", () -> AndroidApiLevelCompute.create(this));
    this.computedMinApiLevel =
        timing.time(
            "ApiLevel computed", () -> apiLevelCompute.computeInitialMinApiLevel(options()));
  }

  public boolean verifyMainThread() {
    assert mainThread == Thread.currentThread();
    return true;
  }

  @Override
  public boolean isModeled(DexType type) {
    return libraryMemberOptimizer.isModeled(type);
  }

  private static <T extends AppInfo> TypeRewriter defaultTypeRewriter(T appInfo) {
    InternalOptions options = appInfo.options();
    return options.getTypeRewriter();
  }

  public static <T extends AppInfo> AppView<T> createForD8(T appInfo) {
    return new AppView<>(
        appInfo,
        ArtProfileCollection.createInitialArtProfileCollection(appInfo, appInfo.options()),
        StartupProfile.empty(),
        WholeProgramOptimizations.OFF,
        defaultTypeRewriter(appInfo));
  }

  public static <T extends AppInfo> AppView<T> createForSimulatingD8InR8(T appInfo) {
    return new AppView<>(
        appInfo,
        ArtProfileCollection.empty(),
        StartupProfile.empty(),
        WholeProgramOptimizations.OFF,
        defaultTypeRewriter(appInfo));
  }

  public static <T extends AppInfo> AppView<T> createForD8(
      T appInfo, TypeRewriter mapper, Timing timing) {
    return new AppView<>(
        appInfo,
        ArtProfileCollection.createInitialArtProfileCollection(appInfo, appInfo.options()),
        StartupProfile.empty(),
        WholeProgramOptimizations.OFF,
        mapper,
        timing);
  }

  public static AppView<AppInfoWithClassHierarchy> createForR8(DexApplication application) {
    return createForR8(application, MainDexInfo.none());
  }

  public static AppView<AppInfoWithClassHierarchy> createForR8(
      DexApplication application, MainDexInfo mainDexInfo) {
    ClassToFeatureSplitMap classToFeatureSplitMap =
        ClassToFeatureSplitMap.createInitialClassToFeatureSplitMap(application.options);
    AppInfoWithClassHierarchy appInfo =
        AppInfoWithClassHierarchy.createInitialAppInfoWithClassHierarchy(
            application,
            classToFeatureSplitMap,
            mainDexInfo,
            GlobalSyntheticsStrategy.forSingleOutputMode());
    return new AppView<>(
        appInfo,
        ArtProfileCollection.createInitialArtProfileCollection(appInfo, appInfo.options()),
        StartupProfile.createInitialStartupProfileForR8(application),
        WholeProgramOptimizations.ON,
        defaultTypeRewriter(appInfo));
  }

  public static <T extends AppInfo> AppView<T> createForL8(T appInfo, TypeRewriter mapper) {
    return new AppView<>(
        appInfo,
        ArtProfileCollection.createInitialArtProfileCollection(appInfo, appInfo.options()),
        StartupProfile.empty(),
        WholeProgramOptimizations.OFF,
        mapper);
  }

  public static <T extends AppInfo> AppView<T> createForRelocator(T appInfo) {
    return new AppView<>(
        appInfo,
        ArtProfileCollection.empty(),
        StartupProfile.empty(),
        WholeProgramOptimizations.OFF,
        defaultTypeRewriter(appInfo));
  }

  public static AppView<AppInfoWithClassHierarchy> createForTracer(
      AppInfoWithClassHierarchy appInfo) {
    return new AppView<>(
        appInfo,
        ArtProfileCollection.empty(),
        StartupProfile.empty(),
        WholeProgramOptimizations.ON,
        defaultTypeRewriter(appInfo));
  }

  public AbstractValueFactory abstractValueFactory() {
    return abstractValueFactory;
  }

  public InstanceFieldInitializationInfoFactory instanceFieldInitializationInfoFactory() {
    return instanceFieldInitializationInfoFactory;
  }

  public SimpleInliningConstraintFactory simpleInliningConstraintFactory() {
    return simpleInliningConstraintFactory;
  }

  public DexApplication app() {
    return appInfo().app();
  }

  public T appInfo() {
    assert !appInfo.hasClassHierarchy() || enableWholeProgramOptimizations();
    return appInfo;
  }

  public AppInfoWithClassHierarchy appInfoWithClassHierarchy() {
    return hasClassHierarchy() ? appInfo.withClassHierarchy() : null;
  }

  public AppInfoWithLiveness appInfoWithLiveness() {
    return hasLiveness() ? appInfo.withLiveness() : null;
  }

  public AppInfoWithClassHierarchy appInfoForDesugaring() {
    if (enableWholeProgramOptimizations()) {
      assert appInfo.hasClassHierarchy();
      return appInfo.withClassHierarchy();
    }
    assert !appInfo.hasClassHierarchy();
    if (appInfoForDesugaring == null) {
      appInfoForDesugaring = AppInfoWithClassHierarchy.createForDesugaring(appInfo());
    }
    return appInfoForDesugaring;
  }

  private void unsetAppInfoForDesugaring() {
    appInfoForDesugaring = null;
  }

  public <U extends T> AppView<U> setAppInfo(U appInfo) {
    assert !appInfo.isObsolete();
    AppInfo previous = this.appInfo;
    this.appInfo = appInfo;
    unsetAppInfoForDesugaring();
    if (appInfo != previous) {
      previous.markObsolete();
    }
    if (appInfo.hasLiveness()) {
      keepInfo = appInfo.withLiveness().getKeepInfo();
    }
    @SuppressWarnings("unchecked")
    AppView<U> appViewWithSpecializedAppInfo = (AppView<U>) this;
    return appViewWithSpecializedAppInfo;
  }

  public boolean isAllCodeProcessed() {
    return allCodeProcessed;
  }

  public void setAllCodeProcessed() {
    allCodeProcessed = true;
  }

  public GraphLens clearCodeRewritings() {
    GraphLens newLens = graphLens.withCodeRewritingsApplied(dexItemFactory());
    setGraphLens(newLens);
    return newLens;
  }

  public AppServices appServices() {
    return appServices;
  }

  public void setAppServices(AppServices appServices) {
    this.appServices = appServices;
  }

  public ArtProfileCollection getArtProfileCollection() {
    return artProfileCollection;
  }

  public void setArtProfileCollection(ArtProfileCollection artProfileCollection) {
    this.artProfileCollection = artProfileCollection;
  }

  public StartupProfile getStartupProfile() {
    return startupProfile;
  }

  public void setStartupProfile(StartupProfile startupProfile) {
    this.startupProfile = startupProfile;
  }

  public AssumeInfoCollection getAssumeInfoCollection() {
    return assumeInfoCollection;
  }

  public void setAssumeInfoCollection(AssumeInfoCollection assumeInfoCollection) {
    this.assumeInfoCollection = assumeInfoCollection;
  }

  public DontWarnConfiguration getDontWarnConfiguration() {
    return dontWarnConfiguration;
  }

  public boolean isClassEscapingIntoLibrary(DexType type) {
    assert type.isClassType();
    return classesEscapingIntoLibrary.test(type);
  }

  public void setClassesEscapingIntoLibrary(Predicate<DexType> classesEscapingIntoLibrary) {
    this.classesEscapingIntoLibrary = classesEscapingIntoLibrary;
  }

  public void setSourceDebugExtensionForType(DexClass clazz, DexValueString sourceDebugExtension) {
    sourceDebugExtensions.put(clazz.type, sourceDebugExtension);
  }

  public DexValueString getSourceDebugExtensionForType(DexClass clazz) {
    return sourceDebugExtensions.get(clazz.type);
  }

  @Override
  public ClassResolutionResult contextIndependentDefinitionForWithResolutionResult(DexType type) {
    return appInfo().contextIndependentDefinitionForWithResolutionResult(type);
  }

  @Override
  public final DexClass definitionFor(DexType type) {
    return appInfo().definitionFor(type);
  }

  public OptionalBool isInterface(DexType type) {
    assert type.isClassType();
    // Without whole program information we should not assume anything about any other class than
    // the current holder in a given context.
    if (enableWholeProgramOptimizations()) {
      DexClass clazz = definitionFor(type);
      if (clazz == null) {
        return OptionalBool.unknown();
      }
      return OptionalBool.of(clazz.isInterface());
    }
    return OptionalBool.unknown();
  }

  @Override
  public DexItemFactory dexItemFactory() {
    return appInfo.dexItemFactory();
  }

  public boolean enableWholeProgramOptimizations() {
    return wholeProgramOptimizations == WholeProgramOptimizations.ON;
  }

  /**
   * Create a new processor context.
   *
   * <p>The order of processor contexts for a compilation must be deterministic so this is required
   * to be called on the main thread only.
   */
  public ProcessorContext createProcessorContext() {
    assert verifyMainThread();
    return context.createProcessorContext();
  }

  public SyntheticItems getSyntheticItems() {
    return appInfo.getSyntheticItems();
  }

  public <E extends Throwable> void withArgumentPropagator(
      ThrowingConsumer<ArgumentPropagator, E> consumer) throws E {
    if (argumentPropagator != null) {
      consumer.accept(argumentPropagator);
    }
  }

  public LibraryMemberOptimizer libraryMethodOptimizer() {
    return libraryMemberOptimizer;
  }

  public LibraryMethodSideEffectModelCollection getLibraryMethodSideEffectModelCollection() {
    return libraryMethodSideEffectModelCollection;
  }

  public ProtoShrinker protoShrinker() {
    return protoShrinker;
  }

  public <E extends Throwable> void withProtoShrinker(ThrowingConsumer<ProtoShrinker, E> consumer)
      throws E {
    if (protoShrinker != null) {
      consumer.accept(protoShrinker);
    }
  }

  public <U> U withProtoShrinker(Function<ProtoShrinker, U> consumer, U defaultValue) {
    if (protoShrinker != null) {
      return consumer.apply(protoShrinker);
    }
    return defaultValue;
  }

  public <U> U withProtoEnumShrinker(Function<EnumLiteProtoShrinker, U> fn, U defaultValue) {
    if (protoShrinker != null && options().protoShrinking().isEnumLiteProtoShrinkingEnabled()) {
      return fn.apply(protoShrinker.enumLiteProtoShrinker);
    }
    return defaultValue;
  }

  public <E extends Throwable> void withGeneratedExtensionRegistryShrinker(
      ThrowingConsumer<GeneratedExtensionRegistryShrinker, E> consumer) throws E {
    if (protoShrinker != null && protoShrinker.generatedExtensionRegistryShrinker != null) {
      consumer.accept(protoShrinker.generatedExtensionRegistryShrinker);
    }
  }

  public <U> U withGeneratedExtensionRegistryShrinker(
      Function<GeneratedExtensionRegistryShrinker, U> fn, U defaultValue) {
    if (protoShrinker != null && protoShrinker.generatedExtensionRegistryShrinker != null) {
      return fn.apply(protoShrinker.generatedExtensionRegistryShrinker);
    }
    return defaultValue;
  }

  public <E extends Throwable> void withGeneratedMessageLiteShrinker(
      ThrowingConsumer<GeneratedMessageLiteShrinker, E> consumer) throws E {
    if (protoShrinker != null && protoShrinker.generatedMessageLiteShrinker != null) {
      consumer.accept(protoShrinker.generatedMessageLiteShrinker);
    }
  }

  public <E extends Throwable> void withGeneratedMessageLiteBuilderShrinker(
      ThrowingConsumer<GeneratedMessageLiteBuilderShrinker, E> consumer) throws E {
    if (protoShrinker != null && protoShrinker.generatedMessageLiteBuilderShrinker != null) {
      consumer.accept(protoShrinker.generatedMessageLiteBuilderShrinker);
    }
  }

  public <U> U withGeneratedMessageLiteShrinker(
      Function<GeneratedMessageLiteShrinker, U> fn, U defaultValue) {
    if (protoShrinker != null && protoShrinker.generatedMessageLiteShrinker != null) {
      return fn.apply(protoShrinker.generatedMessageLiteShrinker);
    }
    return defaultValue;
  }

  public <U> U withGeneratedMessageLiteBuilderShrinker(
      Function<GeneratedMessageLiteBuilderShrinker, U> fn, U defaultValue) {
    if (protoShrinker != null && protoShrinker.generatedMessageLiteBuilderShrinker != null) {
      return fn.apply(protoShrinker.generatedMessageLiteBuilderShrinker);
    }
    return defaultValue;
  }

  public GraphLens codeLens() {
    return codeLens;
  }

  public void setCodeLens(GraphLens codeLens) {
    this.codeLens = codeLens;
  }

  public GraphLens graphLens() {
    return graphLens;
  }

  /** @return true if the graph lens changed, otherwise false. */
  public boolean setGraphLens(GraphLens graphLens) {
    if (graphLens != this.graphLens) {
      this.graphLens = graphLens;

      // TODO(b/202368283): Currently, we always set an applied lens or a clear code rewriting lens
      //  when the graph lens has been fully applied to all code. Therefore, we implicitly update
      //  the code lens when these lenses are set. Now that we have an explicit code lens, the clear
      //  code rewriting lens is redundant and could be removed.
      if (graphLens.isAppliedLens() || graphLens.isClearCodeRewritingLens()) {
        setCodeLens(graphLens);
      }
      return true;
    }
    return false;
  }

  public boolean canUseInitClass() {
    return options().isShrinking() && !initClassLens.isFinal();
  }

  public InitClassLens initClassLens() {
    return initClassLens;
  }

  public boolean hasInitClassLens() {
    return initClassLens != null;
  }

  public void setInitClassLens(InitClassLens initClassLens) {
    this.initClassLens = initClassLens;
  }

  public GraphLens getKotlinMetadataLens() {
    return kotlinMetadataLens;
  }

  public void setKotlinMetadataLens(GraphLens kotlinMetadataLens) {
    this.kotlinMetadataLens = kotlinMetadataLens;
  }

  public void setInitializedClassesInInstanceMethods(
      InitializedClassesInInstanceMethods initializedClassesInInstanceMethods) {
    this.initializedClassesInInstanceMethods = initializedClassesInInstanceMethods;
  }

  public void setCfByteCodePassThrough(Set<DexMethod> cfByteCodePassThrough) {
    this.cfByteCodePassThrough = cfByteCodePassThrough;
  }

  public <U> U withInitializedClassesInInstanceMethods(
      Function<InitializedClassesInInstanceMethods, U> fn, U defaultValue) {
    if (initializedClassesInInstanceMethods != null) {
      return fn.apply(initializedClassesInInstanceMethods);
    }
    return defaultValue;
  }

  public InternalOptions options() {
    return appInfo.options();
  }

  public Reporter reporter() {
    return options().reporter;
  }

  public TestingOptions testing() {
    return options().testing;
  }

  public boolean hasRootSet() {
    return rootSet != null;
  }

  public RootSet rootSet() {
    return rootSet;
  }

  public void setRootSet(RootSet rootSet) {
    this.rootSet = rootSet;
  }

  public void setMainDexRootSet(MainDexRootSet mainDexRootSet) {
    assert mainDexRootSet != null : "Root set should never be recomputed";
    this.mainDexRootSet = mainDexRootSet;
  }

  public boolean hasMainDexRootSet() {
    return mainDexRootSet != null;
  }

  public MainDexRootSet getMainDexRootSet() {
    return mainDexRootSet;
  }

  public KeepInfoCollection getKeepInfo() {
    return keepInfo;
  }

  public KeepClassInfo getKeepInfo(DexProgramClass clazz) {
    return getKeepInfo().getClassInfo(clazz);
  }

  public KeepFieldInfo getKeepInfo(ProgramField field) {
    return getKeepInfo().getFieldInfo(field);
  }

  public KeepMethodInfo getKeepInfo(ProgramMethod method) {
    return getKeepInfo().getMethodInfo(method);
  }

  public NamingLens getNamingLens() {
    return namingLens;
  }

  public void setNamingLens(NamingLens namingLens) {
    this.namingLens = namingLens;
  }

  public boolean hasProguardCompatibilityActions() {
    return proguardCompatibilityActions != null;
  }

  public ProguardCompatibilityActions getProguardCompatibilityActions() {
    return proguardCompatibilityActions;
  }

  public void setProguardCompatibilityActions(
      ProguardCompatibilityActions proguardCompatibilityActions) {
    assert options().forceProguardCompatibility;
    this.proguardCompatibilityActions = proguardCompatibilityActions;
  }

  public MergedClassesCollection allMergedClasses() {
    MergedClassesCollection collection = new MergedClassesCollection();
    if (hasHorizontallyMergedClasses()) {
      collection.add(horizontallyMergedClasses);
    }
    if (verticallyMergedClasses != null) {
      collection.add(verticallyMergedClasses);
    }
    return collection;
  }

  public boolean hasHorizontallyMergedClasses() {
    return !horizontallyMergedClasses.isEmpty();
  }

  /**
   * Get the result of horizontal class merging. Returns null if horizontal class merging has not
   * been run.
   */
  public HorizontallyMergedClasses horizontallyMergedClasses() {
    return horizontallyMergedClasses;
  }

  public void setHorizontallyMergedClasses(
      HorizontallyMergedClasses horizontallyMergedClasses, HorizontalClassMerger.Mode mode) {
    assert !hasHorizontallyMergedClasses() || mode.isFinal();
    this.horizontallyMergedClasses = horizontallyMergedClasses().extend(horizontallyMergedClasses);
    if (mode.isFinal()) {
      testing()
          .horizontallyMergedClassesConsumer
          .accept(dexItemFactory(), horizontallyMergedClasses());
    }
  }

  public boolean hasVerticallyMergedClasses() {
    return verticallyMergedClasses != null;
  }

  /**
   * Get the result of vertical class merging. Returns null if vertical class merging has not been
   * run.
   */
  public VerticallyMergedClasses verticallyMergedClasses() {
    return verticallyMergedClasses;
  }

  public void setVerticallyMergedClasses(VerticallyMergedClasses verticallyMergedClasses) {
    assert this.verticallyMergedClasses == null;
    this.verticallyMergedClasses = verticallyMergedClasses;
    testing().verticallyMergedClassesConsumer.accept(dexItemFactory(), verticallyMergedClasses);
  }

  public OpenClosedInterfacesCollection getOpenClosedInterfacesCollection() {
    return openClosedInterfacesCollection;
  }

  public void setOpenClosedInterfacesCollection(
      OpenClosedInterfacesCollection openClosedInterfacesCollection) {
    this.openClosedInterfacesCollection = openClosedInterfacesCollection;
  }

  public boolean hasUnboxedEnums() {
    return unboxedEnums != null;
  }

  public EnumDataMap unboxedEnums() {
    return hasUnboxedEnums() ? unboxedEnums : EnumDataMap.empty();
  }

  public void setUnboxedEnums(EnumDataMap unboxedEnums) {
    assert !hasUnboxedEnums();
    this.unboxedEnums = unboxedEnums;
    testing().unboxedEnumsConsumer.accept(dexItemFactory(), unboxedEnums);
  }

  public boolean validateUnboxedEnumsHaveBeenPruned() {
    for (DexType unboxedEnum : unboxedEnums.computeAllUnboxedEnums()) {
      assert appInfo.definitionForWithoutExistenceAssert(unboxedEnum) == null
          : "Enum " + unboxedEnum + " has been unboxed but is still in the program.";
      assert appInfo().withLiveness().wasPruned(unboxedEnum)
          : "Enum " + unboxedEnum + " has been unboxed but was not pruned.";
    }
    return true;
  }

  public boolean hasClassHierarchy() {
    return appInfo().hasClassHierarchy();
  }

  @SuppressWarnings("unchecked")
  public AppView<AppInfoWithClassHierarchy> withClassHierarchy() {
    return appInfo.hasClassHierarchy()
        ? (AppView<AppInfoWithClassHierarchy>) this
        : null;
  }

  @SuppressWarnings("unchecked")
  public AppView<AppInfo> withoutClassHierarchy() {
    assert !hasClassHierarchy();
    return (AppView<AppInfo>) this;
  }

  public boolean hasLiveness() {
    return appInfo().hasLiveness();
  }

  public AppView<AppInfoWithLiveness> withLiveness() {
    @SuppressWarnings("unchecked")
    AppView<AppInfoWithLiveness> appViewWithLiveness = (AppView<AppInfoWithLiveness>) this;
    return appViewWithLiveness;
  }

  public OptionalBool isSubtype(DexType subtype, DexType supertype) {
    // Even if we can compute isSubtype by having class hierarchy we may not be allowed to ask the
    // question for all code paths in D8. Having the check for liveness ensure that we are in R8
    // territory.
    if (hasClassHierarchy()) {
      return OptionalBool.of(appInfo().withClassHierarchy().isSubtype(subtype, supertype));
    }
    if (subtype == supertype || supertype == dexItemFactory().objectType) {
      return OptionalBool.TRUE;
    }
    return OptionalBool.unknown();
  }

  public boolean isCfByteCodePassThrough(DexEncodedMethod method) {
    if (!options().isGeneratingClassFiles()) {
      return false;
    }
    if (cfByteCodePassThrough.contains(method.getReference())) {
      return true;
    }
    return options().testing.cfByteCodePassThrough != null
        && options().testing.cfByteCodePassThrough.test(method.getReference());
  }

  public boolean hasCfByteCodePassThroughMethods() {
    return !cfByteCodePassThrough.isEmpty();
  }

  public void pruneItems(PrunedItems prunedItems, ExecutorService executorService)
      throws ExecutionException {
    if (prunedItems.isEmpty()) {
      assert appInfo().app() == prunedItems.getPrunedApp();
      return;
    }
    if (appInfo.hasLiveness()) {
      AppView<AppInfoWithLiveness> self = withLiveness();
      AppInfoWithLiveness info = self.appInfo();
      self.setAppInfo(info.prunedCopyFrom(prunedItems, executorService));
      self.appInfo.setFilter(info.getFilter());
    } else if (appInfo.hasClassHierarchy()) {
      AppView<AppInfoWithClassHierarchy> self = withClassHierarchy();
      AppInfoWithClassHierarchy info = self.appInfo();
      self.setAppInfo(info.prunedCopyFrom(prunedItems, executorService));
      self.appInfo.setFilter(info.getFilter());
    } else {
      pruneAppInfo(prunedItems, this, executorService);
    }
    if (appServices() != null) {
      setAppServices(appServices().prunedCopy(prunedItems));
    }
    setArtProfileCollection(getArtProfileCollection().withoutPrunedItems(prunedItems));
    setAssumeInfoCollection(getAssumeInfoCollection().withoutPrunedItems(prunedItems));
    if (hasProguardCompatibilityActions()) {
      setProguardCompatibilityActions(
          getProguardCompatibilityActions().withoutPrunedItems(prunedItems));
    }
    if (hasRootSet()) {
      rootSet.pruneItems(prunedItems);
    }
    setStartupProfile(getStartupProfile().withoutPrunedItems(prunedItems, getSyntheticItems()));
    if (hasMainDexRootSet()) {
      setMainDexRootSet(mainDexRootSet.withoutPrunedItems(prunedItems));
    }
    setOpenClosedInterfacesCollection(
        openClosedInterfacesCollection.withoutPrunedItems(prunedItems));
  }

  @SuppressWarnings("unchecked")
  private static void pruneAppInfo(
      PrunedItems prunedItems, AppView<?> appView, ExecutorService executorService)
      throws ExecutionException {
    ((AppView<AppInfo>) appView)
        .setAppInfo(appView.appInfo().prunedCopyFrom(prunedItems, executorService));
  }

  public void rewriteWithLens(NonIdentityGraphLens lens) {
    if (lens != null) {
      rewriteWithLens(lens, appInfo().app().asDirect(), withClassHierarchy(), lens.getPrevious());
    }
  }

  public void rewriteWithLensAndApplication(
      NonIdentityGraphLens lens, DirectMappedDexApplication application) {
    rewriteWithLensAndApplication(lens, application, lens.getPrevious());
  }

  public void rewriteWithLensAndApplication(
      NonIdentityGraphLens lens, DirectMappedDexApplication application, GraphLens appliedLens) {
    assert lens != null;
    assert application != null;
    rewriteWithLens(lens, application, withClassHierarchy(), appliedLens);
  }

  private static void rewriteWithLens(
      NonIdentityGraphLens lens,
      DirectMappedDexApplication application,
      AppView<? extends AppInfoWithClassHierarchy> appView,
      GraphLens appliedLens) {
    if (lens == null) {
      return;
    }

    boolean changed = appView.setGraphLens(lens);
    assert changed;
    assert application.verifyWithLens(appView.appInfo().app().asDirect(), lens);

    // The application has already been rewritten with the given applied lens. Therefore, we
    // temporarily replace that lens with a lens that does not have any rewritings to avoid the
    // overhead of traversing the entire lens chain upon each lookup during the rewriting.
    NonIdentityGraphLens firstUnappliedLens = lens;
    while (firstUnappliedLens.getPrevious() != appliedLens) {
      GraphLens previousLens = firstUnappliedLens.getPrevious();
      assert previousLens.isNonIdentityLens();
      assert previousLens != appView.codeLens();
      firstUnappliedLens = previousLens.asNonIdentityLens();
    }

    // Insert a member rebinding lens above the first unapplied lens.
    // TODO(b/182129249): Once the member rebinding phase has been removed, the MemberRebindingLens
    //  should be removed and all uses of FieldRebindingIdentityLens should be replaced by
    //  MemberRebindingIdentityLens.
    GraphLens newMemberRebindingLens = GraphLens.getIdentityLens();
    if (!firstUnappliedLens.isMemberRebindingLens()
        && !firstUnappliedLens.isMemberRebindingIdentityLens()) {
      NonIdentityGraphLens appliedMemberRebindingLens =
          firstUnappliedLens.findPreviousUntil(
              previous ->
                  previous.isMemberRebindingLens() || previous.isMemberRebindingIdentityLens(),
              previous -> previous == appView.codeLens());
      if (appliedMemberRebindingLens != null) {
        newMemberRebindingLens =
            appliedMemberRebindingLens.isMemberRebindingLens()
                ? appliedMemberRebindingLens
                    .asMemberRebindingLens()
                    .toRewrittenFieldRebindingLens(appView, appliedLens, appliedMemberRebindingLens)
                : appliedMemberRebindingLens
                    .asMemberRebindingIdentityLens()
                    .toRewrittenMemberRebindingIdentityLens(
                        appView, appliedLens, appliedMemberRebindingLens);
      }
    }

    firstUnappliedLens.withAlternativeParentLens(
        newMemberRebindingLens,
        () -> {
          GraphLens appliedLensInModifiedLens = GraphLens.getIdentityLens();
          if (appView.hasLiveness()) {
            appView
                .withLiveness()
                .setAppInfo(appView.appInfoWithLiveness().rewrittenWithLens(application, lens));
          } else {
            assert appView.hasClassHierarchy();
            AppView<AppInfoWithClassHierarchy> appViewWithClassHierarchy =
                appView.withClassHierarchy();
            AppInfoWithClassHierarchy appInfo = appViewWithClassHierarchy.appInfo();
            MainDexInfo rewrittenMainDexInfo =
                appInfo.getMainDexInfo().rewrittenWithLens(appView.getSyntheticItems(), lens);
            appViewWithClassHierarchy.setAppInfo(
                appInfo.rebuildWithMainDexInfo(rewrittenMainDexInfo));
          }
          appView.setAppServices(appView.appServices().rewrittenWithLens(lens));
          appView.setArtProfileCollection(
              appView.getArtProfileCollection().rewrittenWithLens(appView, lens));
          appView.setAssumeInfoCollection(
              appView
                  .getAssumeInfoCollection()
                  .rewrittenWithLens(appView, lens, appliedLensInModifiedLens));
          if (appView.hasInitClassLens()) {
            appView.setInitClassLens(appView.initClassLens().rewrittenWithLens(lens));
          }
          if (appView.hasProguardCompatibilityActions()) {
            appView.setProguardCompatibilityActions(
                appView.getProguardCompatibilityActions().rewrittenWithLens(lens));
          }
          if (appView.hasMainDexRootSet()) {
            appView.setMainDexRootSet(appView.getMainDexRootSet().rewrittenWithLens(lens));
          }
          appView.setOpenClosedInterfacesCollection(
              appView.getOpenClosedInterfacesCollection().rewrittenWithLens(lens));
          if (appView.hasRootSet()) {
            appView.setRootSet(appView.rootSet().rewrittenWithLens(lens));
          }
          appView.setStartupProfile(appView.getStartupProfile().rewrittenWithLens(lens));
        });
  }

  public void rewriteWithD8Lens(NonIdentityGraphLens lens) {
    rewriteWithD8Lens(lens, withoutClassHierarchy());
  }

  private static void rewriteWithD8Lens(NonIdentityGraphLens lens, AppView<AppInfo> appView) {
    boolean changed = appView.setGraphLens(lens);
    assert changed;

    appView.setArtProfileCollection(
        appView.getArtProfileCollection().rewrittenWithLens(appView, lens));
  }

  public void setAlreadyLibraryDesugared(Set<DexType> alreadyLibraryDesugared) {
    assert this.alreadyLibraryDesugared == null;
    this.alreadyLibraryDesugared = alreadyLibraryDesugared;
  }

  public boolean isAlreadyLibraryDesugared(DexProgramClass clazz) {
    if (!options().desugarSpecificOptions().allowAllDesugaredInput) {
      return false;
    }
    assert alreadyLibraryDesugared != null;
    return alreadyLibraryDesugared.contains(clazz.getType());
  }

  public void loadApplyMappingSeedMapper() throws IOException {
    if (options().getProguardConfiguration().hasApplyMappingFile()) {
      applyMappingSeedMapper =
          SeedMapper.seedMapperFromFile(
              options().reporter, options().getProguardConfiguration().getApplyMappingFile());
    }
  }

  public SeedMapper getApplyMappingSeedMapper() {
    return applyMappingSeedMapper;
  }

  public void clearApplyMappingSeedMapper() {
    applyMappingSeedMapper = null;
  }

  public boolean checkForTesting(Supplier<Boolean> test) {
    return testing().enableTestAssertions ? test.get() : true;
  }

  public AndroidApiLevelCompute apiLevelCompute() {
    return apiLevelCompute;
  }

  public ComputedApiLevel computedMinApiLevel() {
    return computedMinApiLevel;
  }

  public void addPrunedClassSourceFile(DexType prunedType, String sourceFile) {
    sourceFileForPrunedTypes.put(prunedType, sourceFile);
  }

  public String getPrunedClassSourceFileInfo(DexType dexType) {
    return sourceFileForPrunedTypes.get(dexType);
  }
}
