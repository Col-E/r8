// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.AndroidApiLevel.B;
import static com.android.tools.r8.utils.SystemPropertyUtils.parseSystemPropertyForDevelopmentOrDefault;

import com.android.tools.r8.AndroidResourceConsumer;
import com.android.tools.r8.AndroidResourceProvider;
import com.android.tools.r8.CancelCompilationChecker;
import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DataResourceConsumer;
import com.android.tools.r8.DesugarGraphConsumer;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.GlobalSyntheticsConsumer;
import com.android.tools.r8.MapIdProvider;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.SourceFileProvider;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.SyntheticInfoConsumer;
import com.android.tools.r8.Version;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.debuginfo.DebugRepresentation;
import com.android.tools.r8.dex.ApplicationReader.ProgramClassConflictResolver;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Backend;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.dex.MixedSectionLayoutStrategy;
import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.dump.DumpOptions;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.IncompleteNestNestDesugarDiagnosic;
import com.android.tools.r8.errors.InterfaceDesugarMissingTypeDiagnostic;
import com.android.tools.r8.errors.InvalidDebugInfoException;
import com.android.tools.r8.errors.InvalidLibrarySuperclassDiagnostic;
import com.android.tools.r8.errors.MissingNestHostNestDesugarDiagnostic;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.experimental.graphinfo.GraphConsumer;
import com.android.tools.r8.features.FeatureSplitConfiguration;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.graph.classmerging.VerticallyMergedClasses;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger;
import com.android.tools.r8.horizontalclassmerging.HorizontallyMergedClasses;
import com.android.tools.r8.horizontalclassmerging.Policy;
import com.android.tools.r8.inspector.internal.InspectorImpl;
import com.android.tools.r8.ir.analysis.proto.ProtoReferences;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.desugar.TypeRewriter;
import com.android.tools.r8.ir.desugar.TypeRewriter.MachineTypeRewriter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.nest.Nest;
import com.android.tools.r8.ir.optimize.Inliner;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.ir.optimize.enums.EnumDataMap;
import com.android.tools.r8.lightir.IR2LirConverter;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirStrategy;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.MapConsumer;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.optimize.accessmodification.AccessModifierOptions;
import com.android.tools.r8.optimize.argumentpropagation.ArgumentPropagatorEventConsumer;
import com.android.tools.r8.optimize.redundantbridgeremoval.RedundantBridgeRemovalOptions;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.profile.art.ArtProfileOptions;
import com.android.tools.r8.profile.startup.StartupOptions;
import com.android.tools.r8.profile.startup.instrumentation.StartupInstrumentationOptions;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.repackaging.Repackaging.DefaultRepackagingConfiguration;
import com.android.tools.r8.repackaging.Repackaging.RepackagingConfiguration;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.GlobalKeepInfoConfiguration;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.utils.IROrdering.IdentityIROrdering;
import com.android.tools.r8.utils.IROrdering.NondeterministicIROrdering;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.structural.Ordered;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.objectweb.asm.Opcodes;

public class InternalOptions implements GlobalKeepInfoConfiguration {

  // Set to true to run compilation in a single thread and without randomly shuffling the input.
  // This makes life easier when running R8 in a debugger.
  public static final boolean DETERMINISTIC_DEBUGGING =
      System.getProperty("com.android.tools.r8.deterministicdebugging") != null;

  // Use a MethodCollection where most interleavings between reading and mutating is caught.
  public static final boolean USE_METHOD_COLLECTION_CONCURRENCY_CHECKED = false;

  public enum LineNumberOptimization {
    OFF,
    ON;

    public boolean isOff() {
      return this == OFF;
    }

    public boolean isOn() {
      return this == ON;
    }
  }

  public enum DesugarState {
    OFF,
    ON;

    public boolean isOff() {
      return this == OFF;
    }

    public boolean isOn() {
      return this == ON;
    }
  }

  public static final CfVersion SUPPORTED_CF_VERSION = CfVersion.V21;

  public static final int SUPPORTED_DEX_VERSION =
      AndroidApiLevel.LATEST.getDexVersion().getIntValue();
  public static final int EXPERIMENTAL_DEX_VERSION = DexVersion.V41.getIntValue();

  public static final int ASM_VERSION = Opcodes.ASM9;

  public final DexItemFactory itemFactory;

  public DexItemFactory dexItemFactory() {
    return itemFactory;
  }

  // Internal state signifying that the compilation is cancelled.
  // The state can only ever transition from false to true.
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  public CancelCompilationChecker cancelCompilationChecker = null;
  public AndroidResourceProvider androidResourceProvider = null;
  public AndroidResourceConsumer androidResourceConsumer = null;

  public boolean checkIfCancelled() {
    if (cancelCompilationChecker == null) {
      assert !cancelled.get();
      return false;
    }
    if (cancelled.get()) {
      return true;
    }
    if (cancelCompilationChecker.cancel()) {
      cancelled.set(true);
      return true;
    }
    // The expected path is for no cancel to happen, thus return false here even though a cancel
    // may have happened since the above check. Either next print phase will see the change or the
    // task is done now.
    return false;
  }

  public boolean hasProguardConfiguration() {
    return proguardConfiguration != null;
  }

  public ProguardConfiguration getProguardConfiguration() {
    return proguardConfiguration;
  }

  private final ProguardConfiguration proguardConfiguration;
  public final Reporter reporter;

  // TODO(zerny): Make this private-final once we have full program-consumer support.
  public ProgramConsumer programConsumer = null;

  public ProgramClassConflictResolver programClassConflictResolver = null;

  private GlobalSyntheticsConsumer globalSyntheticsConsumer = null;
  private SyntheticInfoConsumer syntheticInfoConsumer = null;

  public DataResourceConsumer dataResourceConsumer;
  public FeatureSplitConfiguration featureSplitConfiguration;

  public List<Consumer<InspectorImpl>> outputInspections = Collections.emptyList();

  // Constructor for testing and/or other utilities.
  public InternalOptions() {
    reporter = new Reporter();
    itemFactory = new DexItemFactory();
    proguardConfiguration = null;
    enableTreeShaking = false;
    enableMinification = false;
  }

  // Constructor for D8, L8, Lint and other non-shrinkers.
  public InternalOptions(DexItemFactory factory, Reporter reporter) {
    assert reporter != null;
    assert factory != null;
    this.reporter = reporter;
    itemFactory = factory;
    proguardConfiguration = null;
    enableTreeShaking = false;
    enableMinification = false;
    disableGlobalOptimizations();
  }

  // Constructor for R8.
  public InternalOptions(
      CompilationMode mode, ProguardConfiguration proguardConfiguration, Reporter reporter) {
    assert reporter != null;
    assert proguardConfiguration != null;
    this.debug = mode == CompilationMode.DEBUG;
    this.reporter = reporter;
    this.proguardConfiguration = proguardConfiguration;
    itemFactory = proguardConfiguration.getDexItemFactory();
    enableTreeShaking = proguardConfiguration.isShrinking();
    enableMinification = proguardConfiguration.isObfuscating();
    if (!proguardConfiguration.isOptimizing()) {
      // TODO(b/171457102): Avoid the need for this.
      // -dontoptimize disables optimizations by flipping related flags.
      disableAllOptimizations();
    }
    if (debug) {
      assert !isMinifying();
      assert !isOptimizing();
      keepDebugRelatedInformation();
    }
    configurationDebugging = proguardConfiguration.isConfigurationDebugging();
    if (proguardConfiguration.isProtoShrinkingEnabled()) {
      enableProtoShrinking();
    }
  }

  private void keepDebugRelatedInformation() {
    assert !proguardConfiguration.isObfuscating();
    getProguardConfiguration().getKeepAttributes().sourceFile = true;
    getProguardConfiguration().getKeepAttributes().sourceDebugExtension = true;
    getProguardConfiguration().getKeepAttributes().lineNumberTable = true;
    getProguardConfiguration().getKeepAttributes().localVariableTable = true;
    getProguardConfiguration().getKeepAttributes().localVariableTypeTable = true;
  }

  void enableProtoShrinking() {
    enableFieldBitAccessAnalysis = true;
    protoShrinking.enableGeneratedMessageLiteShrinking = true;
    protoShrinking.enableGeneratedMessageLiteBuilderShrinking = true;
    protoShrinking.enableGeneratedExtensionRegistryShrinking = true;
    protoShrinking.enableEnumLiteProtoShrinking = true;
  }

  public InternalOptions withModifications(Consumer<InternalOptions> consumer) {
    consumer.accept(this);
    return this;
  }

  void disableAllOptimizations() {
    disableGlobalOptimizations();
    enableNameReflectionOptimization = false;
    enableStringConcatenationOptimization = false;
  }

  public void disableGlobalOptimizations() {
    inlinerOptions.enableInlining = false;
    enableClassInlining = false;
    enableDevirtualization = false;
    enableVerticalClassMerging = false;
    enableEnumUnboxing = false;
    outline.enabled = false;
    enableEnumValueOptimization = false;
    enableSideEffectAnalysis = false;
    enableTreeShakingOfLibraryMethodOverrides = false;
    enableInitializedClassesAnalysis = false;
    callSiteOptimizationOptions.disableOptimization();
    horizontalClassMergerOptions.setRestrictToSynthetics();
  }

  // Configure options according to platform build assumptions.
  // See go/r8platformflag and b/232073181.
  public void configureAndroidPlatformBuild(boolean isAndroidPlatformBuild) {
    assert !addAndroidPlatformBuildToMarker;
    if (isAndroidPlatformBuild || minApiLevel.isPlatform()) {
      apiModelingOptions().disableApiModeling();
      // TODO(b/232073181): This should also enable throwing errors on triggered backports.
      disableBackports = true;
      addAndroidPlatformBuildToMarker = isAndroidPlatformBuild;
    }
  }

  public boolean printTimes = System.getProperty("com.android.tools.r8.printtimes") != null;
  // To print memory one also have to enable printtimes.
  public boolean printMemory = System.getProperty("com.android.tools.r8.printmemory") != null;

  // Flag to toggle if DEX code objects should pass-through without IR processing.
  public boolean passthroughDexCode = false;

  public static class NeverMergeGroup<T> {
    private final List<T> prefixes;
    private final List<T> exceptionPrefixes;

    NeverMergeGroup(List<T> prefixes, List<T> exceptionPrefixes) {
      this.prefixes = prefixes;
      this.exceptionPrefixes = exceptionPrefixes;
    }

    public List<T> getPrefixes() {
      return prefixes;
    }

    public List<T> getExceptionPrefixes() {
      return exceptionPrefixes;
    }

    public <R> NeverMergeGroup<R> map(Function<T, R> fn) {
      return new NeverMergeGroup<>(
          prefixes.stream().map(fn).collect(Collectors.toList()),
          exceptionPrefixes.stream().map(fn).collect(Collectors.toList()));
    }
  }

  // Flag to toggle if the prefix based merge restriction should be enforced.
  public boolean enableNeverMergePrefixes = true;
  public NeverMergeGroup<String> neverMerge =
      new NeverMergeGroup<>(ImmutableList.of("j$."), ImmutableList.of("java."));

  public boolean classpathInterfacesMayHaveStaticInitialization = false;
  public boolean libraryInterfacesMayHaveStaticInitialization = false;

  // Optimization-related flags. These should conform to -dontoptimize and disableAllOptimizations.
  public boolean enableFieldBitAccessAnalysis =
      System.getProperty("com.android.tools.r8.fieldBitAccessAnalysis") != null;
  public boolean enableVerticalClassMerging = true;
  public boolean enableUnusedInterfaceRemoval = true;
  public boolean enableDevirtualization = true;
  public boolean enableEnumUnboxing = true;
  public boolean enableSimpleInliningConstraints = true;
  public final int simpleInliningConstraintThreshold = 0;
  public boolean enableClassInlining = true;
  public boolean enableClassStaticizer = true;
  public boolean enableInitializedClassesAnalysis = true;
  public boolean enableSideEffectAnalysis = true;
  public boolean enableDeterminismAnalysis = true;
  public boolean enableServiceLoaderRewriting = true;
  public boolean enableNameReflectionOptimization = true;
  public boolean enableStringConcatenationOptimization = true;
  public boolean enableTreeShakingOfLibraryMethodOverrides = false;
  public boolean encodeChecksums = false;
  public BiPredicate<String, Long> dexClassChecksumFilter = (name, checksum) -> true;
  public boolean forceAnnotateSynthetics = false;
  public boolean readDebugSetFileEvent = false;
  public boolean disableL8AnnotationRemoval =
      System.getProperty("com.android.tools.r8.disableL8AnnotationRemoval") != null;

  public int callGraphLikelySpuriousCallEdgeThreshold = 50;

  public int verificationSizeLimitInBytes() {
    if (testing.verificationSizeLimitInBytesOverride > -1) {
      return testing.verificationSizeLimitInBytesOverride;
    }
    // For CF we use the defined limit in the spec. For DEX we use the limit of the static verifier
    // https://android.googlesource.com/platform/art/+/android10-release/compiler/compiler.cc#48
    return isGeneratingClassFiles() ? 65534 : 16383;
  }

  public int minimumVerificationSizeLimitInBytes() {
    if (testing.verificationSizeLimitInBytesOverride > -1) {
      return testing.verificationSizeLimitInBytesOverride;
    }
    return 16383;
  }

  // We assume options will always be created on the main thread.
  public Thread mainThread = Thread.currentThread();

  public boolean enableSwitchRewriting = true;
  public boolean enableStringSwitchConversion = true;
  public int minimumStringSwitchSize = 3;
  public boolean enableEnumValueOptimization = true;
  public boolean enableEnumSwitchMapRemoval = true;
  public final OutlineOptions outline = new OutlineOptions();
  public boolean enableInitializedClassesInInstanceMethodsAnalysis = true;
  public boolean enableRedundantFieldLoadElimination = true;
  // TODO(b/138917494): Disable until we have numbers on potential performance penalties.
  public boolean enableRedundantConstNumberOptimization = false;
  public boolean enableLoopUnrolling = true;

  // TODO(b/237567012): Remove when resolved.
  public boolean enableCheckAllInstructionsDuringStackMapVerification = false;

  public String synthesizedClassPrefix = "";

  // Number of threads to use while processing the dex files.
  public int threadCount = DETERMINISTIC_DEBUGGING ? 1 : ThreadUtils.NOT_SPECIFIED;
  // Print smali disassembly.
  public boolean useSmaliSyntax = false;
  // Verbose output.
  public boolean verbose = false;
  // Silencing output.
  public boolean quiet = false;
  // Throw exception if there is a warning about invalid debug info.
  public boolean invalidDebugInfoFatal = false;
  // Don't gracefully recover from invalid debug info.
  public boolean invalidDebugInfoStrict =
      System.getProperty("com.android.tools.r8.strictdebuginfo") != null;

  public boolean ignoreJavaLibraryOverride = false;

  // When dexsplitting we ignore main dex classes missing in the application. These will be
  // fused together by play store when shipped for pre-L devices.
  public boolean ignoreMainDexMissingClasses = false;

  // Boolean value indicating that byte code pass through may be enabled.
  public boolean enableCfByteCodePassThrough = false;

  // Flag to control the representation of stateless lambdas.
  // See b/222081665 for context.
  public boolean createSingletonsForStatelessLambdas =
      System.getProperty("com.android.tools.r8.createSingletonsForStatelessLambdas") != null;

  // TODO(b/293591931): Remove this flag.
  //  Flag to allow record annotations in DEX. See b/231930852 for context.
  private final boolean emitRecordAnnotationsInDex =
      System.getProperty("com.android.tools.r8.emitRecordAnnotationsInDex") != null;

  // Flag to allow nest annotations in DEX. See b/231930852 for context.
  public boolean emitNestAnnotationsInDex =
      System.getProperty("com.android.tools.r8.emitNestAnnotationsInDex") != null;

  // TODO(b/293591931): Remove this flag.
  // Flag to allow permitted subclasses annotations in DEX. See b/231930852 for context.
  private final boolean emitPermittedSubclassesAnnotationsInDex =
      System.getProperty("com.android.tools.r8.emitPermittedSubclassesAnnotationsInDex") != null;

  private DumpInputFlags dumpInputFlags = DumpInputFlags.getDefault();

  // Contain the contents of the build properties file from the compiler command.
  public DumpOptions dumpOptions;

  public Tool tool = null;

  // Hidden marker for classes.dex
  private boolean hasMarker = false;
  private Marker marker;

  public void setMarker(Marker marker) {
    this.hasMarker = true;
    this.marker = marker;
  }

  public Marker getMarker() {
    assert tool != null;
    if (hasMarker) {
      return marker;
    }
    return createMarker(tool);
  }

  // Compute the marker to be placed in the main dex file.
  private Marker createMarker(Tool tool) {
    Marker marker =
        new Marker(tool)
            .setVersion(Version.LABEL)
            .setCompilationMode(debug ? CompilationMode.DEBUG : CompilationMode.RELEASE)
            .setBackend(isGeneratingClassFiles() ? Backend.CF : Backend.DEX)
            .setHasChecksums(encodeChecksums);
    // The marker records the min API if any desugaring happens or if the compiler generates dex
    // since the output depends on the min API in this case. There is basically no min API entry
    // in R8 cf to cf.
    if (isGeneratingDex() || desugarState == DesugarState.ON) {
      marker.setMinApi(getMinApiLevel().getLevel());
    }
    if (machineDesugaredLibrarySpecification.getIdentifier() != null) {
      marker.setDesugaredLibraryIdentifiers(machineDesugaredLibrarySpecification.getIdentifier());
    }
    if (Version.isDevelopmentVersion()) {
      marker.setSha1(VersionProperties.INSTANCE.getSha());
    }
    if (tool == Tool.R8) {
      marker.setR8Mode(forceProguardCompatibility ? "compatibility" : "full");
    }
    if (addAndroidPlatformBuildToMarker) {
      marker.setAndroidPlatformBuild();
    }
    return marker;
  }

  public void setDumpInputFlags(DumpInputFlags dumpInputFlags) {
    this.dumpInputFlags = dumpInputFlags;
  }

  public boolean hasConsumer() {
    return programConsumer != null;
  }

  public InternalOutputMode getInternalOutputMode() {
    assert hasConsumer();
    if (isGeneratingDexIndexed()) {
      return InternalOutputMode.DexIndexed;
    } else if (isGeneratingDexFilePerClassFile()) {
      return InternalOutputMode.DexFilePerClassFile;
    } else if (isGeneratingClassFiles()) {
      return InternalOutputMode.ClassFile;
    }
    throw new UnsupportedOperationException("Cannot find internal output mode.");
  }

  public boolean hasGlobalSyntheticsConsumer() {
    return globalSyntheticsConsumer != null;
  }

  public GlobalSyntheticsConsumer getGlobalSyntheticsConsumer() {
    return globalSyntheticsConsumer;
  }

  public void setGlobalSyntheticsConsumer(GlobalSyntheticsConsumer globalSyntheticsConsumer) {
    this.globalSyntheticsConsumer = globalSyntheticsConsumer;
  }

  public void setSyntheticInfoConsumer(SyntheticInfoConsumer syntheticInfoConsumer) {
    this.syntheticInfoConsumer = syntheticInfoConsumer;
  }

  public SyntheticInfoConsumer getSyntheticInfoConsumer() {
    return syntheticInfoConsumer;
  }

  public boolean isDesugaredLibraryCompilation() {
    return machineDesugaredLibrarySpecification.isLibraryCompilation();
  }

  public boolean isRelocatorCompilation() {
    return relocatorCompilation;
  }

  public boolean shouldKeepStackMapTable() {
    assert isRelocatorCompilation() || getProguardConfiguration() != null;
    return isRelocatorCompilation() || getProguardConfiguration().getKeepAttributes().stackMapTable;
  }

  public boolean shouldRerunEnqueuer() {
    return isShrinking() || isMinifying() || getProguardConfiguration().hasApplyMappingFile();
  }

  public boolean isGeneratingDex() {
    return isGeneratingDexIndexed() || isGeneratingDexFilePerClassFile();
  }

  public boolean isGeneratingDexIndexed() {
    return programConsumer instanceof DexIndexedConsumer;
  }

  public boolean isGeneratingDexFilePerClassFile() {
    return programConsumer instanceof DexFilePerClassFileConsumer;
  }

  public boolean isGeneratingClassFiles() {
    return programConsumer instanceof ClassFileConsumer;
  }

  public boolean isDesugaring() {
    return desugarState.isOn();
  }

  public boolean isCfDesugaring() {
    return isGeneratingClassFiles() && desugarState.isOn();
  }

  public DexIndexedConsumer getDexIndexedConsumer() {
    return (DexIndexedConsumer) programConsumer;
  }

  public DexFilePerClassFileConsumer getDexFilePerClassFileConsumer() {
    return (DexFilePerClassFileConsumer) programConsumer;
  }

  public ClassFileConsumer getClassFileConsumer() {
    return (ClassFileConsumer) programConsumer;
  }

  public void signalFinishedToConsumers() {
    if (programConsumer != null) {
      programConsumer.finished(reporter);
      if (dataResourceConsumer != null) {
        dataResourceConsumer.finished(reporter);
      }
    }
    if (featureSplitConfiguration != null) {
      for (FeatureSplit featureSplit : featureSplitConfiguration.getFeatureSplits()) {
        ProgramConsumer programConsumer = featureSplit.getProgramConsumer();
        programConsumer.finished(reporter);
        DataResourceConsumer dataResourceConsumer = programConsumer.getDataResourceConsumer();
        if (dataResourceConsumer != null) {
          dataResourceConsumer.finished(reporter);
        }
      }
    }
    if (desugarGraphConsumer != null) {
      desugarGraphConsumer.finished();
    }
  }

  public boolean shouldDesugarNests() {
    return !canUseNestBasedAccess();
  }

  public boolean shouldDesugarRecords() {
    return desugarState.isOn() && !canUseRecords();
  }

  public boolean canUseDesugarBufferCovariantReturnType() {
    return hasFeaturePresentFrom(AndroidApiLevel.Q);
  }

  public boolean shouldDesugarBufferCovariantReturnType() {
    return !canUseDesugarBufferCovariantReturnType();
  }

  public boolean shouldDesugarVarHandle() {
    return desugarState.isOn() && !canUseVarHandle() && enableVarHandleDesugaring;
  }

  public Set<String> extensiveLoggingFilter = getExtensiveLoggingFilter();
  public Set<String> extensiveInterfaceMethodMinifierLoggingFilter =
      getExtensiveInterfaceMethodMinifierLoggingFilter();

  public List<String> methodsFilter = ImmutableList.of();
  private AndroidApiLevel minApiLevel = AndroidApiLevel.getDefault();
  // Skipping min_api check and compiling an intermediate result intended for later merging.
  // Intermediate builds also emits or update synthesized classes mapping.
  public boolean intermediate = false;
  private boolean addAndroidPlatformBuildToMarker = false;
  public boolean retainCompileTimeAnnotations = true;
  public boolean ignoreBootClasspathEnumsForMaindexTracing =
      System.getProperty("com.android.tools.r8.ignoreBootClasspathEnumsForMaindexTracing") != null;
  public boolean pruneNonVissibleAnnotationClasses =
      System.getProperty("com.android.tools.r8.pruneNonVissibleAnnotationClasses") != null;
  public List<String> logArgumentsFilter = ImmutableList.of();

  // Flag to turn on/offLoad/store optimization in the Cf back-end.
  public boolean enableLoadStoreOptimization = true;
  // Flag to turn on/off desugaring in D8/R8.
  public DesugarState desugarState = DesugarState.ON;
  // Flag to turn on/off partial VarHandle desugaring.
  public boolean enableVarHandleDesugaring = false;
  // Flag to turn off backport methods (and report errors if triggered).
  public boolean disableBackports = false;
  public boolean disableBackportsWithErrorDiagnostics =
      System.getProperty("com.android.tools.r8.throwErrorOnBackport") != null;
  // Flag to turn on/off reduction of nest to improve class merging optimizations.
  public boolean enableNestReduction = true;
  // Defines interface method rewriter behavior.
  public OffOrAuto interfaceMethodDesugaring = OffOrAuto.Auto;
  // Defines try-with-resources rewriter behavior.
  public OffOrAuto tryWithResourcesDesugaring = OffOrAuto.Auto;
  // Flag to turn on/off processing of @dalvik.annotation.codegen.CovariantReturnType and
  // @dalvik.annotation.codegen.CovariantReturnType$CovariantReturnTypes.
  public boolean processCovariantReturnTypeAnnotations = true;
  // Flag to control library/program class lookup order.
  // TODO(120884788): Enable this flag as the default.
  public boolean lookupLibraryBeforeProgram = false;
  // TODO(120884788): Leave this system property as a stop-gap for some time.
  // public boolean lookupLibraryBeforeProgram =
  //     System.getProperty("com.android.tools.r8.lookupProgramBeforeLibrary") == null;

  public boolean enableEnqueuerDeferredTracing =
      System.getProperty("com.android.tools.r8.disableEnqueuerDeferredTracing") == null;

  public boolean loadAllClassDefinitions = false;

  // Whether or not to check for valid multi-dex builds.
  //
  // For min-api levels that did not support native multi-dex the user should provide a main dex
  // list. However, DX, didn't check that this was the case. Therefore, for CompatDX we have a flag
  // to disable the check that the build makes sense for multi-dexing.
  public boolean enableMainDexListCheck = true;

  private final boolean enableTreeShaking;
  private final boolean enableMinification;

  public AndroidApiLevel getMinApiLevel() {
    // If compiling to CF with no desugaring then we should not inspect the min-api.
    // For now we assert the API level for non-desugared CF is B, but it would be better to never
    // access the min-api in those cases.
    assert desugarState.isOn() || isGeneratingDex() || minApiLevel.equals(AndroidApiLevel.B);
    return minApiLevel;
  }

  public void setMinApiLevel(AndroidApiLevel minApiLevel) {
    assert minApiLevel != null;
    this.minApiLevel = minApiLevel;
  }

  public boolean isOptimizing() {
    return hasProguardConfiguration() && getProguardConfiguration().isOptimizing();
  }

  public boolean isRelease() {
    return !debug;
  }

  public boolean isShrinking() {
    assert proguardConfiguration == null
        || enableTreeShaking == proguardConfiguration.isShrinking();
    return enableTreeShaking;
  }

  public boolean isMinifying() {
    assert proguardConfiguration == null
        || enableMinification == proguardConfiguration.isObfuscating();
    return enableMinification;
  }

  @Override
  public boolean isAnnotationRemovalEnabled() {
    return !isForceProguardCompatibilityEnabled();
  }

  @Override
  public boolean isTreeShakingEnabled() {
    return isShrinking();
  }

  @Override
  public boolean isMinificationEnabled() {
    return isMinifying();
  }

  @Override
  public boolean isOptimizationEnabled() {
    return isOptimizing();
  }

  @Override
  public boolean isRepackagingEnabled() {
    return !debug
        && proguardConfiguration != null
        && proguardConfiguration.getPackageObfuscationMode().isSome()
        && (isMinifying() || !isForceProguardCompatibilityEnabled());
  }

  @Override
  public boolean isForceProguardCompatibilityEnabled() {
    return forceProguardCompatibility;
  }

  public boolean parseSignatureAttribute() {
    return isKeepAttributesSignatureEnabled();
  }

  @Override
  public boolean isKeepAttributesSignatureEnabled() {
    return proguardConfiguration == null || proguardConfiguration.getKeepAttributes().signature;
  }

  @Override
  public boolean isKeepEnclosingMethodAttributeEnabled() {
    return proguardConfiguration.getKeepAttributes().enclosingMethod;
  }

  @Override
  public boolean isKeepInnerClassesAttributeEnabled() {
    return proguardConfiguration.getKeepAttributes().innerClasses;
  }

  @Override
  public boolean isKeepRuntimeInvisibleAnnotationsEnabled() {
    return proguardConfiguration.getKeepAttributes().runtimeInvisibleAnnotations;
  }

  @Override
  public boolean isKeepRuntimeInvisibleParameterAnnotationsEnabled() {
    return proguardConfiguration.getKeepAttributes().runtimeInvisibleParameterAnnotations;
  }

  @Override
  public boolean isKeepRuntimeVisibleAnnotationsEnabled() {
    return proguardConfiguration.getKeepAttributes().runtimeVisibleAnnotations;
  }

  @Override
  public boolean isKeepRuntimeVisibleParameterAnnotationsEnabled() {
    return proguardConfiguration.getKeepAttributes().runtimeVisibleParameterAnnotations;
  }

  @Override
  public boolean isKeepRuntimeVisibleTypeAnnotationsEnabled() {
    return proguardConfiguration.getKeepAttributes().runtimeVisibleTypeAnnotations;
  }

  @Override
  public boolean isKeepRuntimeInvisibleTypeAnnotationsEnabled() {
    return proguardConfiguration.getKeepAttributes().runtimeInvisibleTypeAnnotations;
  }

  @Override
  public boolean isKeepPermittedSubclassesEnabled() {
    return proguardConfiguration == null
        || proguardConfiguration.getKeepAttributes().permittedSubclasses;
  }

  /**
   * If any non-static class merging is enabled, information about types referred to by instanceOf
   * and check cast instructions needs to be collected.
   */
  public boolean isClassMergingExtensionRequired(Enqueuer.Mode mode) {
    if (mode.isInitialTreeShaking()) {
      return (horizontalClassMergerOptions.isEnabled(HorizontalClassMerger.Mode.INITIAL)
              && !horizontalClassMergerOptions.isRestrictedToSynthetics())
          || enableVerticalClassMerging;
    }
    if (mode.isFinalTreeShaking()) {
      return horizontalClassMergerOptions.isEnabled(HorizontalClassMerger.Mode.FINAL)
          && !horizontalClassMergerOptions.isRestrictedToSynthetics();
    }
    assert false;
    return false;
  }

  @Override
  public boolean isAccessModificationEnabled() {
    return accessModifierOptions.isAccessModificationEnabled();
  }

  @Override
  public boolean isMethodStaticizingEnabled() {
    return callSiteOptimizationOptions().isMethodStaticizingEnabled();
  }

  public boolean keepInnerClassStructure() {
    return getProguardConfiguration().getKeepAttributes().signature
        || getProguardConfiguration().getKeepAttributes().innerClasses;
  }

  public boolean canUseInputStackMaps() {
    return testing.readInputStackMaps ? testing.readInputStackMaps : isGeneratingClassFiles();
  }

  public boolean printCfg = false;
  public String printCfgFile;
  public boolean ignoreMissingClasses = false;
  public boolean reportMissingClassesInEnclosingMethodAttribute = false;
  public boolean reportMissingClassesInInnerClassAttributes = false;
  public boolean reportMissingClassesInPermittedSubclassesAttributes = false;
  public boolean disableGenericSignatureValidation = false;
  public boolean disableInnerClassSeparatorValidationWhenRepackaging = false;

  // EXPERIMENTAL flag to get behaviour as close to Proguard as possible.
  public boolean forceProguardCompatibility = false;
  public AssertionConfigurationWithDefault assertionsConfiguration = null;
  public boolean configurationDebugging = false;

  // Don't convert Code objects to IRCode.
  public boolean skipIR = false;

  public boolean debug = false;

  private final AccessModifierOptions accessModifierOptions = new AccessModifierOptions(this);
  private final RewriteArrayOptions rewriteArrayOptions = new RewriteArrayOptions();
  private final CallSiteOptimizationOptions callSiteOptimizationOptions =
      new CallSiteOptimizationOptions();
  private final CfCodeAnalysisOptions cfCodeAnalysisOptions = new CfCodeAnalysisOptions();
  private final ClassInlinerOptions classInlinerOptions = new ClassInlinerOptions();
  private final InlinerOptions inlinerOptions = new InlinerOptions(this);
  private final HorizontalClassMergerOptions horizontalClassMergerOptions =
      new HorizontalClassMergerOptions();
  private final OpenClosedInterfacesOptions openClosedInterfacesOptions =
      new OpenClosedInterfacesOptions();
  private final ProtoShrinkingOptions protoShrinking = new ProtoShrinkingOptions();
  private final RedundantBridgeRemovalOptions redundantBridgeRemovalOptions =
      new RedundantBridgeRemovalOptions();
  private final KotlinOptimizationOptions kotlinOptimizationOptions =
      new KotlinOptimizationOptions();
  private final ApiModelTestingOptions apiModelTestingOptions = new ApiModelTestingOptions();
  private final DesugarSpecificOptions desugarSpecificOptions = new DesugarSpecificOptions();
  private final MappingComposeOptions mappingComposeOptions = new MappingComposeOptions();
  private final ArtProfileOptions artProfileOptions = new ArtProfileOptions(this);
  private final StartupOptions startupOptions = new StartupOptions();
  private final StartupInstrumentationOptions startupInstrumentationOptions =
      new StartupInstrumentationOptions();
  public final TestingOptions testing = new TestingOptions();

  public List<ProguardConfigurationRule> mainDexKeepRules = ImmutableList.of();
  public boolean minimalMainDex;
  /**
   * Enable usage of InheritanceClassInDexDistributor for multidex legacy builds. This allows
   * distribution of classes to minimize DexOpt LinearAlloc usage by minimizing linking errors
   * during DexOpt and controlling the load of classes with linking issues. This has the consequence
   * of making minimal main dex not absolutely minimal regarding runtime execution constraints
   * because it's adding classes in the main dex to satisfy also DexOpt constraints.
   */
  public boolean enableInheritanceClassInDexDistributor = true;

  public LineNumberOptimization lineNumberOptimization = LineNumberOptimization.ON;

  public RewriteArrayOptions rewriteArrayOptions() {
    return rewriteArrayOptions;
  }

  public CallSiteOptimizationOptions callSiteOptimizationOptions() {
    return callSiteOptimizationOptions;
  }

  public ClassInlinerOptions classInlinerOptions() {
    return classInlinerOptions;
  }

  public InlinerOptions inlinerOptions() {
    return inlinerOptions;
  }

  public HorizontalClassMergerOptions horizontalClassMergerOptions() {
    return horizontalClassMergerOptions;
  }

  public ProtoShrinkingOptions protoShrinking() {
    return protoShrinking;
  }

  public KotlinOptimizationOptions kotlinOptimizationOptions() {
    return kotlinOptimizationOptions;
  }

  public ApiModelTestingOptions apiModelingOptions() {
    return apiModelTestingOptions;
  }

  public MappingComposeOptions mappingComposeOptions() {
    return mappingComposeOptions;
  }

  public DesugarSpecificOptions desugarSpecificOptions() {
    return desugarSpecificOptions;
  }

  public AccessModifierOptions getAccessModifierOptions() {
    return accessModifierOptions;
  }

  public CfCodeAnalysisOptions getCfCodeAnalysisOptions() {
    return cfCodeAnalysisOptions;
  }

  public RedundantBridgeRemovalOptions getRedundantBridgeRemovalOptions() {
    return redundantBridgeRemovalOptions;
  }

  public DumpInputFlags getDumpInputFlags() {
    return dumpInputFlags;
  }

  public OpenClosedInterfacesOptions getOpenClosedInterfacesOptions() {
    return openClosedInterfacesOptions;
  }

  public ArtProfileOptions getArtProfileOptions() {
    return artProfileOptions;
  }

  public StartupOptions getStartupOptions() {
    return startupOptions;
  }

  public StartupInstrumentationOptions getStartupInstrumentationOptions() {
    return startupInstrumentationOptions;
  }

  public TestingOptions getTestingOptions() {
    return testing;
  }

  private static Set<String> getExtensiveLoggingFilter() {
    String property = System.getProperty("com.android.tools.r8.extensiveLoggingFilter");
    if (property != null) {
      ImmutableSet.Builder<String> builder = ImmutableSet.builder();
      StringUtils.splitForEach(property, ';', builder::add);
      return builder.build();
    }
    return ImmutableSet.of();
  }

  private static Set<String> getExtensiveInterfaceMethodMinifierLoggingFilter() {
    String property =
        System.getProperty("com.android.tools.r8.extensiveInterfaceMethodMinifierLoggingFilter");
    if (property != null) {
      ImmutableSet.Builder<String> builder = ImmutableSet.builder();
      StringUtils.splitForEach(property, ';', builder::add);
      return builder.build();
    }
    return ImmutableSet.of();
  }

  public static class InvalidParameterAnnotationInfo {

    final DexMethod method;
    final int expectedParameterCount;
    final int actualParameterCount;

    public InvalidParameterAnnotationInfo(
        DexMethod method, int expectedParameterCount, int actualParameterCount) {
      this.method = method;
      this.expectedParameterCount = expectedParameterCount;
      this.actualParameterCount = actualParameterCount;
    }
  }

  private static class TypeVersionPair {

    final CfVersion version;
    final DexType type;

    public TypeVersionPair(CfVersion version, DexType type) {
      this.version = version;
      this.type = type;
    }
  }

  private final Map<Origin, List<TypeVersionPair>> missingEnclosingMembers = new HashMap<>();

  private final Map<Origin, List<InvalidParameterAnnotationInfo>> warningInvalidParameterAnnotations
      = new HashMap<>();

  private final Map<Origin, List<Pair<ProgramMethod, String>>> warningInvalidDebugInfo =
      new HashMap<>();

  // Don't read code from dex files. Used to extract non-code information from vdex files where
  // the code contains unsupported byte codes.
  public boolean skipReadingDexCode = false;

  // If null, no main-dex list needs to be computed.
  // If non null it must be and passed to the consumer.
  public StringConsumer mainDexListConsumer = null;

  // If null, no proguard map needs to be computed.
  // If non null it must be and passed to the consumer.
  public MapConsumer mapConsumer = null;

  // If null, no usage information needs to be computed.
  // If non-null, it must be and is passed to the consumer.
  public StringConsumer usageInformationConsumer = null;

  public boolean hasUsageInformationConsumer() {
    return usageInformationConsumer != null;
  }

  // If null, no proguard seeds info needs to be computed.
  // If non null it must be and passed to the consumer.
  public StringConsumer proguardSeedsConsumer = null;

  // If null, no configuration information needs to be printed.
  // If non-null, configuration must be passed to the consumer.
  public StringConsumer configurationConsumer = null;

  public void resetDesugaredLibrarySpecificationForTesting() {
    loadMachineDesugaredLibrarySpecification = null;
    machineDesugaredLibrarySpecification = MachineDesugaredLibrarySpecification.empty();
  }

  public void configureDesugaredLibrary(
      DesugaredLibrarySpecification desugaredLibrarySpecification, String synthesizedClassPrefix) {
    assert synthesizedClassPrefix != null;
    assert desugaredLibrarySpecification != null;
    String prefix =
        synthesizedClassPrefix.isEmpty()
            ? System.getProperty("com.android.tools.r8.synthesizedClassPrefix", "")
            : synthesizedClassPrefix;
    String postPrefix = System.getProperty("com.android.tools.r8.desugaredLibraryPostPrefix", null);
    setDesugaredLibrarySpecification(desugaredLibrarySpecification, postPrefix);
    String post =
        postPrefix == null ? "" : DescriptorUtils.getPackageBinaryNameFromJavaType(postPrefix);
    this.synthesizedClassPrefix = prefix.isEmpty() ? "" : prefix + post;
  }

  public void setDesugaredLibrarySpecification(DesugaredLibrarySpecification specification) {
    setDesugaredLibrarySpecification(specification, null);
  }

  private void setDesugaredLibrarySpecification(
      DesugaredLibrarySpecification specification, String postPrefix) {
    if (specification.isEmpty()) {
      return;
    }
    loadMachineDesugaredLibrarySpecification =
        (timing, app) -> {
          MachineDesugaredLibrarySpecification machineSpec =
              specification.toMachineSpecification(app, timing);
          machineDesugaredLibrarySpecification =
              postPrefix != null
                  ? machineSpec.withPostPrefix(dexItemFactory(), postPrefix)
                  : machineSpec;
        };
  }

  private ThrowingBiConsumer<Timing, DexApplication, IOException>
      loadMachineDesugaredLibrarySpecification = null;

  public void loadMachineDesugaredLibrarySpecification(Timing timing, DexApplication app)
      throws IOException {
    if (loadMachineDesugaredLibrarySpecification == null) {
      return;
    }
    timing.begin("Load machine specification");
    loadMachineDesugaredLibrarySpecification.accept(timing, app);
    timing.end();
  }

  // Contains flags describing library desugaring.
  public MachineDesugaredLibrarySpecification machineDesugaredLibrarySpecification =
      MachineDesugaredLibrarySpecification.empty();

  public TypeRewriter getTypeRewriter() {
    return machineDesugaredLibrarySpecification.requiresTypeRewriting()
        ? new MachineTypeRewriter(machineDesugaredLibrarySpecification)
        : TypeRewriter.empty();
  }

  public boolean relocatorCompilation = false;

  // If null, no keep rules are recorded.
  // If non null it records desugared library APIs used by the program.
  public StringConsumer desugaredLibraryKeepRuleConsumer = null;

  // If null, no graph information needs to be provided for the keep/inclusion of classes
  // in the output. If non-null, each edge pertaining to kept parts of the resulting program
  // must be reported to the consumer.
  public GraphConsumer keptGraphConsumer = null;

  // If null, no graph information needs to be provided for the keep/inclusion of classes
  // in the main-dex output. If non-null, each edge pertaining to kept parts in the main-dex output
  // of the resulting program must be reported to the consumer.
  public GraphConsumer mainDexKeptGraphConsumer = null;

  // If null, no desugaring dependencies need to be provided. If non-null, each dependency between
  // code objects needed for correct desugaring needs to be provided to the consumer.
  public DesugarGraphConsumer desugarGraphConsumer = null;

  public Consumer<List<ProguardConfigurationRule>> syntheticProguardRulesConsumer = null;

  public MapIdProvider mapIdProvider = null;
  public SourceFileProvider sourceFileProvider = null;

  public static boolean assertionsEnabled() {
    boolean assertionsEnabled = false;
    assert assertionsEnabled = true; // Intentional side-effect.
    return assertionsEnabled;
  }

  public static void checkAssertionsEnabled() {
    if (!assertionsEnabled()) {
      throw new Unreachable();
    }
  }

  /** A set of dexitems we have reported missing to dedupe warnings. */
  private final Set<DexItem> reportedMissingForDesugaring = Sets.newConcurrentHashSet();

  private final AtomicBoolean reportedErrorReadingKotlinMetadataReflectively =
      new AtomicBoolean(false);
  private final Set<DexItem> invalidLibraryClasses = Sets.newConcurrentHashSet();

  public RuntimeException errorMissingNestHost(DexClass clazz) {
    throw reporter.fatalError(
        new MissingNestHostNestDesugarDiagnostic(
            clazz.getOrigin(), Position.UNKNOWN, messageErrorMissingNestHost(clazz)));
  }

  private static String messageErrorMissingNestHost(DexClass compiledClass) {
    String nestHostName = compiledClass.getNestHost().getName();
    return "Class "
        + compiledClass.type.getName()
        + " requires its nest host "
        + nestHostName
        + " to be on program or class path.";
  }

  public RuntimeException errorMissingNestMember(Nest nest) {
    throw reporter.fatalError(
        new IncompleteNestNestDesugarDiagnosic(
            nest.getHostClass().getOrigin(), Position.UNKNOWN, messageErrorIncompleteNest(nest)));
  }

  private static String messageErrorIncompleteNest(Nest nest) {
    List<DexProgramClass> programClassesFromNest = new ArrayList<>();
    List<DexClasspathClass> classpathClassesFromNest = new ArrayList<>();
    List<DexLibraryClass> libraryClassesFromNest = new ArrayList<>();
    nest.getHostClass()
        .accept(
            programClassesFromNest::add,
            classpathClassesFromNest::add,
            libraryClassesFromNest::add);
    for (DexClass memberClass : nest.getMembers()) {
      memberClass.accept(
          programClassesFromNest::add, classpathClassesFromNest::add, libraryClassesFromNest::add);
    }
    StringBuilder stringBuilder =
        new StringBuilder("Compilation of classes ")
            .append(StringUtils.join(", ", programClassesFromNest, DexClass::getTypeName))
            .append(" requires its nest mates ");
    if (nest.hasMissingMembers()) {
      stringBuilder
          .append(StringUtils.join(", ", nest.getMissingMembers(), DexType::getTypeName))
          .append(" (unavailable) ");
    }
    if (!libraryClassesFromNest.isEmpty()) {
      stringBuilder
          .append(StringUtils.join(", ", libraryClassesFromNest, DexClass::getTypeName))
          .append(" (on library path) ");
    }
    stringBuilder.append("to be on program or class path.");
    if (!classpathClassesFromNest.isEmpty()) {
      stringBuilder
          .append("(Classes ")
          .append(StringUtils.join(", ", classpathClassesFromNest, DexClass::getTypeName))
          .append(" from the same nest are on class path).");
    }
    return stringBuilder.toString();
  }

  public void warningMissingTypeForDesugar(
      Origin origin, Position position, DexType missingType, DexMethod context) {
    if (reportedMissingForDesugaring.add(missingType)) {
      reporter.warning(
          new InterfaceDesugarMissingTypeDiagnostic(
              origin,
              position,
              Reference.classFromDescriptor(missingType.toDescriptorString()),
              Reference.classFromDescriptor(context.holder.toDescriptorString()),
              null));
    }
  }

  public void warningMissingInterfaceForDesugar(
      DexClass classToDesugar, DexClass implementing, DexType missing) {
    if (reportedMissingForDesugaring.add(missing)) {
      reporter.warning(
          new InterfaceDesugarMissingTypeDiagnostic(
              classToDesugar.getOrigin(),
              Position.UNKNOWN,
              Reference.classFromDescriptor(missing.toDescriptorString()),
              Reference.classFromDescriptor(classToDesugar.getType().toDescriptorString()),
              classToDesugar == implementing
                  ? null
                  : Reference.classFromDescriptor(implementing.getType().toDescriptorString())));
    }
  }

  public void warningReadingKotlinMetadataReflective() {
    if (reportedErrorReadingKotlinMetadataReflectively.compareAndSet(false, true)) {
      reporter.warning(
          new StringDiagnostic(
              "Could not read the kotlin metadata message reflectively which indicates the"
                  + " compiler running in the context of a Security Manager. Not being able to"
                  + " read the kotlin metadata will have a negative effect oncode size"));
    }
  }

  public void warningInvalidLibrarySuperclassForDesugar(
      Origin origin,
      DexType libraryType,
      DexType invalidSuperType,
      String message,
      Set<DexMethod> retarget) {
    if (invalidLibraryClasses.add(invalidSuperType)) {
      reporter.warning(
          new InvalidLibrarySuperclassDiagnostic(
              origin,
              Reference.classFromDescriptor(libraryType.toDescriptorString()),
              Reference.classFromDescriptor(invalidSuperType.toDescriptorString()),
              message,
              Lists.newArrayList(
                  Iterables.transform(retarget, method -> method.asMethodReference()))));
    }
  }

  public void warningMissingEnclosingMember(DexType clazz, Origin origin, CfVersion version) {
    TypeVersionPair pair = new TypeVersionPair(version, clazz);
    synchronized (missingEnclosingMembers) {
      missingEnclosingMembers.computeIfAbsent(origin, k -> new ArrayList<>()).add(pair);
    }
  }

  public void warningInvalidParameterAnnotations(
      DexMethod method, Origin origin, int expected, int actual) {
    InvalidParameterAnnotationInfo info =
        new InvalidParameterAnnotationInfo(method, expected, actual);
    synchronized (warningInvalidParameterAnnotations) {
      warningInvalidParameterAnnotations.computeIfAbsent(origin, k -> new ArrayList<>()).add(info);
    }
  }

  public void warningInvalidDebugInfo(
      ProgramMethod method, Origin origin, InvalidDebugInfoException e) {
    if (invalidDebugInfoFatal) {
      throw new CompilationError("Fatal warning: Invalid debug info", e);
    }
    synchronized (warningInvalidDebugInfo) {
      warningInvalidDebugInfo.computeIfAbsent(
          origin, k -> new ArrayList<>()).add(new Pair<>(method, e.getMessage()));
    }
  }

  public boolean printWarnings() {
    boolean printed = false;
    boolean printOutdatedToolchain = false;
    if (warningInvalidParameterAnnotations.size() > 0) {
      // TODO(b/67626202): Add a regression test with a program that hits this issue.
      reporter.info(
          new StringDiagnostic(
              "Invalid parameter counts in MethodParameter attributes. "
                  + "This is likely due to Proguard having removed a parameter."));
      for (Origin origin : new TreeSet<>(warningInvalidParameterAnnotations.keySet())) {
        StringBuilder builder =
            new StringBuilder("Methods with invalid MethodParameter attributes:");
        for (InvalidParameterAnnotationInfo info : warningInvalidParameterAnnotations.get(origin)) {
          builder
              .append("\n  ")
              .append(info.method)
              .append(" expected count: ")
              .append(info.expectedParameterCount)
              .append(" actual count: ")
              .append(info.actualParameterCount);
        }
        reporter.info(new StringDiagnostic(builder.toString(), origin));
      }
      printed = true;
    }
    if (warningInvalidDebugInfo.size() > 0) {
      int count = 0;
      for (List<Pair<ProgramMethod, String>> methods : warningInvalidDebugInfo.values()) {
        count += methods.size();
      }
      reporter.info(
          new StringDiagnostic(
              "Stripped invalid locals information from "
                  + count
                  + (count == 1 ? " method." : " methods.")));
      for (Origin origin : new TreeSet<>(warningInvalidDebugInfo.keySet())) {
        StringBuilder builder = new StringBuilder("Methods with invalid locals information:");
        for (Pair<ProgramMethod, String> method : warningInvalidDebugInfo.get(origin)) {
          builder.append("\n  ").append(method.getFirst().toSourceString());
          builder.append("\n  ").append(method.getSecond());
        }
        reporter.info(new StringDiagnostic(builder.toString(), origin));
      }
      printed = true;
      printOutdatedToolchain = true;
    }
    if (missingEnclosingMembers.size() > 0) {
      reporter.info(
          new StringDiagnostic(
              "InnerClasses attribute has entries missing a corresponding "
                  + "EnclosingMethod attribute. "
                  + "Such InnerClasses attribute entries are ignored."));
      for (Origin origin : new TreeSet<>(missingEnclosingMembers.keySet())) {
        StringBuilder builder = new StringBuilder("Classes with missing EnclosingMethod: ");
        boolean first = true;
        for (TypeVersionPair pair : missingEnclosingMembers.get(origin)) {
          if (first) {
            first = false;
          } else {
            builder.append(", ");
          }
          builder.append(pair.type);
          printOutdatedToolchain |= pair.version.isLessThan(CfVersion.V1_5);
        }
        reporter.info(new StringDiagnostic(builder.toString(), origin));
      }
      printed = true;
    }
    if (printOutdatedToolchain) {
      reporter.info(
          new StringDiagnostic(
              "Some warnings are typically a sign of using an outdated Java toolchain."
                  + " To fix, recompile the source with an updated toolchain."));
    }
    return printed;
  }

  public boolean hasMethodsFilter() {
    return methodsFilter.size() > 0;
  }

  public boolean methodMatchesFilter(DexEncodedMethod method) {
    // Not specifying a filter matches all methods.
    if (!hasMethodsFilter()) {
      return true;
    }
    // Currently the filter is simple string equality on the qualified name.
    String qualifiedName = method.qualifiedName();
    return methodsFilter.contains(qualifiedName);
  }

  public boolean methodMatchesLogArgumentsFilter(DexEncodedMethod method) {
    // Not specifying a filter matches no methods.
    if (logArgumentsFilter.size() == 0) {
      return false;
    }
    // Currently the filter is simple string equality on the qualified name.
    String qualifiedName = method.qualifiedName();
    return logArgumentsFilter.contains(qualifiedName);
  }

  public enum PackageObfuscationMode {
    // No package obfuscation.
    NONE,
    // Strategy based on ordinary package obfuscation when no package-obfuscation mode is specified
    // by the users. In practice this falls back to FLATTEN but with keeping package-names.
    MINIFICATION,
    // Repackaging all classes into the single user-given (or top-level) package.
    REPACKAGE,
    // Repackaging all packages into the single user-given (or top-level) package.
    FLATTEN;

    public boolean isNone() {
      return this == NONE;
    }

    public boolean isFlattenPackageHierarchy() {
      return this == FLATTEN;
    }

    public boolean isRepackageClasses() {
      return this == REPACKAGE;
    }

    public boolean isMinification() {
      return this == MINIFICATION;
    }

    public boolean isSome() {
      return !isNone();
    }
  }

  public static class OutlineOptions {
    public boolean enabled = true;
    public int minSize = 3;
    public int maxSize = 99;
    public int threshold = 20;
    public int maxNumberOfInstructionsToBeConsidered = 100;
  }

  public static class KotlinOptimizationOptions {
    public boolean disableKotlinSpecificOptimizations =
        System.getProperty("com.android.tools.r8.disableKotlinSpecificOptimizations") != null;
  }

  // Temporary desugar specific options to make progress on b/147485959
  // All options should be including bugs to either fix the underlying issue or extend the api.
  public static class DesugarSpecificOptions {
    // b/172508621
    public boolean sortMethodsOnCfOutput =
        System.getProperty("com.android.tools.r8.sortMethodsOnCfWriting") != null;
    // Desugaring is not fully idempotent. With this option turned on all desugared input is
    // allowed, and if it is detected that the desugared input cannot be reprocessed, that input
    // will be passed-through without the problematic rewritings applied.
    public boolean allowAllDesugaredInput =
        System.getProperty("com.android.tools.r8.allowAllDesugaredInput") != null;
    // See b/191469661 for why this is here.
    public boolean noCfMarkerForDesugaredCode =
        System.getProperty("com.android.tools.r8.noCfMarkerForDesugaredCode") != null;
    // See b/182065081 for why this is here.
    public boolean lambdaClassFieldsFinal =
        System.getProperty("com.android.tools.r8.lambdaClassFieldsNotFinal") == null;
  }

  public class RewriteArrayOptions {
    // Arbitrary limit of number of inputs to new-filled-array/range.
    // The technical limit is 255 (Constants.U8BIT_MAX).
    public int minSizeForFilledNewArray = 1;
    public int maxSizeForFilledNewArrayOfReferences = 200;
    public int maxSizeForFilledNewArrayOfInts = 5;

    // Arbitrary limits of number of inputs to fill-array-data.
    public int minSizeForFilledArrayData = 2;
    public int maxSizeForFilledArrayData = 8 * 1024;

    // Check the most relaxed size range allowed.
    public boolean isPotentialSize(int size) {
      return minSizeForFilledNewArray <= size && size <= maxSizeForFilledArrayData;
    }

    // Dalvik x86-atom backend had a bug that made it crash on filled-new-array instructions for
    // arrays of objects. This is unfortunate, since this never hits arm devices, but we have
    // to disallow filled-new-array of objects for dalvik until kitkat. The buggy code was
    // removed during the jelly-bean release cycle and is not there from kitkat.
    //
    // Buggy code that accidentally call code that only works on primitives arrays.
    //
    // https://android.googlesource.com/platform/dalvik/+/ics-mr0/vm/mterp/out/InterpAsm-x86-atom.S#25106
    public boolean canUseFilledNewArrayOfStrings() {
      assert isGeneratingDex();
      return hasFeaturePresentFrom(AndroidApiLevel.K);
    }

    // When adding support for emitting new-filled-array for non-String types, ART 6.0.1 had issues.
    // https://ci.chromium.org/ui/p/r8/builders/ci/linux-android-6.0.1/6507/overview
    // It somehow had a new-array-filled return null.
    public boolean canUseFilledNewArrayOfNonStringObjects() {
      assert isGeneratingDex();
      return hasFeaturePresentFrom(AndroidApiLevel.N);
    }

    // When adding support for emitting filled-new-array for sub-types, ART 13 (Api-level 33) had
    // issues. See b/283715197.
    public boolean canUseSubTypesInFilledNewArray() {
      assert isGeneratingDex();
      return !canHaveBugPresentUntil(AndroidApiLevel.U);
    }

    // Dalvik doesn't handle new-filled-array with arrays as values. It fails with:
    // W(629880) VFY: [Ljava/lang/Integer; is not instance of Ljava/lang/Integer;  (dalvikvm)
    public boolean canUseFilledNewArrayOfArrays() {
      assert isGeneratingDex();
      return hasFeaturePresentFrom(AndroidApiLevel.L);
    }
  }

  public class CallSiteOptimizationOptions {

    private boolean enabled = true;
    private boolean enableMethodStaticizing = true;

    private boolean forceSyntheticsForInstanceInitializers = false;

    public void disableOptimization() {
      enabled = false;
    }

    public int getMaxNumberOfInParameters() {
      return 10;
    }

    public boolean isEnabled() {
      if (!isOptimizing() || !isShrinking()) {
        return false;
      }
      return enabled;
    }

    public boolean isForceSyntheticsForInstanceInitializersEnabled() {
      return forceSyntheticsForInstanceInitializers;
    }

    public boolean isMethodStaticizingEnabled() {
      return enableMethodStaticizing;
    }

    public CallSiteOptimizationOptions setEnabled(boolean enabled) {
      if (enabled) {
        assert isEnabled();
      } else {
        disableOptimization();
      }
      return this;
    }

    public CallSiteOptimizationOptions setForceSyntheticsForInstanceInitializers(
        boolean forceSyntheticsForInstanceInitializers) {
      this.forceSyntheticsForInstanceInitializers = forceSyntheticsForInstanceInitializers;
      return this;
    }

    public CallSiteOptimizationOptions setEnableMethodStaticizing(boolean enableMethodStaticizing) {
      this.enableMethodStaticizing = enableMethodStaticizing;
      return this;
    }
  }

  public static class CfCodeAnalysisOptions {

    private boolean allowUnreachableCfBlocks = true;
    private boolean enableUnverifiableCodeReporting = false;

    public boolean isUnverifiableCodeReportingEnabled() {
      return enableUnverifiableCodeReporting;
    }

    public boolean isUnreachableCfBlocksAllowed() {
      return allowUnreachableCfBlocks;
    }

    public CfCodeAnalysisOptions setAllowUnreachableCfBlocks(boolean allowUnreachableCfBlocks) {
      this.allowUnreachableCfBlocks = allowUnreachableCfBlocks;
      return this;
    }

    public CfCodeAnalysisOptions setEnableUnverifiableCodeReporting(
        boolean enableUnverifiableCodeReporting) {
      this.enableUnverifiableCodeReporting = enableUnverifiableCodeReporting;
      return this;
    }
  }

  public class ClassInlinerOptions {

    public int classInliningInstructionAllowance = -1;

    public int getClassInliningInstructionAllowance() {
      if (classInliningInstructionAllowance >= 0) {
        return classInliningInstructionAllowance;
      }
      if (isGeneratingClassFiles()) {
        return 50;
      }
      assert isGeneratingDex();
      return 65;
    }
  }

  public interface ApplyInliningToInlineePredicate {

    boolean test(AppView<?> appView, ProgramMethod method, int inliningDepth);
  }

  public static class InlinerOptions {

    public boolean enableConstructorInlining = true;

    public boolean enableInlining =
        !parseSystemPropertyForDevelopmentOrDefault("com.android.tools.r8.disableinlining", false);

    // This defines the limit of instructions in the inlinee
    public int simpleInliningInstructionLimit =
        parseSystemPropertyForDevelopmentOrDefault(
            "com.android.tools.r8.inliningInstructionLimit", -1);

    public int[] multiCallerInliningInstructionLimits =
        new int[] {Integer.MAX_VALUE, 28, 16, 12, 10};

    // This defines how many instructions of inlinees we can inlinee overall.
    public int inliningInstructionAllowance = 1500;

    // Maximum number of distinct values in a method that may be used in a monitor-enter
    // instruction.
    public int inliningMonitorEnterValuesAllowance = 4;

    // Maximum number of control flow resolution blocks that setup the register state before
    // the actual catch handler allowed when inlining. Threshold found empirically by testing on
    // GMS Core.
    public int inliningControlFlowResolutionBlocksThreshold = 15;

    public boolean enableInliningOfInvokesWithClassInitializationSideEffects = true;
    public boolean enableInliningOfInvokesWithNullableReceivers = true;
    public boolean disableInliningOfLibraryMethodOverrides = true;

    public ApplyInliningToInlineePredicate applyInliningToInlineePredicateForTesting = null;

    private final InternalOptions options;

    public InlinerOptions(InternalOptions options) {
      this.options = options;
    }

    public static void disableInlining(InternalOptions options) {
      options.inlinerOptions().enableInlining = false;
    }

    public static void setOnlyForceInlining(InternalOptions options) {
      options.testing.validInliningReasons = ImmutableSet.of(Reason.FORCE);
    }

    public int getSimpleInliningInstructionLimit() {
      // If a custom simple inlining instruction limit is set, then use that.
      if (simpleInliningInstructionLimit >= 0) {
        return simpleInliningInstructionLimit;
      }
      // Allow 4 instructions when using LIR regardless of backend.
      if (options.testing.useLir) {
        // TODO(b/288226522): We should reevaluate this for size and other inputs as it regresses
        //  compared to DEX code with limit 5 for tivi. This is set to 5 to avoid discard errors
        //  in chrome. Using 5 also improves size for chrome compared to a lower value.
        return 5;
      }
      // Allow 3 instructions when generating to class files.
      if (options.isGeneratingClassFiles()) {
        return 3;
      }
      // Allow the size of the dex code to be up to 5 bytes.
      assert options.isGeneratingDex();
      return 5;
    }

    public boolean isConstructorInliningEnabled() {
      return enableConstructorInlining;
    }

    public void setEnableConstructorInlining(boolean enableConstructorInlining) {
      this.enableConstructorInlining = enableConstructorInlining;
    }

    public boolean shouldApplyInliningToInlinee(
        AppView<?> appView, ProgramMethod inlinee, int inliningDepth) {
      if (applyInliningToInlineePredicateForTesting != null) {
        return applyInliningToInlineePredicateForTesting.test(appView, inlinee, inliningDepth);
      }
      if (options.protoShrinking().shouldApplyInliningToInlinee(appView, inlinee, inliningDepth)) {
        return true;
      }
      return false;
    }
  }

  public class HorizontalClassMergerOptions {

    // TODO(b/138781768): Set enable to true when this bug is resolved.
    private boolean enable =
        !Version.isDevelopmentVersion()
            || System.getProperty("com.android.tools.r8.disableHorizontalClassMerging") == null;
    private boolean enableInitial = true;
    // TODO(b/205611444): Enable by default.
    private boolean enableClassInitializerDeadlockDetection = true;
    private boolean enableInterfaceMerging =
        System.getProperty("com.android.tools.r8.enableHorizontalInterfaceMerging") != null;
    private boolean enableInterfaceMergingInInitial = false;
    private boolean enableSameFilePolicy =
        System.getProperty("com.android.tools.r8.enableSameFilePolicy") != null;
    private boolean enableSyntheticMerging = true;
    private boolean ignoreRuntimeTypeChecksForTesting = false;
    private boolean restrictToSynthetics = false;

    public void disable() {
      enable = false;
    }

    public void disableInitialRoundOfClassMerging() {
      enableInitial = false;
    }

    public void disableSyntheticMerging() {
      enableSyntheticMerging = false;
    }

    public void enable() {
      enable = true;
    }

    public void enableIf(boolean enable) {
      this.enable = enable;
    }

    public int getMaxClassGroupSizeInR8() {
      return 30;
    }

    public int getMaxClassGroupSizeInD8() {
      return 100;
    }

    public int getMaxInterfaceGroupSize() {
      return 100;
    }

    public boolean isConstructorMergingEnabled() {
      return true;
    }

    public boolean isClassInitializerDeadlockDetectionEnabled() {
      return enableClassInitializerDeadlockDetection;
    }

    public boolean isEnabled(HorizontalClassMerger.Mode mode) {
      if (!enable || debug || intermediate) {
        return false;
      }
      if (mode.isInitial()) {
        return enableInitial && inlinerOptions.enableInlining && isShrinking();
      }
      assert mode.isFinal();
      return true;
    }

    public boolean isIgnoreRuntimeTypeChecksForTestingEnabled() {
      return ignoreRuntimeTypeChecksForTesting;
    }

    public boolean isSameFilePolicyEnabled() {
      return enableSameFilePolicy;
    }

    public boolean isSyntheticMergingEnabled() {
      return enableSyntheticMerging;
    }

    public boolean isInterfaceMergingEnabled(HorizontalClassMerger.Mode mode) {
      if (!enableInterfaceMerging) {
        return false;
      }
      if (mode.isInitial()) {
        return enableInterfaceMergingInInitial;
      }
      assert mode.isFinal();
      return true;
    }

    public boolean isRestrictedToSynthetics() {
      return restrictToSynthetics || !isOptimizing() || !isShrinking();
    }

    public void setEnableClassInitializerDeadlockDetection() {
      enableClassInitializerDeadlockDetection = true;
    }

    public void setEnableInterfaceMerging() {
      enableInterfaceMerging = true;
    }

    public void setEnableInterfaceMerging(boolean enableInterfaceMerging) {
      this.enableInterfaceMerging = enableInterfaceMerging;
    }

    public void setEnableInterfaceMergingInInitial() {
      enableInterfaceMergingInInitial = true;
    }

    public void setEnableSameFilePolicy(boolean enableSameFilePolicy) {
      this.enableSameFilePolicy = enableSameFilePolicy;
    }

    public void setIgnoreRuntimeTypeChecksForTesting() {
      ignoreRuntimeTypeChecksForTesting = true;
    }

    public void setRestrictToSynthetics() {
      restrictToSynthetics = true;
    }
  }

  public static class OpenClosedInterfacesOptions {

    public interface OpenInterfaceWitnessSuppression {

      boolean isSuppressed(
          AppView<? extends AppInfoWithClassHierarchy> appView,
          TypeElement valueType,
          DexClass openInterface);
    }

    // Allow open interfaces by default. This is set to false in testing.
    private boolean allowOpenInterfaces = true;

    // When open interfaces are not allowed, compilation fails with an assertion error unless each
    // open interface witness is expected according to some suppression.
    private List<OpenInterfaceWitnessSuppression> suppressions = new ArrayList<>();

    public void disallowOpenInterfaces() {
      allowOpenInterfaces = false;
    }

    public OpenClosedInterfacesOptions suppressSingleOpenInterface(ClassReference classReference) {
      assert !allowOpenInterfaces;
      suppressions.add(
          (appView, valueType, openInterface) ->
              openInterface.getTypeName().equals(classReference.getTypeName()));
      return this;
    }

    public void suppressAllOpenInterfaces() {
      assert !allowOpenInterfaces;
      suppressions.add((appView, valueType, openInterface) -> true);
    }

    public void suppressAllOpenInterfacesDueToMissingClasses() {
      assert !allowOpenInterfaces;
      suppressions.add(
          (appView, valueType, openInterface) -> valueType.isBasedOnMissingClass(appView));
    }

    public void suppressArrayAssignmentsToJavaLangSerializable() {
      assert !allowOpenInterfaces;
      suppressions.add(
          (appView, valueType, openInterface) ->
              valueType.isArrayType()
                  && openInterface.getTypeName().equals("java.io.Serializable"));
    }

    public void suppressZipFileAssignmentsToJavaLangAutoCloseable() {
      assert !allowOpenInterfaces;
      suppressions.add(
          (appView, valueType, openInterface) ->
              valueType.isClassType()
                  && valueType
                      .asClassType()
                      .getClassType()
                      .getTypeName()
                      .equals("java.util.zip.ZipFile")
                  && openInterface.getTypeName().equals("java.lang.AutoCloseable"));
    }

    public boolean isOpenInterfacesAllowed() {
      return allowOpenInterfaces;
    }

    public boolean hasSuppressions() {
      return !suppressions.isEmpty();
    }

    public boolean isSuppressed(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        TypeElement valueType,
        DexClass openInterface) {
      return allowOpenInterfaces
          || suppressions.stream()
              .anyMatch(suppression -> suppression.isSuppressed(appView, valueType, openInterface));
    }
  }

  public static class MappingComposeOptions {
    public boolean enableExperimentalMappingComposition = false;

    public boolean allowEmptyMappedRanges =
        parseSystemPropertyForDevelopmentOrDefault(
            "com.android.tools.r8.allowemptymappedranges", false);

    // TODO(b/247136434): Disable for internal builds.
    public boolean allowNonExistingOriginalRanges = true;
    public Consumer<ClassNameMapper> generatedClassNameMapperConsumer = null;
  }

  public static class ApiModelTestingOptions {

    // Flag to specify if we should load the database or not. The api database is used for
    // library member rebinding.
    public boolean enableLibraryApiModeling =
        System.getProperty("com.android.tools.r8.disableApiModeling") == null;

    // The flag enableApiCallerIdentification controls if we can inline or merge targets with
    // different api levels. It is also the flag that specifies if we assign api levels to
    // references.
    public boolean enableApiCallerIdentification =
        System.getProperty("com.android.tools.r8.disableApiModeling") == null;
    public boolean checkAllApiReferencesAreSet =
        System.getProperty("com.android.tools.r8.disableApiModeling") == null;
    public boolean enableStubbingOfClasses =
        System.getProperty("com.android.tools.r8.disableApiModeling") == null;
    public boolean enableOutliningOfMethods =
        System.getProperty("com.android.tools.r8.disableApiModeling") == null;
    public boolean reportUnknownApiReferences =
        System.getProperty("com.android.tools.r8.reportUnknownApiReferences") != null;

    // TODO(b/232823652): Enable when we can compute the offset correctly.
    public boolean useMemoryMappedByteBuffer = false;

    // A mapping from references to the api-level introducing them.
    public Map<MethodReference, AndroidApiLevel> methodApiMapping = new HashMap<>();
    public Map<FieldReference, AndroidApiLevel> fieldApiMapping = new HashMap<>();
    public Map<ClassReference, AndroidApiLevel> classApiMapping = new HashMap<>();
    public BiConsumer<MethodReference, ComputedApiLevel> tracedMethodApiLevelCallback = null;

    public void visitMockedApiLevelsForReferences(
        DexItemFactory factory, BiConsumer<DexReference, AndroidApiLevel> apiLevelConsumer) {
      if (methodApiMapping.isEmpty() && fieldApiMapping.isEmpty() && classApiMapping.isEmpty()) {
        return;
      }
      classApiMapping.forEach(
          (classReference, apiLevel) -> {
            apiLevelConsumer.accept(factory.createType(classReference.getDescriptor()), apiLevel);
          });
      fieldApiMapping.forEach(
          (fieldReference, apiLevel) -> {
            apiLevelConsumer.accept(factory.createField(fieldReference), apiLevel);
          });
      methodApiMapping.forEach(
          (methodReference, apiLevel) -> {
            apiLevelConsumer.accept(factory.createMethod(methodReference), apiLevel);
          });
    }

    public boolean isApiLibraryModelingEnabled() {
      return enableLibraryApiModeling;
    }

    public boolean isCheckAllApiReferencesAreSet() {
      return enableLibraryApiModeling && checkAllApiReferencesAreSet;
    }

    public boolean isApiCallerIdentificationEnabled() {
      return enableLibraryApiModeling && enableApiCallerIdentification;
    }

    public void disableApiModeling() {
      enableLibraryApiModeling = false;
      enableApiCallerIdentification = false;
      enableOutliningOfMethods = false;
      enableStubbingOfClasses = false;
      checkAllApiReferencesAreSet = false;
    }

    /**
     * Disable the workarounds for missing APIs. This does not disable the use of the database, just
     * the introduction of soft-verification workarounds for potentially missing API references.
     */
    public void disableOutliningAndStubbing() {
      enableOutliningOfMethods = false;
      enableStubbingOfClasses = false;
    }

    public void disableApiCallerIdentification() {
      enableApiCallerIdentification = false;
    }

    public void disableStubbingOfClasses() {
      enableStubbingOfClasses = false;
    }
  }

  public static class ProtoShrinkingOptions {

    public boolean enableGeneratedExtensionRegistryShrinking = false;
    public boolean enableGeneratedMessageLiteShrinking = false;
    public boolean enableGeneratedMessageLiteBuilderShrinking = false;
    public boolean traverseOneOfAndRepeatedProtoFields = false;
    public boolean enableEnumLiteProtoShrinking = false;
    // Breaks the Chrome build if this is not enabled because of MethodToInvoke switchMaps.
    // See b/174530756 for more details.
    public boolean enableProtoEnumSwitchMapShrinking = true;

    public void disable() {
      enableGeneratedExtensionRegistryShrinking = false;
      enableGeneratedMessageLiteShrinking = false;
      enableGeneratedMessageLiteBuilderShrinking = false;
      traverseOneOfAndRepeatedProtoFields = false;
      enableEnumLiteProtoShrinking = false;
    }

    public boolean enableRemoveProtoEnumSwitchMap() {
      return isProtoShrinkingEnabled() && enableProtoEnumSwitchMapShrinking;
    }

    public boolean isProtoShrinkingEnabled() {
      return enableGeneratedExtensionRegistryShrinking
          || enableGeneratedMessageLiteShrinking
          || enableGeneratedMessageLiteBuilderShrinking
          || enableEnumLiteProtoShrinking;
    }

    public boolean isEnumLiteProtoShrinkingEnabled() {
      return enableEnumLiteProtoShrinking;
    }

    public boolean shouldApplyInliningToInlinee(
        AppView<?> appView, ProgramMethod inlinee, int inliningDepth) {
      if (isProtoShrinkingEnabled()) {
        if (appView.protoShrinker().getProtoReferences().isDynamicMethodBridge(inlinee)) {
          return true;
        }
        if (inliningDepth <= 1) {
          ProtoReferences protoReferences = appView.protoShrinker().getProtoReferences();
          if (inlinee.getHolderType() == protoReferences.generatedMessageLiteType) {
            return true;
          }
          if (inlinee.getHolder().getSuperType() == protoReferences.generatedMessageLiteType) {
            return true;
          }
        }
      }
      return false;
    }
  }

  public static class TestingOptions {

    public boolean roundtripThroughLir = false;
    private boolean useLir = System.getProperty("com.android.tools.r8.nolir") == null;
    private boolean convertLir = System.getProperty("com.android.tools.r8.convertlir") == null;

    public void enableLir() {
      useLir = true;
    }

    public void disableLir() {
      useLir = false;
    }

    public boolean canUseLir(AppView<?> appView) {
      return useLir && appView.enableWholeProgramOptimizations();
    }

    // As part of integrating LIR the compiler is split in three phases: pre, supported, and post.
    // Any attempt at building IR must have conversion options consistent with the active phase.
    private enum LirPhase {
      PRE,
      SUPPORTED,
      POST
    }

    private LirPhase currentPhase = LirPhase.PRE;

    public void enterLirSupportedPhase(AppView<?> appView, ExecutorService executorService)
        throws ExecutionException {
      assert isPreLirPhase();
      currentPhase = LirPhase.SUPPORTED;
      if (!canUseLir(appView) || !convertLir) {
        return;
      }
      // Convert code objects to LIR.
      ThreadUtils.processItems(
          appView.appInfo().classes(),
          clazz -> {
            // TODO(b/225838009): Also convert instance initializers to LIR, by adding support for
            //  computing the inlining constraint for LIR and using that in the class mergers, and
            //  class initializers, by updating the concatenation of clinits in horizontal class
            //  merging.
            clazz.forEachProgramMethodMatching(
                method ->
                    method.hasCode()
                        && !method.isInitializer()
                        && !appView.isCfByteCodePassThrough(method),
                method -> {
                  IRCode code =
                      method.buildIR(appView, MethodConversionOptions.forLirPhase(appView));
                  LirCode<Integer> lirCode =
                      IR2LirConverter.translate(
                          code,
                          BytecodeMetadataProvider.empty(),
                          LirStrategy.getDefaultStrategy().getEncodingStrategy(),
                          appView.options());
                  method.setCode(lirCode, appView);
                });
          },
          executorService);
      // Conversion to LIR via IR will allocate type elements.
      // They are not needed after construction so remove them again.
      appView.dexItemFactory().clearTypeElementsCache();
    }

    public void exitLirSupportedPhase() {
      assert isSupportedLirPhase();
      currentPhase = LirPhase.POST;
    }

    public void skipLirPhasesForTestingFinalOutput() {
      assert currentPhase == LirPhase.PRE;
      currentPhase = LirPhase.POST;
    }

    public boolean isPreLirPhase() {
      return currentPhase == LirPhase.PRE;
    }

    public boolean isSupportedLirPhase() {
      return currentPhase == LirPhase.SUPPORTED;
    }

    public boolean isPostLirPhase() {
      return currentPhase == LirPhase.POST;
    }

    // If false, use the desugared library implementation when desugared library is enabled.
    public boolean alwaysBackportListSetMapMethods = true;
    public boolean neverReuseCfLocalRegisters = false;
    public boolean checkReceiverAlwaysNullInCallSiteOptimization = true;
    public boolean forceInlineAPIConversions = false;
    public boolean ignoreValueNumbering = false;
    private boolean hasReadCheckDeterminism = false;
    private DeterminismChecker determinismChecker = null;
    public boolean usePcEncodingInCfForTesting = false;
    public boolean dexVersion40FromApiLevel30 =
        System.getProperty("com.android.tools.r8.dexVersion40ForApiLevel30") != null;
    public boolean dexContainerExperiment =
        System.getProperty("com.android.tools.r8.dexContainerExperiment") != null;

    // Testing options to analyse locality of items in DEX files when they are generated.
    public boolean calculateItemUseCountInDex = false;
    public boolean calculateItemUseCountInDexDumpSingleUseStrings = false;

    public boolean enableBinopOptimization = true;

    private DeterminismChecker getDeterminismChecker() {
      // Lazily read the env-var so that it can be set after options init.
      if (determinismChecker == null && !hasReadCheckDeterminism) {
        hasReadCheckDeterminism = true;
        String dir = System.getProperty("com.android.tools.r8.checkdeterminism");
        if (dir != null) {
          setDeterminismChecker(DeterminismChecker.createWithFileBacking(Paths.get(dir)));
        }
      }
      return determinismChecker;
    }

    public void setDeterminismChecker(DeterminismChecker checker) {
      determinismChecker = checker;
    }

    public void checkDeterminism(AppView<?> appView) {
      DeterminismChecker determinismChecker = getDeterminismChecker();
      if (determinismChecker != null) {
        determinismChecker.check(appView);
      }
    }

    public <E extends Exception> void checkDeterminism(
        ThrowingConsumer<DeterminismChecker, E> consumer) {
      DeterminismChecker determinismChecker = getDeterminismChecker();
      if (determinismChecker != null) {
        consumer.acceptWithRuntimeException(determinismChecker);
      }
    }

    public static final int NO_LIMIT = -1;

    public ArgumentPropagatorEventConsumer argumentPropagatorEventConsumer =
        ArgumentPropagatorEventConsumer.emptyConsumer();

    public Predicate<DexProgramClass> isEligibleForBridgeHoisting = Predicates.alwaysTrue();

    // Force writing the specified bytes as the DEX version content.
    public byte[] forceDexVersionBytes = null;

    public IROrdering irOrdering =
        InternalOptions.assertionsEnabled() && !InternalOptions.DETERMINISTIC_DEBUGGING
            ? NondeterministicIROrdering.getInstance()
            : IdentityIROrdering.getInstance();

    public BiFunction<MixedSectionLayoutStrategy, VirtualFile, MixedSectionLayoutStrategy>
        mixedSectionLayoutStrategyInspector = (strategy, virtualFile) -> strategy;

    public void setMixedSectionLayoutStrategyInspector(
        BiFunction<MixedSectionLayoutStrategy, VirtualFile, MixedSectionLayoutStrategy>
            mixedSectionLayoutStrategyInspector) {
      this.mixedSectionLayoutStrategyInspector = mixedSectionLayoutStrategyInspector;
    }

    public BiConsumer<AppInfoWithLiveness, Enqueuer.Mode> enqueuerInspector = null;

    public Consumer<String> processingContextsConsumer = null;

    public Function<AppView<AppInfoWithLiveness>, RepackagingConfiguration>
        repackagingConfigurationFactory = DefaultRepackagingConfiguration::new;

    public BiConsumer<DexItemFactory, HorizontallyMergedClasses> horizontallyMergedClassesConsumer =
        ConsumerUtils.emptyBiConsumer();
    public Function<List<Policy>, List<Policy>> horizontalClassMergingPolicyRewriter =
        Function.identity();
    public TriFunction<AppView<?>, Iterable<DexProgramClass>, DexProgramClass, DexProgramClass>
        horizontalClassMergingTarget = (appView, candidates, target) -> target;

    public BiConsumer<DexItemFactory, EnumDataMap> unboxedEnumsConsumer =
        ConsumerUtils.emptyBiConsumer();

    public BiConsumer<DexItemFactory, VerticallyMergedClasses> verticallyMergedClassesConsumer =
        ConsumerUtils.emptyBiConsumer();

    public Consumer<Deque<ProgramMethodSet>> waveModifier = waves -> {};

    public Consumer<DebugRepresentation> debugRepresentationCallback = null;

    public Consumer<DexProgramClass> globalSyntheticCreatedCallback = null;

    /**
     * If this flag is enabled, we will also compute the set of possible targets for invoke-
     * interface and invoke-virtual instructions that target a library method, and add the
     * corresponding edges to the call graph.
     *
     * <p>Setting this flag leads to more call graph edges, which can be good for size (e.g., it
     * increases the likelihood that virtual methods have been processed by the time their call
     * sites are processed, which allows more inlining).
     *
     * <p>However, the set of possible targets for such invokes can be very large. As an example,
     * consider the instruction {@code invoke-virtual {v0, v1}, `void Object.equals(Object)`}).
     * Therefore, tracing such invokes comes at a considerable performance penalty.
     */
    public boolean addCallEdgesForLibraryInvokes = false;

    public boolean allowClassInliningOfSynthetics = true;
    public boolean allowInjectedAnnotationMethods = false;
    public boolean allowInliningOfSynthetics = true;
    public boolean allowTypeErrors =
        !Version.isDevelopmentVersion()
            || System.getProperty("com.android.tools.r8.allowTypeErrors") != null;
    public boolean allowInvokeErrors = false;
    public boolean allowUnnecessaryDontWarnWildcards = true;
    public boolean allowUnusedDontWarnRules = true;
    public boolean alwaysUseExistingAccessInfoCollectionsInMemberRebinding = true;
    public boolean alwaysUsePessimisticRegisterAllocation = false;
    public boolean enableCheckCastAndInstanceOfRemoval = true;
    public boolean enableDeadSwitchCaseElimination = true;
    public boolean enableInvokeSuperToInvokeVirtualRewriting = true;
    public boolean enableMultiANewArrayDesugaringForClassFiles = false;
    public boolean enableSyntheticSharing = true;
    public boolean enableSwitchToIfRewriting = true;
    public boolean enableEnumUnboxingDebugLogs =
        System.getProperty("com.android.tools.r8.enableEnumUnboxingDebugLogs") != null;
    public boolean enableEnumWithSubtypesUnboxing = true;
    public boolean forceRedundantConstNumberRemoval = false;
    public boolean enableExperimentalDesugaredLibraryKeepRuleGenerator = false;
    public boolean invertConditionals = false;
    public boolean placeExceptionalBlocksLast = false;
    public boolean forceJumboStringProcessing = false;
    public boolean forcePcBasedEncoding = false;
    public int pcBasedDebugEncodingOverheadThreshold =
        System.getProperty("com.android.tools.r8.pc2pcOverheadThreshold") != null
            ? Integer.parseInt(System.getProperty("com.android.tools.r8.pc2pcOverheadThreshold"))
            : 200000;
    public Set<Inliner.Reason> validInliningReasons = null;
    public boolean noLocalsTableOnInput = false;
    public boolean forceNameReflectionOptimization = false;
    public boolean enableNarrowAndWideningingChecksInD8 = false;
    public BiConsumer<IRCode, AppView<?>> irModifier = null;
    public Consumer<IRCode> inlineeIrModifier = null;
    public int basicBlockMuncherIterationLimit = NO_LIMIT;
    public boolean dontReportFailingCheckDiscarded =
        System.getProperty("com.android.tools.r8.testing.dontReportFailingCheckDiscarded") != null;
    public boolean disableRecordApplicationReaderMap = false;
    public boolean trackDesugaredAPIConversions =
        System.getProperty("com.android.tools.r8.trackDesugaredAPIConversions") != null;
    public boolean enumUnboxingRewriteJavaCGeneratedMethod = false;
    // TODO(b/154793333): Enable assertions always when resolved.
    public boolean assertConsistentRenamingOfSignature = false;
    public boolean allowStaticInterfaceMethodsForPreNApiLevel = false;
    public int verificationSizeLimitInBytesOverride = -1;
    public boolean forceIRForCfToCfDesugar =
        System.getProperty("com.android.tools.r8.forceIRForCfToCfDesugar") != null;
    public boolean disableMappingToOriginalProgramVerification = false;
    public boolean allowInvalidCfAccessFlags =
        System.getProperty("com.android.tools.r8.allowInvalidCfAccessFlags") != null;
    public boolean verifyInputs = System.getProperty("com.android.tools.r8.verifyInputs") != null;
    // TODO(b/177333791): Set to true
    public boolean checkForNotExpandingMainDexTracingResult = false;
    public Set<String> allowedUnusedDontWarnPatterns = new HashSet<>();
    public boolean enableTestAssertions =
        System.getProperty("com.android.tools.r8.enableTestAssertions") != null;
    public boolean disableMarkingMethodsFinal =
        System.getProperty("com.android.tools.r8.disableMarkingMethodsFinal") != null;
    public boolean disableMarkingClassesFinal =
        System.getProperty("com.android.tools.r8.disableMarkingClassesFinal") != null;
    public boolean testEnableTestAssertions = false;
    public boolean keepMetadataInR8IfNotRewritten = true;

    // If set, pruned record fields are not used in hashCode/equals/toString and toString prints
    // minified field names instead of original field names.
    public boolean enableRecordModeling = true;

    // Flag to allow processing of resources in D8. A data resource consumer still needs to be
    // specified.
    public boolean enableD8ResourcesPassThrough = false;

    // TODO(b/144781417): This is disabled by default as some test apps appear to have such classes.
    public boolean allowNonAbstractClassesWithAbstractMethods = true;

    public boolean verifyKeptGraphInfo = false;

    public boolean readInputStackMaps = true;
    public boolean disableStackMapVerification = false;

    public boolean disableShortenLiveRanges = false;

    // Option for testing outlining with interface array arguments, see b/132420510.
    public boolean allowOutlinerInterfaceArrayArguments = false;

    public int limitNumberOfClassesPerDex = -1;

    public MinifierTestingOptions minifier = new MinifierTestingOptions();

    // Testing hooks to trigger effects in various compiler places.
    public Runnable hookInIrConversion = null;

    public static class MinifierTestingOptions {

      public Comparator<DexMethod> interfaceMethodOrdering = null;

      public Comparator<Wrapper<DexEncodedMethod>> getInterfaceMethodOrderingOrDefault(
          Comparator<Wrapper<DexEncodedMethod>> comparator) {
        if (interfaceMethodOrdering != null) {
          return (a, b) ->
              interfaceMethodOrdering.compare(a.get().getReference(), b.get().getReference());
        }
        return comparator;
      }
    }

    public boolean measureProguardIfRuleEvaluations = false;
    public ProguardIfRuleEvaluationData proguardIfRuleEvaluationData =
        new ProguardIfRuleEvaluationData();

    public static class ProguardIfRuleEvaluationData {

      public int numberOfProguardIfRuleClassEvaluations = 0;
      public int numberOfProguardIfRuleMemberEvaluations = 0;
    }

    public Consumer<ProgramMethod> callSiteOptimizationInfoInspector =
        ConsumerUtils.emptyConsumer();

    public Predicate<DexMethod> cfByteCodePassThrough = null;

    public boolean enableExperimentalMapFileVersion = false;

    public boolean alwaysGenerateLambdaFactoryMethods = false;
  }

  public MapVersion getMapFileVersion() {
    return testing.enableExperimentalMapFileVersion
        ? MapVersion.MAP_VERSION_EXPERIMENTAL
        : MapVersion.STABLE;
  }

  @VisibleForTesting
  public void disableNameReflectionOptimization() {
    // Use this util to disable get*Name() computation if the main intention of tests is checking
    // const-class, e.g., canonicalization, or some test classes' only usages are get*Name().
    enableNameReflectionOptimization = false;
  }

  private boolean hasMinApi(AndroidApiLevel level) {
    return getMinApiLevel().isGreaterThanOrEqualTo(level);
  }

  /**
   * Predicate to guard on the support of a language feature.
   *
   * <p>Note that if not desugaring or compiling to DEX, then the output is a mapping of the input
   * and thus all parts should be representable (assuming the compiler has support for them).
   */
  private boolean hasFeaturePresentFrom(AndroidApiLevel level) {
    if (desugarState.isOn() || isGeneratingDex()) {
      return level != null && hasMinApi(level);
    }
    // If not desugaring and not compiling to DEX, then the API level is effectively ignored and
    // we assume that everything in the input is supported in the output.
    assert minApiLevel.equals(B);
    return true;
  }

  /**
   * Predicate to guard against the possible presence of a VM bug.
   *
   * <p>Note that if the compilation is not desugaring to a min-api or targeting DEX at a min-api,
   * then the bug is assumed to be present as the CF output could be further compiled to any target.
   */
  private boolean canHaveBugPresentUntil(AndroidApiLevel level) {
    if (desugarState.isOn() || isGeneratingDex()) {
      return level == null || !hasMinApi(level);
    }
    assert minApiLevel.equals(B);
    return true;
  }

  /**
   * Allow access modification of synthetic lambda implementation methods in D8 to avoid generating
   * an excessive amount of accessibility bridges. In R8, the lambda implementation methods are
   * inlined into the synthesized accessibility bridges, thus we don't allow access modification.
   */
  public boolean canAccessModifyLambdaImplementationMethods(AppView<?> appView) {
    return !appView.enableWholeProgramOptimizations();
  }

  /**
   * Dex2Oat issues a warning for abstract methods on non-abstract classes, so we never allow this.
   *
   * <p>Note that having an invoke instruction that targets an abstract method on a non-abstract
   * class will fail with a verification error on Dalvik. Therefore, this must not be more
   * permissive than {@code return minApiLevel >= AndroidApiLevel.L.getLevel()}.
   *
   * <p>See b/132953944.
   */
  @SuppressWarnings("ConstantConditions")
  public boolean canUseAbstractMethodOnNonAbstractClass() {
    boolean result = false;
    assert !(result && canHaveDalvikAbstractMethodOnNonAbstractClassVerificationBug());
    return result;
  }

  public boolean canUseConstClassInstructions(CfVersion cfVersion) {
    assert isGeneratingClassFiles();
    return cfVersion.isGreaterThanOrEqualTo(requiredCfVersionForConstClassInstructions());
  }

  public CfVersion requiredCfVersionForConstClassInstructions() {
    assert isGeneratingClassFiles();
    return CfVersion.V1_5;
  }

  public static AndroidApiLevel invokePolymorphicOnMethodHandleApiLevel() {
    return AndroidApiLevel.O;
  }

  public boolean canUseInvokePolymorphicOnMethodHandle() {
    return hasFeaturePresentFrom(invokePolymorphicOnMethodHandleApiLevel());
  }

  public static AndroidApiLevel invokePolymorphicOnVarHandleApiLevel() {
    return AndroidApiLevel.P;
  }

  public boolean canUseInvokePolymorphicOnVarHandle() {
    return hasFeaturePresentFrom(invokePolymorphicOnMethodHandleApiLevel());
  }

  public static AndroidApiLevel constantMethodHandleApiLevel() {
    return AndroidApiLevel.P;
  }

  public boolean canUseConstantMethodHandle() {
    return hasFeaturePresentFrom(constantMethodHandleApiLevel());
  }

  public static AndroidApiLevel constantMethodTypeApiLevel() {
    return AndroidApiLevel.P;
  }

  public boolean canUseConstantMethodType() {
    return hasFeaturePresentFrom(constantMethodTypeApiLevel());
  }

  public static AndroidApiLevel invokeCustomApiLevel() {
    return AndroidApiLevel.O;
  }

  public boolean canUseInvokeCustom() {
    return hasFeaturePresentFrom(invokeCustomApiLevel());
  }

  public static AndroidApiLevel constantDynamicApiLevel() {
    return null;
  }

  public boolean canUseConstantDynamic() {
    return hasFeaturePresentFrom(constantDynamicApiLevel());
  }

  public static AndroidApiLevel defaultAndStaticInterfaceMethodsApiLevel() {
    return AndroidApiLevel.N;
  }

  public static AndroidApiLevel defaultInterfaceMethodsApiLevel() {
    return defaultAndStaticInterfaceMethodsApiLevel();
  }

  public static AndroidApiLevel staticInterfaceMethodsApiLevel() {
    return defaultAndStaticInterfaceMethodsApiLevel();
  }

  public boolean canUseDefaultAndStaticInterfaceMethods() {
    return hasFeaturePresentFrom(defaultInterfaceMethodsApiLevel());
  }

  public static AndroidApiLevel privateInterfaceMethodsApiLevel() {
    return AndroidApiLevel.N;
  }

  public boolean canUsePrivateInterfaceMethods() {
    return hasFeaturePresentFrom(privateInterfaceMethodsApiLevel());
  }

  public static AndroidApiLevel varHandleApiLevel() {
    return AndroidApiLevel.T;
  }

  public boolean canUseVarHandle() {
    return hasFeaturePresentFrom(varHandleApiLevel());
  }

  public boolean canUseNestBasedAccess() {
    return hasFeaturePresentFrom(null) || emitNestAnnotationsInDex;
  }

  public boolean canUseRecords() {
    return hasFeaturePresentFrom(AndroidApiLevel.U) || emitRecordAnnotationsInDex;
  }

  public boolean canUseSealedClasses() {
    return hasFeaturePresentFrom(AndroidApiLevel.U) || emitPermittedSubclassesAnnotationsInDex;
  }

  public boolean canLeaveStaticInterfaceMethodInvokes() {
    return hasFeaturePresentFrom(AndroidApiLevel.L);
  }

  public boolean canUseTwrCloseResourceMethod() {
    return hasFeaturePresentFrom(AndroidApiLevel.K);
  }

  public boolean canUseSpacesInSimpleName() {
    return itemFactory.getSkipNameValidationForTesting()
        || hasFeaturePresentFrom(AndroidApiLevel.R);
  }

  public boolean enableBackportedMethodRewriting() {
    return desugarState.isOn() && minApiLevel.isLessThan(AndroidApiLevel.ANDROID_PLATFORM);
  }

  public boolean enableTryWithResourcesDesugaring() {
    switch (tryWithResourcesDesugaring) {
      case Off:
        return false;
      case Auto:
        return desugarState.isOn() && !canUseTwrCloseResourceMethod();
    }
    throw new Unreachable();
  }

  public boolean canUseDexPc2PcAsDebugInformation() {
    return isGeneratingDex() && lineNumberOptimization.isOn();
  }

  // Debug entries may be dropped only if the source file content allows being omitted from
  // stack traces, or if the VM will report the source file even with a null valued debug info.
  public boolean allowDiscardingResidualDebugInfo() {
    // TODO(b/146565491): We can drop debug info once fixed at a known min-api.
    return sourceFileProvider != null && sourceFileProvider.allowDiscardingSourceFile();
  }

  public boolean allowDiscardingResidualDebugInfo(ProgramMethod method) {
    // TODO(b/146565491): We can drop debug info once fixed at a known min-api.
    DexString sourceFile = method.getHolder().getSourceFile();
    return sourceFile == null || sourceFile.equals(itemFactory.defaultSourceFileAttribute);
  }

  public boolean canUseNativeDexPcInsteadOfDebugInfo() {
    return canUseDexPc2PcAsDebugInformation()
        && hasMinApi(AndroidApiLevel.O)
        && allowDiscardingResidualDebugInfo();
  }

  public boolean canUseNativeDexPcInsteadOfDebugInfo(ProgramMethod method) {
    return canUseDexPc2PcAsDebugInformation()
        && hasMinApi(AndroidApiLevel.O)
        && allowDiscardingResidualDebugInfo(method);
  }

  public boolean shouldMaterializeLineInfoForNativePcEncoding(ProgramMethod method) {
    assert method.getDefinition().getCode().asDexCode() != null;
    assert method.getDefinition().getCode().asDexCode().getDebugInfo() == null;
    return method.getHolder().originatesFromDexResource()
        && canUseNativeDexPcInsteadOfDebugInfo(method);
  }

  public boolean isInterfaceMethodDesugaringEnabled() {
    // This condition is to filter out tests that never set program consumer.
    if (!hasConsumer()) {
      return false;
    }
    return desugarState.isOn()
        && interfaceMethodDesugaring == OffOrAuto.Auto
        && !canUseDefaultAndStaticInterfaceMethods();
  }

  public boolean isSwitchRewritingEnabled() {
    return enableSwitchRewriting && !debug;
  }

  public boolean isStringSwitchConversionEnabled() {
    return enableStringSwitchConversion && !debug;
  }

  public boolean canUseMultidex() {
    assert isGeneratingDex();
    return intermediate || hasMinApi(AndroidApiLevel.L);
  }

  public boolean canUseJavaUtilObjects() {
    return hasFeaturePresentFrom(AndroidApiLevel.K);
  }

  public boolean canUseJavaUtilObjectsIsNull() {
    return hasFeaturePresentFrom(AndroidApiLevel.N);
  }

  public boolean canUseSuppressedExceptions() {
    // TODO(b/214239152): Suppressed exceptions are @hide from at least 4.0.1 / Android I / API 14.
    return hasFeaturePresentFrom(AndroidApiLevel.K);
  }

  public boolean canUseAssertionErrorTwoArgumentConstructor() {
    return hasFeaturePresentFrom(AndroidApiLevel.K);
  }

  public boolean canUseCanonicalizedCodeObjects() {
    return hasFeaturePresentFrom(AndroidApiLevel.S);
  }

  public CfVersion classFileVersionAfterDesugaring(CfVersion version) {
    assert isGeneratingClassFiles();
    if (!isDesugaring()) {
      return version;
    }
    CfVersion maxVersionAfterDesugar =
        canUseDefaultAndStaticInterfaceMethods() ? CfVersion.V1_8 : CfVersion.V1_7;
    return Ordered.min(maxVersionAfterDesugar, version);
  }

  // The Apache Harmony-based AssertionError constructor which takes an Object on API 15 and older
  // calls the Error supertype constructor with null as the exception cause. This prevents
  // subsequent calls to initCause() because its implementation checks that cause==this before
  // allowing a cause to be set.
  //
  // https://android.googlesource.com/platform/libcore/+/refs/heads/ics-mr1/luni/src/main/java/java/lang/AssertionError.java#56
  public boolean canInitCauseAfterAssertionErrorObjectConstructor() {
    return hasFeaturePresentFrom(AndroidApiLevel.J);
  }

  // Art had a bug (b/68761724) for Android N and O in the arm32 interpreter
  // where an aget-wide instruction using the same register for the array
  // and the first register of the result could lead to the wrong exception
  // being thrown on out of bounds.
  public boolean canUseSameArrayAndResultRegisterInArrayGetWide() {
    return hasFeaturePresentFrom(AndroidApiLevel.P);
  }

  // Some Lollipop versions of Art found in the wild perform invalid bounds
  // check elimination. There is a fast path of loops and a slow path.
  // The bailout to the slow path is performed too early and therefore
  // the array-index variable might not be defined in the slow path code leading
  // to use of undefined registers as indices into arrays. The result
  // is ArrayIndexOutOfBounds exceptions.
  //
  // In an attempt to help these Art VMs, all single-width constants are initialized and not moved.
  //
  // There is no guarantee that this works, but it does make the problem
  // disappear on the one known instance of this problem.
  //
  // See b/69364976 and b/77996377.
  public boolean canHaveBoundsCheckEliminationBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.M);
  }

  // MediaTek JIT compilers for KitKat phones did not implement the not
  // instruction as it was not generated by DX. Therefore, apps containing
  // not instructions would crash if the code was JIT compiled. Therefore,
  // we can only use not instructions if we are targeting Art-based
  // phones.
  public boolean canUseNotInstruction() {
    return hasFeaturePresentFrom(AndroidApiLevel.L);
  }

  // Art before M has a verifier bug where the type of the contents of the receiver register is
  // assumed to not change. If the receiver register is reused for something else the verifier
  // will fail and the code will not run.
  public boolean canHaveThisTypeVerifierBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.M);
  }

  // Art crashes if we do dead reference elimination of the receiver in release mode and Art
  // is asked for the |this| object over a JDWP connection at a point where the receiver
  // register has been clobbered.
  //
  // See b/116683601 and b/116837585.
  public boolean canHaveThisJitCodeDebuggingBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.Q);
  }

  // The dalvik jit had a bug where the long operations add, sub, or, xor and and would write
  // the first part of the result long before reading the second part of the input longs.
  public boolean canHaveOverlappingLongRegisterBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.L);
  }

  // Some dalvik versions found in the wild perform invalid JIT compilation of cmp-long
  // instructions where the result register overlaps with the input registers.
  // See b/74084493.
  //
  // The same dalvik versions also have a bug where the JIT compilation of code such as:
  //
  // void method(long l) {
  //  if (l < 0) throw new RuntimeException("less than");
  //  if (l == 0) throw new RuntimeException("equal");
  // }
  //
  // Will enter the case for l==0 even when l is non-zero. The code generated for this is of
  // the form:
  //
  // 0:   0x00: ConstWide16         v0, 0x0000000000000000 (0)
  // 1:   0x02: CmpLong             v2, v4, v0
  // 2:   0x04: IfLtz               v2, 0x0c (+8)
  // 3:   0x06: IfNez               v2, 0x0a (+4)
  //
  // However, the jit apparently clobbers the input register in the IfLtz instruction. Therefore,
  // for dalvik VMs we have to instead generate the following code:
  //
  // 0:   0x00: ConstWide16         v0, 0x0000000000000000 (0)
  // 1:   0x02: CmpLong             v2, v4, v0
  // 2:   0x04: IfLtz               v2, 0x0e (+10)
  // 3:   0x06: CmpLong             v2, v4, v0
  // 4:   0x08: IfNez               v2, 0x0c (+4)
  //
  // See b/75408029.
  public boolean canHaveCmpLongBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.L);
  }

  // Some Lollipop VMs crash if there is a const instruction between a cmp and an if instruction.
  //
  // Crashing code:
  //
  //    :goto_0
  //    cmpg-float v0, p0, p0
  //    const/4 v1, 0
  //    if-gez v0, :cond_0
  //    add-float/2addr p0, v1
  //    goto :goto_0
  //    :cond_0
  //    return p0
  //
  // Working code:
  //    :goto_0
  //    const/4 v1, 0
  //    cmpg-float v0, p0, p0
  //    if-gez v0, :cond_0
  //    add-float/2addr p0, v1
  //    goto :goto_0
  //    :cond_0
  //    return p0
  //
  // See b/115552239.
  public boolean canHaveCmpIfFloatBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.M);
  }

  // Some Lollipop VMs incorrectly optimize code with mul2addr instructions. In particular,
  // the following hash code method produces wrong results after optimizations:
  //
  //    0:   0x00: IgetObject          v0, v3, Field java.lang.Class MultiClassKey.first
  //    1:   0x02: InvokeVirtual       { v0 } Ljava/lang/Object;->hashCode()I
  //    2:   0x05: MoveResult          v0
  //    3:   0x06: Const16             v1, 0x001f (31)
  //    4:   0x08: MulInt2Addr         v1, v0
  //    5:   0x09: IgetObject          v2, v3, Field java.lang.Class MultiClassKey.second
  //    6:   0x0b: InvokeVirtual       { v2 } Ljava/lang/Object;->hashCode()I
  //    7:   0x0e: MoveResult          v2
  //    8:   0x0f: AddInt2Addr         v1, v2
  //    9:   0x10: Return              v1
  //
  // It seems that the issue is the MulInt2Addr instructions. Avoiding that, the VM computes
  // hash codes correctly also after optimizations.
  //
  // This issue has only been observed on a Verizon Ellipsis 8 tablet. See b/76115465.
  public boolean canHaveMul2AddrBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.M);
  }

  // Some Marshmallow VMs create an incorrect doubly-linked list of instructions. When the VM
  // attempts to create a fixup for a Cortex 53 long add/sub issue, it may diverge due to the cyclic
  // list.
  //
  // See b/77842465.
  public boolean canHaveDex2OatLinkedListBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.N);
  }

  // dex2oat on Marshmallow VMs does aggressive inlining which can eat up all the memory on
  // devices for self-recursive methods.
  //
  // See b/111960171
  public boolean canHaveDex2OatInliningIssue() {
    return canHaveBugPresentUntil(AndroidApiLevel.N);
  }

  // Art 7.0.0 and later Art JIT may perform an invalid optimization if a string new-instance does
  // not flow directly to the init call.
  //
  // See b/78493232 and b/80118070.
  public boolean canHaveArtStringNewInitBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.Q);
  }

  // Dalvik tracing JIT may perform invalid optimizations when int/float values are converted to
  // double and used in arithmetic operations.
  //
  // See b/77496850.
  public boolean canHaveNumberConversionRegisterAllocationBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.L);
  }

  // Some Lollipop mediatek VMs have a peculiar bug where the inliner crashes if there is a
  // simple constructor that just forwards its arguments to the super constructor. Strangely,
  // this happens only for specific signatures: so far the only reproduction we have is for
  // a constructor accepting two doubles and one object.
  //
  // To workaround this we insert a materializing const instruction before the super init
  // call. Having a temporary register seems to disable the buggy optimizations.
  //
  // See b/68378480.
  public boolean canHaveForwardingInitInliningBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.M);
  }

  // Some Lollipop x86_64 VMs have a bug causing a segfault if an exception handler directly targets
  // a conditional-loop header. This cannot happen for debug builds as the existence of a
  // move-exception instruction will ensure a non-direct target.
  //
  // To workaround this in release builds, we insert a materializing nop instruction in the
  // exception handler forcing it not directly target any loop header.
  //
  // See b/111337896.
  public boolean canHaveExceptionTargetingLoopHeaderBug() {
    assert isGeneratingDex();
    return !debug && canHaveBugPresentUntil(AndroidApiLevel.M);
  }

  // The Dalvik tracing JIT can trace past the end of the instruction stream and end up
  // parsing non-code bytes as code (typically leading to a crash). See b/117907456.
  //
  // In order to workaround this we insert a goto past the throw, and another goto after the throw
  // jumping back to the throw.

  // We used to insert a empty loop at the end, however, mediatek has an optimizer
  // on lollipop devices that cannot deal with an unreachable infinite loop, so we
  // couldn't do that. See b/119895393.
  // We also could not insert any dead code (e.g. a return) because that would make mediatek
  // dominator calculations on 7.0.0 crash. See b/128926846.
  public boolean canHaveTracingPastInstructionsStreamBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.L);
  }

  // The art verifier incorrectly propagates type information for the following pattern:
  //
  //  move vA, vB
  //  instance-of vB, vA, Type
  //  if-eqz/nez vB
  //
  // In that case it will assume that vB has object type after the if. Therefore, if the
  // result of the instance-of operation is reused in a boolean context the verifier will
  // fail with a type conflict.
  //
  // In order to make sure that cannot happen, we insert a nop between the move and
  // the instance-of instruction so that this pattern in the art verifier does not
  // match.
  //
  //  move vA, vB
  //  nop
  //  instance-of vB, vA, Type
  //  if-eqz/nez vB
  //
  // This happens rarely, but it can happen in debug mode where the move
  // put a value into a new register which has associated locals information.
  //
  // Fixed in Android Q, see b/120985556.
  public boolean canHaveArtInstanceOfVerifierBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.Q);
  }

  // Some Art Lollipop version do not deal correctly with long-to-int conversions.
  //
  // In particular, the following code performs an out of bounds array access when the
  // long loaded from the long array is very large (has non-zero values in the upper 32 bits).
  //
  //   aget-wide v9, v3, v1
  //   long-to-int v9, v9
  //   aget-wide v10, v3, v9
  //
  // The issue seems to be that the higher bits of the 64-bit register holding the long
  // are not cleared and the integer is therefore a 64-bit integer that is not truncated
  // to 32 bits.
  //
  // As a workaround, we do not allow long-to-int to have the same source and target register
  // for min-apis where lollipop devices could be targeted.
  //
  // See b/80262475.
  public boolean canHaveLongToIntBug() {
    // We have only seen this happening on Lollipop arm64 backends. We have tested on
    // Marshmallow and Nougat arm64 devices and they do not have the bug.
    return canHaveBugPresentUntil(AndroidApiLevel.M);
  }

  // The Art VM for Android N through P has a bug in the JIT that means that if the same
  // exception block with a move-exception instruction is targeted with more than one type
  // of exception the JIT will incorrectly assume that the exception object has one of these
  // types and will optimize based on that one type instead of taking all the types into account.
  //
  // In order to workaround that, we always generate distinct move-exception instructions for
  // distinct dex types.
  //
  // See b/120164595.
  public boolean canHaveExceptionTypeBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.Q);
  }

  // Art 4.0.4 fails with a verification error when a null-literal is being passed directly to an
  // aget instruction. We therefore need to be careful when performing trivial check-cast
  // elimination of check-cast instructions where the value being cast is the constant null.
  // See b/123269162.
  public boolean canHaveArtCheckCastVerifierBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.J);
  }

  // The verifier will merge A[] and B[] to Object[], even when both A and B implement an interface
  // I, i.e., the join should have been I[]. This can lead to verification errors when the value is
  // used as an I[].
  //
  // See b/69826014.
  public boolean canHaveIncorrectJoinForArrayOfInterfacesBug() {
    return true;
  }

  // The dalvik verifier will crash the program if there is a try catch block with an exception
  // type that does not exist.
  // We don't do anything special about this, except that we don't inline methods that have a
  // catch handler with the ReflectiveOperationException type, i.e., if the program did not crash
  // in the non R8 case it should not in the R8 case.
  // Currently we handle only the ReflectiveOperationException, but there could be other exceptions.
  // We do this for all pre art version, in case we add more Exception types later on. The
  // problem is there for all dalvik vms, but the exception was added in api level 19
  // so we don't see it there.
  //
  // See b/131349148
  public boolean canHaveDalvikCatchHandlerVerificationBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.L);
  }

  // Having an invoke instruction that targets an abstract method on a non-abstract class will fail
  // with a verification error.
  //
  // See b/132953944.
  public boolean canHaveDalvikAbstractMethodOnNonAbstractClassVerificationBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.L);
  }

  // On dalvik we see issues when using an int value in places where a boolean, byte, char, or short
  // is expected.
  //
  // For example, if we inline the following method into the call site:
  //   public int value;
  //   public boolean getValue() {
  //     return value;
  //   }
  //
  // See also b/134304597 and b/124152497.
  public boolean canHaveDalvikIntUsedAsNonIntPrimitiveTypeBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.L);
  }

  // The standard library prior to API 19 did not contain a ZipFile that implemented Closable.
  //
  // See b/177532008.
  public boolean canHaveZipFileWithMissingCloseableBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.K);
  }

  // Some versions of Dalvik had a bug where a switch with a MAX_INT key would still go to
  // the default case when switching on the value MAX_INT.
  //
  // See b/177790310.
  public boolean canHaveSwitchMaxIntBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.K);
  }

  // On Dalvik the methods Integer.parseInt and Long.parseLong does not support strings with a '+'
  // prefix
  //
  // See b/182137865.
  public boolean canParseNumbersWithPlusPrefix() {
    return hasFeaturePresentFrom(AndroidApiLevel.L);
  }

  // Lollipop and Marshmallow devices do not correctly handle invoke-super when the static holder
  // is higher up in the hierarchy than the method that the invoke-super should resolve to.
  //
  // See b/215573892.
  public boolean canHaveSuperInvokeBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.N);
  }

  // Some Dalvik and Art MVs does not support interface invokes to Object
  // members not explicitly defined on the symbolic reference of the
  // interface invoke. In these cases rewrite to a virtual invoke with
  // the symbolic reference java.lang.Object.
  //
  // The support was added in Android O, however at least for j.l.CharSequence.equals the handling
  // in Art was incorrect (b/231450655). Further down the line b/271408544 indicate that it is not
  // until Android S that this is working.
  //
  // javac started generating code like this with the fix for JDK-8272564, which will be part of
  // JDK 18.
  //
  // See b/218298666.
  public boolean canHaveInvokeInterfaceToObjectMethodBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.S);
  }

  // Until we fully drop support for API levels < 16, we have to emit an empty annotation set to
  // work around a DALVIK bug. See b/36951668.
  public boolean canHaveDalvikEmptyAnnotationSetBug() {
    return canHaveBugPresentUntil(AndroidApiLevel.J_MR1);
  }

  public boolean canHaveNonReboundConstructorInvoke() {
    return isGeneratingDex() && minApiLevel.isGreaterThanOrEqualTo(AndroidApiLevel.L);
  }

  // b/238399429 Some art 6 vms have issues with multiple monitors in the same method
  // Don't inline code with monitors into methods that already have monitors.
  public boolean canHaveIssueWithInlinedMonitors() {
    return canHaveBugPresentUntil(AndroidApiLevel.N);
  }

  // b/272725341. ART 11 and 12 re-introduced hard verification errors when unable to compute
  // subtype relationship when no other verification issues exists in code.
  public boolean canHaveVerifyErrorForUnknownUnusedReturnValue() {
    return isGeneratingDex() && canHaveBugPresentUntil(AndroidApiLevel.T);
  }

  public boolean canInitNewInstanceUsingSuperclassConstructor() {
    return canHaveNonReboundConstructorInvoke();
  }
}
