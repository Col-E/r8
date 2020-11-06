// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.graph.FieldAccessInfoImpl.MISSING_FIELD_ACCESS_INFO;
import static com.android.tools.r8.naming.IdentifierNameStringUtils.identifyIdentifier;
import static com.android.tools.r8.naming.IdentifierNameStringUtils.isReflectionMethod;
import static com.android.tools.r8.shaking.AnnotationRemover.shouldKeepAnnotation;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.code.CfOrDexInstruction;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.experimental.graphinfo.GraphConsumer;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexItemFactory.ClassMethods;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.DirectMappedDexApplication.Builder;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.EnumValueInfoMapCollection;
import com.android.tools.r8.graph.FieldAccessInfoCollectionImpl;
import com.android.tools.r8.graph.FieldAccessInfoImpl;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.GenericSignatureEnqueuerAnalysis;
import com.android.tools.r8.graph.GraphLens.NestedGraphLens;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.LookupLambdaTarget;
import com.android.tools.r8.graph.LookupTarget;
import com.android.tools.r8.graph.MethodAccessInfoCollection;
import com.android.tools.r8.graph.ObjectAllocationInfoCollectionImpl;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMember;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.graph.ResolutionResult.FailedResolutionResult;
import com.android.tools.r8.graph.ResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.graph.UseRegistry.MethodHandleUse;
import com.android.tools.r8.graph.analysis.DesugaredLibraryConversionWrapperAnalysis;
import com.android.tools.r8.graph.analysis.EnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.EnqueuerCheckCastAnalysis;
import com.android.tools.r8.graph.analysis.EnqueuerExceptionGuardAnalysis;
import com.android.tools.r8.graph.analysis.EnqueuerInstanceOfAnalysis;
import com.android.tools.r8.graph.analysis.EnqueuerInvokeAnalysis;
import com.android.tools.r8.ir.analysis.proto.ProtoEnqueuerUseRegistry;
import com.android.tools.r8.ir.analysis.proto.schema.ProtoEnqueuerExtension;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.ConstantValueUtils;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter;
import com.android.tools.r8.ir.desugar.DesugaredLibraryAPIConverter;
import com.android.tools.r8.ir.desugar.LambdaClass;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.ir.desugar.LambdaRewriter;
import com.android.tools.r8.kotlin.KotlinMetadataEnqueuerExtension;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.naming.identifiernamestring.IdentifierNameStringLookupResult;
import com.android.tools.r8.naming.identifiernamestring.IdentifierNameStringTypeLookupResult;
import com.android.tools.r8.shaking.DelayedRootSetActionItem.InterfaceMethodSyntheticBridgeAction;
import com.android.tools.r8.shaking.EnqueuerWorklist.EnqueuerAction;
import com.android.tools.r8.shaking.GraphReporter.KeepReasonWitness;
import com.android.tools.r8.shaking.KeepInfo.Joiner;
import com.android.tools.r8.shaking.KeepInfoCollection.MutableKeepInfoCollection;
import com.android.tools.r8.shaking.RootSetBuilder.ConsequentRootSet;
import com.android.tools.r8.shaking.RootSetBuilder.ItemsWithRules;
import com.android.tools.r8.shaking.RootSetBuilder.MutableItemsWithRules;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.shaking.ScopedDexMethodSet.AddMethodIfMoreVisibleResult;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.DesugarState;
import com.android.tools.r8.utils.IteratorUtils;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.Visibility;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.collections.ProgramFieldSet;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import it.unimi.dsi.fastutil.objects.Object2BooleanArrayMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import java.lang.reflect.InvocationHandler;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Approximates the runtime dependencies for the given set of roots.
 * <p>
 * <p>The implementation filters the static call-graph with liveness information on classes to
 * remove virtual methods that are reachable by their static type but are unreachable at runtime as
 * they are not visible from any instance.
 * <p>
 * <p>As result of the analysis, an instance of {@link AppInfoWithLiveness} is returned. See the
 * field descriptions for details.
 */
public class Enqueuer {

  public enum Mode {
    INITIAL_TREE_SHAKING,
    FINAL_TREE_SHAKING,
    MAIN_DEX_TRACING,
    WHY_ARE_YOU_KEEPING;

    public boolean isInitialTreeShaking() {
      return this == INITIAL_TREE_SHAKING;
    }

    public boolean isFinalTreeShaking() {
      return this == FINAL_TREE_SHAKING;
    }

    public boolean isInitialOrFinalTreeShaking() {
      return isInitialTreeShaking() || isFinalTreeShaking();
    }

    public boolean isTracingMainDex() {
      return this == MAIN_DEX_TRACING;
    }

    public boolean isWhyAreYouKeeping() {
      return this == WHY_ARE_YOU_KEEPING;
    }
  }

  private final boolean forceProguardCompatibility;
  private final Mode mode;

  private Set<EnqueuerAnalysis> analyses = Sets.newIdentityHashSet();
  private Set<EnqueuerInvokeAnalysis> invokeAnalyses = Sets.newIdentityHashSet();
  private Set<EnqueuerInstanceOfAnalysis> instanceOfAnalyses = Sets.newIdentityHashSet();
  private Set<EnqueuerExceptionGuardAnalysis> exceptionGuardAnalyses = Sets.newIdentityHashSet();
  private Set<EnqueuerCheckCastAnalysis> checkCastAnalyses = Sets.newIdentityHashSet();

  // Don't hold a direct pointer to app info (use appView).
  private AppInfoWithClassHierarchy appInfo;
  private final AppView<AppInfoWithClassHierarchy> appView;
  private SubtypingInfo subtypingInfo;
  private final InternalOptions options;
  private RootSet rootSet;
  private ProguardClassFilter dontWarnPatterns;
  private final EnqueuerUseRegistryFactory useRegistryFactory;
  private AnnotationRemover.Builder annotationRemoverBuilder;
  private final EnqueuerDefinitionSupplier enqueuerDefinitionSupplier;

  private final FieldAccessInfoCollectionImpl fieldAccessInfoCollection =
      new FieldAccessInfoCollectionImpl();
  private final MethodAccessInfoCollection.SortedBuilder methodAccessInfoCollection =
      MethodAccessInfoCollection.sortedBuilder();
  private final ObjectAllocationInfoCollectionImpl.Builder objectAllocationInfoCollection;
  private final Map<DexCallSite, ProgramMethodSet> callSites = new IdentityHashMap<>();

  private final Set<DexReference> identifierNameStrings = Sets.newIdentityHashSet();

  /**
   * Tracks the dependency between a method and the super-method it calls, if any. Used to make
   * super methods become live when they become reachable from a live sub-method.
   */
  private final Map<DexEncodedMethod, ProgramMethodSet> superInvokeDependencies =
      Maps.newIdentityHashMap();
  /** Set of instance fields that can be reached by read/write operations. */
  private final Map<DexProgramClass, ProgramFieldSet> reachableInstanceFields =
      Maps.newIdentityHashMap();

  /**
   * Set of types that are mentioned in the program. We at least need an empty abstract class item
   * for these.
   */
  private final SetWithReportedReason<DexProgramClass> liveTypes = new SetWithReportedReason<>();

  /** Set of classes whose initializer may execute. */
  private final SetWithReportedReason<DexProgramClass> initializedClasses =
      new SetWithReportedReason<>();

  /**
   * Set of interfaces whose interface initializer may execute directly in response to a static
   * field or method access on the interface.
   */
  private final SetWithReportedReason<DexProgramClass> directlyInitializedInterfaces =
      new SetWithReportedReason<>();

  /**
   * Set of interfaces whose interface initializer may execute indirectly as a side-effect of the
   * class initialization of a (non-interface) subclass.
   */
  private final SetWithReportedReason<DexProgramClass> indirectlyInitializedInterfaces =
      new SetWithReportedReason<>();

  /**
   * Set of live types defined in the library and classpath.
   *
   * <p>Used to build a new app of just referenced types and avoid duplicate tracing.
   */
  private final Set<DexClass> liveNonProgramTypes = Sets.newIdentityHashSet();

  /** Set of reachable proto types that will be dead code eliminated. */
  private final Set<DexProgramClass> deadProtoTypeCandidates = Sets.newIdentityHashSet();

  /** Set of missing types. */
  private final Set<DexType> missingTypes = Sets.newIdentityHashSet();

  /** Set of proto types that were found to be dead during the first round of tree shaking. */
  private Set<DexType> initialDeadProtoTypes = Sets.newIdentityHashSet();

  /** Set of types that were found to be missing during the first round of tree shaking. */
  private Set<DexType> initialMissingTypes;

  /** Set of types that was pruned during the first round of tree shaking. */
  private Set<DexType> initialPrunedTypes;

  /** Mapping from each unused interface to the set of live types that implements the interface. */
  private final Map<DexProgramClass, Set<DexProgramClass>> unusedInterfaceTypes =
      new IdentityHashMap<>();

  /**
   * Set of methods that are the immediate target of an invoke. They might not actually be live but
   * are required so that invokes can find the method. If a method is only a target but not live,
   * its implementation may be removed and it may be marked abstract.
   */
  private final SetWithReason<DexEncodedMethod> targetedMethods;

  /** Set of methods that have invalid resolutions or lookups. */
  private final Set<DexMethod> failedResolutionTargets;

  /**
   * Set of program methods that are used as the bootstrap method for an invoke-dynamic instruction.
   */
  private final Set<DexMethod> bootstrapMethods = Sets.newIdentityHashSet();
  /**
   * Set of direct methods that are the immediate target of an invoke-dynamic.
   */
  private final Set<DexMethod> methodsTargetedByInvokeDynamic = Sets.newIdentityHashSet();
  /**
   * Set of virtual methods that are the immediate target of an invoke-direct.
   */
  private final Set<DexMethod> virtualMethodsTargetedByInvokeDirect = Sets.newIdentityHashSet();
  /**
   * Set of methods that belong to live classes and can be reached by invokes. These need to be
   * kept.
   */
  private final LiveMethodsSet liveMethods;

  /**
   * Set of fields that belong to live classes and can be reached by invokes. These need to be kept.
   */
  private final LiveFieldsSet liveFields;

  /**
   * Set of service types (from META-INF/services/) that may have been instantiated reflectively via
   * ServiceLoader.load() or ServiceLoader.loadInstalled().
   */
  private final Set<DexType> instantiatedAppServices = Sets.newIdentityHashSet();

  /** A queue of items that need processing. Different items trigger different actions. */
  private final EnqueuerWorklist workList;

  /** A set of methods that need code inspection for Java reflection in use. */
  private final ProgramMethodSet pendingReflectiveUses = ProgramMethodSet.createLinked();

  /** Mapping of types to the methods reachable at that type. */
  private final Map<DexProgramClass, Set<DexMethod>> reachableVirtualTargets =
      new IdentityHashMap<>();

  /**
   * A set of references we have reported missing to dedupe warnings.
   */
  private final Set<DexReference> reportedMissing = Sets.newIdentityHashSet();

  /** Collection of keep requirements for the program. */
  private final MutableKeepInfoCollection keepInfo = new MutableKeepInfoCollection();

  /**
   * A set of seen const-class references that serve as an initial lock-candidate set and will
   * prevent class merging.
   */
  private final Set<DexType> lockCandidates = Sets.newIdentityHashSet();

  /**
   * A map from seen init-class references to the minimum required visibility of the corresponding
   * static field.
   */
  private final Map<DexType, Visibility> initClassReferences = new IdentityHashMap<>();

  /**
   * A map from annotation classes to annotations that need to be processed should the classes ever
   * become live.
   */
  private final Map<DexType, Set<DexAnnotation>> deferredAnnotations = new IdentityHashMap<>();

  /** Map of active if rules to speed up aapt2 generated keep rules. */
  private Map<Wrapper<ProguardIfRule>, Set<ProguardIfRule>> activeIfRules;

  /**
   * A cache of ScopedDexMethodSet for each live type used for determining that virtual methods that
   * cannot be removed because they are widening access for another virtual method defined earlier
   * in the type hierarchy. See b/136698023 for more information.
   */
  private final Map<DexType, ScopedDexMethodSet> scopedMethodsForLiveTypes =
      new IdentityHashMap<>();

  private final GraphReporter graphReporter;

  private final LambdaRewriter lambdaRewriter;
  private final BackportedMethodRewriter backportRewriter;
  private final DesugaredLibraryConversionWrapperAnalysis desugaredLibraryWrapperAnalysis;
  private final Map<DexType, Pair<LambdaClass, ProgramMethod>> lambdaClasses =
      new IdentityHashMap<>();
  private final Map<DexEncodedMethod, Map<DexCallSite, LambdaClass>> lambdaCallSites =
      new IdentityHashMap<>();
  private final Map<DexMethod, ProgramMethod> methodsWithBackports = new IdentityHashMap<>();
  private final Set<DexProgramClass> classesWithSerializableLambdas = Sets.newIdentityHashSet();

  Enqueuer(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      SubtypingInfo subtypingInfo,
      GraphConsumer keptGraphConsumer,
      Mode mode) {
    assert appView.appServices() != null;
    InternalOptions options = appView.options();
    this.appInfo = appView.appInfo();
    this.appView = appView.withClassHierarchy();
    this.subtypingInfo = subtypingInfo;
    this.forceProguardCompatibility = options.forceProguardCompatibility;
    this.graphReporter = new GraphReporter(appView, keptGraphConsumer);
    this.mode = mode;
    this.options = options;
    this.useRegistryFactory = createUseRegistryFactory();
    this.workList = EnqueuerWorklist.createWorklist(appView);

    if (mode.isInitialOrFinalTreeShaking()) {
      if (options.protoShrinking().enableGeneratedMessageLiteShrinking) {
        registerAnalysis(new ProtoEnqueuerExtension(appView));
      }
      appView.withGeneratedMessageLiteBuilderShrinker(
          shrinker -> registerAnalysis(shrinker.createEnqueuerAnalysis()));
    }

    targetedMethods = new SetWithReason<>(graphReporter::registerMethod);
    // This set is only populated in edge cases due to multiple default interface methods.
    // The set is generally expected to be empty and in the unlikely chance it is not, it will
    // likely contain two methods. Thus the default capacity of 2.
    failedResolutionTargets = SetUtils.newIdentityHashSet(2);
    liveMethods = new LiveMethodsSet(graphReporter::registerMethod);
    liveFields = new LiveFieldsSet(graphReporter::registerField);
    lambdaRewriter = options.desugarState == DesugarState.ON ? new LambdaRewriter(appView) : null;
    backportRewriter =
        options.desugarState == DesugarState.ON ? new BackportedMethodRewriter(appView) : null;

    objectAllocationInfoCollection =
        ObjectAllocationInfoCollectionImpl.builder(mode.isInitialTreeShaking(), graphReporter);

    if (appView.rewritePrefix.isRewriting() && mode.isInitialTreeShaking()) {
      desugaredLibraryWrapperAnalysis = new DesugaredLibraryConversionWrapperAnalysis(appView);
      registerAnalysis(desugaredLibraryWrapperAnalysis);
      registerInvokeAnalysis(desugaredLibraryWrapperAnalysis);
    } else {
      desugaredLibraryWrapperAnalysis = null;
    }

    enqueuerDefinitionSupplier = new EnqueuerDefinitionSupplier(appView, this);
  }

  private AppInfoWithClassHierarchy appInfo() {
    return appView.appInfo();
  }

  public Mode getMode() {
    return mode;
  }

  public GraphReporter getGraphReporter() {
    return graphReporter;
  }

  private EnqueuerUseRegistryFactory createUseRegistryFactory() {
    if (mode.isFinalTreeShaking()) {
      return appView.withGeneratedMessageLiteShrinker(
          ignore -> ProtoEnqueuerUseRegistry.getFactory(), DefaultEnqueuerUseRegistry::new);
    }
    return DefaultEnqueuerUseRegistry::new;
  }

  public EnqueuerUseRegistryFactory getUseRegistryFactory() {
    return useRegistryFactory;
  }

  public Enqueuer registerAnalysis(EnqueuerAnalysis analysis) {
    analyses.add(analysis);
    return this;
  }

  private Enqueuer registerInvokeAnalysis(EnqueuerInvokeAnalysis analysis) {
    invokeAnalyses.add(analysis);
    return this;
  }

  public Enqueuer registerInstanceOfAnalysis(EnqueuerInstanceOfAnalysis analysis) {
    instanceOfAnalyses.add(analysis);
    return this;
  }

  public Enqueuer registerCheckCastAnalysis(EnqueuerCheckCastAnalysis analysis) {
    checkCastAnalyses.add(analysis);
    return this;
  }

  public Enqueuer registerExceptionGuardAnalysis(EnqueuerExceptionGuardAnalysis analysis) {
    exceptionGuardAnalyses.add(analysis);
    return this;
  }

  public void setAnnotationRemoverBuilder(AnnotationRemover.Builder annotationRemoverBuilder) {
    this.annotationRemoverBuilder = annotationRemoverBuilder;
  }

  public void setInitialDeadProtoTypes(Set<DexType> initialDeadProtoTypes) {
    assert mode.isFinalTreeShaking();
    this.initialDeadProtoTypes = initialDeadProtoTypes;
  }

  public void setInitialMissingTypes(Set<DexType> initialMissingTypes) {
    assert mode.isFinalTreeShaking();
    this.initialMissingTypes = initialMissingTypes;
  }

  public void setInitialPrunedTypes(Set<DexType> initialPrunedTypes) {
    assert mode.isFinalTreeShaking();
    this.initialPrunedTypes = initialPrunedTypes;
  }

  public void addDeadProtoTypeCandidate(DexType type) {
    assert type.isProgramType(appView);
    addDeadProtoTypeCandidate(appView.definitionFor(type).asProgramClass());
  }

  public void addDeadProtoTypeCandidate(DexProgramClass clazz) {
    deadProtoTypeCandidates.add(clazz);
  }

  private boolean isProgramClass(DexType type) {
    return getProgramClassOrNull(type) != null;
  }

  private void recordReference(DexReference r) {
    if (r.isDexType()) {
      recordTypeReference(r.asDexType());
    } else if (r.isDexField()) {
      recordFieldReference(r.asDexField());
    } else {
      assert r.isDexMethod();
      recordMethodReference(r.asDexMethod());
    }
  }

  private void recordTypeReference(DexType type) {
    if (type == null) {
      return;
    }
    if (type.isArrayType()) {
      type = type.toBaseType(appView.dexItemFactory());
    }
    if (!type.isClassType()) {
      return;
    }
    // Lookup the definition, ignoring the result. This populates the missing and referenced sets.
    definitionFor(type);
  }

  private void recordMethodReference(DexMethod method) {
    recordTypeReference(method.holder);
    recordTypeReference(method.proto.returnType);
    for (DexType type : method.proto.parameters.values) {
      recordTypeReference(type);
    }
  }

  private void recordFieldReference(DexField field) {
    recordTypeReference(field.holder);
    recordTypeReference(field.type);
  }

  public DexEncodedMethod definitionFor(DexMethod method) {
    DexClass clazz = definitionFor(method.holder);
    if (clazz == null) {
      return null;
    }
    return clazz.lookupMethod(method);
  }

  private DexClass definitionFor(DexType type) {
    return internalDefinitionFor(type, false);
  }

  private DexClass definitionForFromReflectiveAccess(DexType type) {
    return internalDefinitionFor(type, true);
  }

  private DexClass internalDefinitionFor(DexType type, boolean fromReflectiveAccess) {
    DexClass clazz =
        fromReflectiveAccess
            ? appView.appInfo().definitionForWithoutExistenceAssert(type)
            : appView.appInfo().definitionFor(type);
    if (clazz == null) {
      if (!fromReflectiveAccess) {
        reportMissingClass(type);
      }
      return null;
    }
    if (clazz.isNotProgramClass()) {
      addLiveNonProgramType(clazz);
    }
    return clazz;
  }

  public boolean isPinned(DexType type) {
    return keepInfo.isPinned(type, appInfo);
  }

  private void addLiveNonProgramType(DexClass clazz) {
    assert clazz.isNotProgramClass();
    // Fast path to avoid the worklist when the class is already seen.
    if (!liveNonProgramTypes.add(clazz)) {
      return;
    }
    Deque<DexClass> worklist = new ArrayDeque<>();
    worklist.addLast(clazz);
    while (!worklist.isEmpty()) {
      DexClass definition = worklist.removeFirst();
      processNewLiveNonProgramType(definition, worklist);
    }
  }

  private void processNewLiveNonProgramType(DexClass clazz, Deque<DexClass> worklist) {
    assert clazz.isNotProgramClass();
    if (clazz.isLibraryClass()) {
      // TODO(b/149201735): This likely needs to apply to classpath too.
      ensureMethodsContinueToWidenAccess(clazz);
      // Only libraries must not derive program. Classpath classes can, assuming correct keep rules.
      warnIfLibraryTypeInheritsFromProgramType(clazz.asLibraryClass());
    }
    for (DexEncodedField field : clazz.fields()) {
      addNonProgramClassToWorklist(field.field.type, worklist);
    }
    for (DexEncodedMethod method : clazz.methods()) {
      addNonProgramClassToWorklist(method.method.proto.returnType, worklist);
      for (DexType param : method.method.proto.parameters.values) {
        addNonProgramClassToWorklist(param, worklist);
      }
    }
    for (DexType supertype : clazz.allImmediateSupertypes()) {
      addNonProgramClassToWorklist(supertype, worklist);
    }
  }

  private void addNonProgramClassToWorklist(DexType type, Deque<DexClass> worklist) {
    if (type.isArrayType()) {
      type = type.toBaseType(appView.dexItemFactory());
    }
    if (!type.isClassType()) {
      return;
    }
    DexClass definition = appView.definitionFor(type);
    if (definition == null) {
      reportMissingClass(type);
      return;
    }
    if (definition.isProgramClass() || !liveNonProgramTypes.add(definition)) {
      return;
    }
    worklist.addLast(definition);
  }

  private DexProgramClass getProgramClassOrNull(DexType type) {
    DexClass clazz = definitionFor(type);
    return clazz != null && clazz.isProgramClass() ? clazz.asProgramClass() : null;
  }

  private DexProgramClass getProgramClassOrNullFromReflectiveAccess(DexType type) {
    // To avoid that we report reflectively accessed types as missing.
    DexClass clazz = definitionForFromReflectiveAccess(type);
    return clazz != null && clazz.isProgramClass() ? clazz.asProgramClass() : null;
  }

  private void warnIfLibraryTypeInheritsFromProgramType(DexLibraryClass clazz) {
    if (clazz.superType != null) {
      ensureFromLibraryOrThrow(clazz.superType, clazz);
    }
    for (DexType iface : clazz.interfaces.values) {
      ensureFromLibraryOrThrow(iface, clazz);
    }
  }

  private void enqueueRootItems(Map<DexReference, Set<ProguardKeepRuleBase>> items) {
    items.entrySet().forEach(this::enqueueRootItem);
  }

  private void enqueueRootItems(ItemsWithRules items) {
    items.forEachField(this::enqueueRootField);
    items.forEachMethod(this::enqueueRootMethod);
    items.forEachClass(this::enqueueRootClass);
  }

  private void enqueueRootItem(Entry<DexReference, Set<ProguardKeepRuleBase>> root) {
    DexReference reference = root.getKey();
    Set<ProguardKeepRuleBase> rules = root.getValue();
    if (reference.isDexField()) {
      enqueueRootField(reference.asDexField(), rules);
    } else if (reference.isDexMethod()) {
      enqueueRootMethod(reference.asDexMethod(), rules);
    } else if (reference.isDexType()) {
      enqueueRootClass(reference.asDexType(), rules);
    } else {
      throw new Unreachable();
    }
  }

  // TODO(b/123923324): Verify that root items are present.
  private void enqueueRootClass(DexType type, Set<ProguardKeepRuleBase> rules) {
    DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(type));
    if (clazz != null) {
      enqueueRootClass(clazz, rules, null);
    }
  }

  private void enqueueRootClass(
      DexProgramClass clazz, Set<ProguardKeepRuleBase> rules, DexDefinition precondition) {
    keepClassWithRules(clazz, rules);
    enqueueKeepRuleInstantiatedType(clazz, rules, precondition);
  }

  private void enqueueKeepRuleInstantiatedType(
      DexProgramClass clazz, Set<ProguardKeepRuleBase> rules, DexDefinition precondition) {
    KeepReasonWitness witness = graphReporter.reportKeepClass(precondition, rules, clazz);
    if (clazz.isAnnotation()) {
      workList.enqueueMarkAnnotationInstantiatedAction(clazz, witness);
    } else if (clazz.isInterface()) {
      workList.enqueueMarkInterfaceInstantiatedAction(clazz, witness);
    } else {
      workList.enqueueMarkInstantiatedAction(clazz, null, InstantiationReason.KEEP_RULE, witness);
      if (clazz.hasDefaultInitializer()) {
        ProgramMethod defaultInitializer = clazz.getProgramDefaultInitializer();
        if (forceProguardCompatibility) {
          workList.enqueueMarkMethodKeptAction(
              defaultInitializer,
              graphReporter.reportCompatKeepDefaultInitializer(defaultInitializer));
        }
        if (clazz.isExternalizable(appView)) {
          enqueueMarkMethodLiveAction(defaultInitializer, witness);
        }
      }
    }
  }

  // TODO(b/123923324): Verify that root items are present.
  private void enqueueRootField(DexField reference, Set<ProguardKeepRuleBase> rules) {
    DexProgramClass holder = getProgramClassOrNull(reference.holder);
    if (holder != null) {
      ProgramField field = holder.lookupProgramField(reference);
      if (field != null) {
        enqueueRootField(field, rules, null);
      }
    }
  }

  private void enqueueRootField(
      ProgramField field, Set<ProguardKeepRuleBase> rules, DexDefinition precondition) {
    keepFieldWithRules(field, rules);
    workList.enqueueMarkFieldKeptAction(
        field, graphReporter.reportKeepField(precondition, rules, field.getDefinition()));
  }

  // TODO(b/123923324): Verify that root items are present.
  private void enqueueRootMethod(DexMethod reference, Set<ProguardKeepRuleBase> rules) {
    DexProgramClass holder = getProgramClassOrNull(reference.holder);
    if (holder != null) {
      ProgramMethod method = holder.lookupProgramMethod(reference);
      if (method != null) {
        enqueueRootMethod(method, rules, null);
      }
    }
  }

  private void enqueueRootMethod(
      ProgramMethod method, Set<ProguardKeepRuleBase> rules, DexDefinition precondition) {
    keepMethodWithRules(method, rules);
    workList.enqueueMarkMethodKeptAction(
        method, graphReporter.reportKeepMethod(precondition, rules, method.getDefinition()));
  }

  private void enqueueRootItem(DexDefinition item, Set<ProguardKeepRuleBase> rules) {
    internalEnqueueRootItem(item, rules, null);
  }

  private void internalEnqueueRootItem(
      DexDefinition item, Set<ProguardKeepRuleBase> rules, DexDefinition precondition) {
    if (item.isDexClass()) {
      DexProgramClass clazz = item.asDexClass().asProgramClass();
      if (clazz != null) {
        enqueueRootClass(clazz, rules, precondition);
      }
    } else if (item.isDexEncodedField()) {
      DexEncodedField field = item.asDexEncodedField();
      DexProgramClass holder = getProgramClassOrNull(field.holder());
      if (holder != null) {
        enqueueRootField(new ProgramField(holder, field), rules, precondition);
      }
    } else if (item.isDexEncodedMethod()) {
      DexEncodedMethod method = item.asDexEncodedMethod();
      DexProgramClass holder = getProgramClassOrNull(method.holder());
      if (holder != null) {
        enqueueRootMethod(new ProgramMethod(holder, method), rules, precondition);
      }
    } else {
      throw new IllegalArgumentException(item.toString());
    }
  }

  private void enqueueFirstNonSerializableClassInitializer(
      DexProgramClass clazz, KeepReason reason) {
    assert clazz.isSerializable(appView);
    // Climb up the class hierarchy. Break out if the definition is not found, or hit the library
    // classes which are kept by definition, or encounter the first non-serializable class.
    while (clazz.isSerializable(appView)) {
      DexProgramClass superClass = getProgramClassOrNull(clazz.superType);
      if (superClass == null) {
        return;
      }
      clazz = superClass;
    }
    if (clazz.hasDefaultInitializer()) {
      enqueueMarkMethodLiveAction(clazz.getProgramDefaultInitializer(), reason);
    }
  }

  // Utility to avoid adding to the worklist if already live.
  private boolean enqueueMarkMethodLiveAction(ProgramMethod method, KeepReason reason) {
    if (liveMethods.add(method, reason)) {
      workList.enqueueMarkMethodLiveAction(method, reason);
      return true;
    }
    return false;
  }

  private void compatEnqueueHolderIfDependentNonStaticMember(
      DexProgramClass holder, Set<ProguardKeepRuleBase> compatRules) {
    if (!forceProguardCompatibility || compatRules == null) {
      return;
    }
    // TODO(b/120959039): This needs the set of instance member as preconditon.
    enqueueKeepRuleInstantiatedType(holder, compatRules, null);
  }

  //
  // Things to do with registering events. This is essentially the interface for byte-code
  // traversals.
  //

  private boolean registerMethodWithTargetAndContext(
      BiPredicate<DexMethod, ProgramMethod> registration, DexMethod method, ProgramMethod context) {
    DexType baseHolder = method.holder.toBaseType(appView.dexItemFactory());
    if (baseHolder.isClassType()) {
      markTypeAsLive(baseHolder, clazz -> graphReporter.reportClassReferencedFrom(clazz, context));
      return registration.test(method, context);
    }
    return false;
  }

  public boolean registerFieldRead(DexField field, ProgramMethod context) {
    return registerFieldAccess(field, context, true, false);
  }

  public boolean registerReflectiveFieldRead(DexField field, ProgramMethod context) {
    return registerFieldAccess(field, context, true, true);
  }

  public boolean registerFieldWrite(DexField field, ProgramMethod context) {
    return registerFieldAccess(field, context, false, false);
  }

  public boolean registerReflectiveFieldWrite(DexField field, ProgramMethod context) {
    return registerFieldAccess(field, context, false, true);
  }

  public boolean registerReflectiveFieldAccess(DexField field, ProgramMethod context) {
    boolean changed = registerFieldAccess(field, context, true, true);
    changed |= registerFieldAccess(field, context, false, true);
    return changed;
  }

  private boolean registerFieldAccess(
      DexField field, ProgramMethod context, boolean isRead, boolean isReflective) {
    FieldAccessInfoImpl info = fieldAccessInfoCollection.get(field);
    if (info == null) {
      DexEncodedField encodedField = resolveField(field).getResolvedField();

      // If the field does not exist, then record this in the mapping, such that we don't have to
      // resolve the field the next time.
      if (encodedField == null) {
        fieldAccessInfoCollection.extend(field, MISSING_FIELD_ACCESS_INFO);
        return true;
      }

      // Check if we have previously created a FieldAccessInfo object for the field definition.
      info = fieldAccessInfoCollection.get(encodedField.field);

      // If not, we must create one.
      if (info == null) {
        info = new FieldAccessInfoImpl(encodedField.field);
        fieldAccessInfoCollection.extend(encodedField.field, info);
      }

      // If `field` is an indirect reference, then create a mapping for it, such that we don't have
      // to resolve the field the next time we see the reference.
      if (field != encodedField.field) {
        fieldAccessInfoCollection.extend(field, info);
      }
    } else if (info == MISSING_FIELD_ACCESS_INFO) {
      return false;
    }
    if (isReflective) {
      info.setHasReflectiveAccess();
    }
    return isRead ? info.recordRead(field, context) : info.recordWrite(field, context);
  }

  void traceCallSite(DexCallSite callSite, ProgramMethod context) {
    DexProgramClass bootstrapClass =
        getProgramClassOrNull(callSite.bootstrapMethod.asMethod().holder);
    if (bootstrapClass != null) {
      bootstrapMethods.add(callSite.bootstrapMethod.asMethod());
    }

    LambdaDescriptor descriptor = LambdaDescriptor.tryInfer(callSite, appInfo, context);
    if (descriptor == null) {
      return;
    }

    DexEncodedMethod contextMethod = context.getDefinition();
    if (lambdaRewriter != null) {
      assert contextMethod.getCode().isCfCode() : "Unexpected input type with lambdas";
      CfCode code = contextMethod.getCode().asCfCode();
      if (code != null) {
        LambdaClass lambdaClass = lambdaRewriter.getOrCreateLambdaClass(descriptor, context);
        lambdaClasses.put(lambdaClass.type, new Pair<>(lambdaClass, context));
        lambdaCallSites
            .computeIfAbsent(contextMethod, k -> new IdentityHashMap<>())
            .put(callSite, lambdaClass);
        if (lambdaClass.descriptor.interfaces.contains(appView.dexItemFactory().serializableType)) {
          classesWithSerializableLambdas.add(context.getHolder());
        }
      }
    } else {
      markLambdaAsInstantiated(descriptor, context);
      transitionMethodsForInstantiatedLambda(descriptor);
      callSites.computeIfAbsent(callSite, ignore -> ProgramMethodSet.create()).add(context);
    }

    // For call sites representing a lambda, we link the targeted method
    // or field as if it were referenced from the current method.

    DexMethodHandle implHandle = descriptor.implHandle;
    assert implHandle != null;

    DexMethod method = implHandle.asMethod();
    if (!methodsTargetedByInvokeDynamic.add(method)) {
      return;
    }

    switch (implHandle.type) {
      case INVOKE_STATIC:
        traceInvokeStaticFromLambda(method, context);
        break;
      case INVOKE_INTERFACE:
        traceInvokeInterfaceFromLambda(method, context);
        break;
      case INVOKE_INSTANCE:
        traceInvokeVirtualFromLambda(method, context);
        break;
      case INVOKE_DIRECT:
        traceInvokeDirectFromLambda(method, context);
        break;
      case INVOKE_CONSTRUCTOR:
        traceNewInstanceFromLambda(method.holder, context);
        break;
      default:
        throw new Unreachable();
    }
  }

  void traceCheckCast(DexType type, ProgramMethod currentMethod) {
    checkCastAnalyses.forEach(analysis -> analysis.traceCheckCast(type, currentMethod));
    traceConstClassOrCheckCast(type, currentMethod);
  }

  void traceConstClass(
      DexType type,
      ProgramMethod currentMethod,
      ListIterator<? extends CfOrDexInstruction> iterator) {
    handleLockCandidate(type, currentMethod, iterator);
    traceConstClassOrCheckCast(type, currentMethod);
  }

  private void handleLockCandidate(
      DexType type,
      ProgramMethod currentMethod,
      ListIterator<? extends CfOrDexInstruction> iterator) {
    // We conservatively group T.class and T[].class to ensure that we do not merge T with S if
    // potential locks on T[].class and S[].class exists.
    DexType baseType = type.toBaseType(appView.dexItemFactory());
    if (baseType.isClassType()) {
      DexProgramClass baseClass = getProgramClassOrNull(baseType);
      if (baseClass != null && isConstClassMaybeUsedAsLock(currentMethod, iterator)) {
        lockCandidates.add(baseType);
      }
    }
  }

  /**
   * Returns true if the const-class value may flow into a monitor instruction.
   *
   * <p>Some common usages of const-class values are handled, such as calls to Class.get*Name().
   */
  private boolean isConstClassMaybeUsedAsLock(
      ProgramMethod currentMethod, ListIterator<? extends CfOrDexInstruction> iterator) {
    if (iterator == null) {
      return true;
    }
    boolean result = true;
    if (currentMethod.getDefinition().getCode().isCfCode()) {
      CfInstruction nextInstruction =
          IteratorUtils.nextUntil(
                  iterator,
                  instruction ->
                      !instruction.asCfInstruction().isLabel()
                          && !instruction.asCfInstruction().isPosition())
              .asCfInstruction();
      assert nextInstruction != null;
      if (nextInstruction.isInvoke()) {
        CfInvoke invoke = nextInstruction.asInvoke();
        DexMethod invokedMethod = invoke.getMethod();
        ClassMethods classMethods = appView.dexItemFactory().classMethods;
        if (classMethods.isReflectiveNameLookup(invokedMethod)
            || invokedMethod == classMethods.desiredAssertionStatus
            || invokedMethod == classMethods.getClassLoader
            || invokedMethod == classMethods.getPackage) {
          result = false;
        }
      }
      iterator.previous();
    }
    return result;
  }

  private void traceConstClassOrCheckCast(DexType type, ProgramMethod currentMethod) {
    if (!forceProguardCompatibility) {
      traceTypeReference(type, currentMethod);
      return;
    }
    DexType baseType = type.toBaseType(appView.dexItemFactory());
    if (baseType.isClassType()) {
      DexProgramClass baseClass = getProgramClassOrNull(baseType);
      if (baseClass != null) {
        // Don't require any constructor, see b/112386012.
        markClassAsInstantiatedWithCompatRule(
            baseClass, graphReporter.reportCompatInstantiated(baseClass, currentMethod));
      }
    }
  }

  void traceInitClass(DexType type, ProgramMethod currentMethod) {
    assert type.isClassType();

    Visibility oldMinimumRequiredVisibility = initClassReferences.get(type);
    if (oldMinimumRequiredVisibility == null) {
      DexProgramClass clazz = getProgramClassOrNull(type);
      if (clazz == null) {
        assert false;
        return;
      }

      initClassReferences.put(
          type, computeMinimumRequiredVisibilityForInitClassField(type, currentMethod.getHolder()));

      markTypeAsLive(type, classReferencedFromReporter(currentMethod));
      markDirectAndIndirectClassInitializersAsLive(clazz);
      return;
    }

    if (oldMinimumRequiredVisibility.isPublic()) {
      return;
    }

    Visibility minimumRequiredVisibilityForCurrentMethod =
        computeMinimumRequiredVisibilityForInitClassField(type, currentMethod.getHolder());

    // There should never be a need to have an InitClass instruction for the enclosing class.
    assert !minimumRequiredVisibilityForCurrentMethod.isPrivate();

    if (minimumRequiredVisibilityForCurrentMethod.isPublic()) {
      initClassReferences.put(type, minimumRequiredVisibilityForCurrentMethod);
      return;
    }

    if (oldMinimumRequiredVisibility.isProtected()) {
      return;
    }

    if (minimumRequiredVisibilityForCurrentMethod.isProtected()) {
      initClassReferences.put(type, minimumRequiredVisibilityForCurrentMethod);
      return;
    }

    assert oldMinimumRequiredVisibility.isPackagePrivate();
    assert minimumRequiredVisibilityForCurrentMethod.isPackagePrivate();
  }

  private Visibility computeMinimumRequiredVisibilityForInitClassField(
      DexType clazz, DexProgramClass context) {
    if (clazz.isSamePackage(context.type)) {
      return Visibility.PACKAGE_PRIVATE;
    }
    if (appInfo.isStrictSubtypeOf(context.type, clazz)) {
      return Visibility.PROTECTED;
    }
    return Visibility.PUBLIC;
  }

  void traceMethodHandle(
      DexMethodHandle methodHandle, MethodHandleUse use, ProgramMethod currentMethod) {
    // If a method handle is not an argument to a lambda metafactory it could flow to a
    // MethodHandle.invokeExact invocation. For that to work, the receiver type cannot have
    // changed and therefore we cannot perform member rebinding. For these handles, we maintain
    // the receiver for the method handle. Therefore, we have to make sure that the receiver
    // stays in the output (and is not class merged). To ensure that we treat the receiver
    // as instantiated.
    if (methodHandle.isMethodHandle() && use != MethodHandleUse.ARGUMENT_TO_LAMBDA_METAFACTORY) {
      DexType type = methodHandle.asMethod().holder;
      DexProgramClass clazz = getProgramClassOrNull(type);
      if (clazz != null) {
        KeepReason reason = KeepReason.methodHandleReferencedIn(currentMethod);
        if (clazz.isAnnotation()) {
          markTypeAsLive(clazz, graphReporter.registerClass(clazz, reason));
        } else if (clazz.isInterface()) {
          markInterfaceAsInstantiated(clazz, graphReporter.registerInterface(clazz, reason));
        } else {
          workList.enqueueMarkInstantiatedAction(
              clazz, null, InstantiationReason.REFERENCED_IN_METHOD_HANDLE, reason);
        }
      }
    }
  }

  void traceTypeReference(DexType type, ProgramMethod currentMethod) {
    markTypeAsLive(type, classReferencedFromReporter(currentMethod));
  }

  void traceInstanceOf(DexType type, ProgramMethod currentMethod) {
    instanceOfAnalyses.forEach(analysis -> analysis.traceInstanceOf(type, currentMethod));
    traceTypeReference(type, currentMethod);
  }

  void traceExceptionGuard(DexType guard, ProgramMethod currentMethod) {
    exceptionGuardAnalyses.forEach(analysis -> analysis.traceExceptionGuard(guard, currentMethod));
    traceTypeReference(guard, currentMethod);
  }

  void traceInvokeDirect(DexMethod invokedMethod, ProgramMethod context) {
    boolean skipTracing =
        registerDeferredActionForDeadProtoBuilder(
            invokedMethod.holder,
            context,
            () -> workList.enqueueTraceInvokeDirectAction(invokedMethod, context));
    if (skipTracing) {
      addDeadProtoTypeCandidate(invokedMethod.holder);
      return;
    }

    traceInvokeDirect(invokedMethod, context, KeepReason.invokedFrom(context));
  }

  /** Returns true if a deferred action was registered. */
  private boolean registerDeferredActionForDeadProtoBuilder(
      DexType type, ProgramMethod currentMethod, Action action) {
    DexProgramClass clazz = getProgramClassOrNull(type);
    if (clazz != null) {
      return appView.withGeneratedMessageLiteBuilderShrinker(
          shrinker ->
              shrinker.deferDeadProtoBuilders(
                  clazz, currentMethod, () -> liveTypes.registerDeferredAction(clazz, action)),
          false);
    }
    return false;
  }

  void traceInvokeDirectFromLambda(DexMethod invokedMethod, ProgramMethod context) {
    traceInvokeDirect(invokedMethod, context, KeepReason.invokedFromLambdaCreatedIn(context));
  }

  private void traceInvokeDirect(
      DexMethod invokedMethod, ProgramMethod context, KeepReason reason) {
    if (registerBackportInvoke(invokedMethod, context)) {
      return;
    }

    if (!registerMethodWithTargetAndContext(
        methodAccessInfoCollection::registerInvokeDirectInContext, invokedMethod, context)) {
      return;
    }
    if (Log.ENABLED) {
      Log.verbose(getClass(), "Register invokeDirect `%s`.", invokedMethod);
    }
    handleInvokeOfDirectTarget(invokedMethod, reason);
    invokeAnalyses.forEach(analysis -> analysis.traceInvokeDirect(invokedMethod, context));
  }

  void traceInvokeInterface(DexMethod invokedMethod, ProgramMethod context) {
    traceInvokeInterface(invokedMethod, context, KeepReason.invokedFrom(context));
  }

  void traceInvokeInterfaceFromLambda(DexMethod invokedMethod, ProgramMethod context) {
    traceInvokeInterface(invokedMethod, context, KeepReason.invokedFromLambdaCreatedIn(context));
  }

  private void traceInvokeInterface(
      DexMethod method, ProgramMethod context, KeepReason keepReason) {
    if (registerBackportInvoke(method, context)) {
      return;
    }

    if (!registerMethodWithTargetAndContext(
        methodAccessInfoCollection::registerInvokeInterfaceInContext, method, context)) {
      return;
    }
    if (Log.ENABLED) {
      Log.verbose(getClass(), "Register invokeInterface `%s`.", method);
    }
    markVirtualMethodAsReachable(method, true, context, keepReason);
    invokeAnalyses.forEach(analysis -> analysis.traceInvokeInterface(method, context));
  }

  void traceInvokeStatic(DexMethod invokedMethod, ProgramMethod context) {
    traceInvokeStatic(invokedMethod, context, KeepReason.invokedFrom(context));
  }

  void traceInvokeStaticFromLambda(DexMethod invokedMethod, ProgramMethod context) {
    traceInvokeStatic(invokedMethod, context, KeepReason.invokedFromLambdaCreatedIn(context));
  }

  private boolean registerBackportInvoke(DexMethod invokedMethod, ProgramMethod context) {
    if (backportRewriter != null && backportRewriter.needsDesugaring(invokedMethod)) {
      methodsWithBackports.putIfAbsent(context.getReference(), context);
      return true;
    }
    return false;
  }

  private void traceInvokeStatic(
      DexMethod invokedMethod, ProgramMethod context, KeepReason reason) {
    if (registerBackportInvoke(invokedMethod, context)) {
      return;
    }

    DexItemFactory dexItemFactory = appView.dexItemFactory();
    if (dexItemFactory.classMethods.isReflectiveClassLookup(invokedMethod)
        || dexItemFactory.atomicFieldUpdaterMethods.isFieldUpdater(invokedMethod)) {
      // Implicitly add -identifiernamestring rule for the Java reflection in use.
      identifierNameStrings.add(invokedMethod);
      // Revisit the current method to implicitly add -keep rule for items with reflective access.
      pendingReflectiveUses.add(context);
    }
    // See comment in handleJavaLangEnumValueOf.
    if (invokedMethod == dexItemFactory.enumMembers.valueOf) {
      pendingReflectiveUses.add(context);
    }
    // Handling of application services.
    if (dexItemFactory.serviceLoaderMethods.isLoadMethod(invokedMethod)) {
      pendingReflectiveUses.add(context);
    }
    if (invokedMethod == dexItemFactory.proxyMethods.newProxyInstance) {
      pendingReflectiveUses.add(context);
    }
    if (!registerMethodWithTargetAndContext(
        methodAccessInfoCollection::registerInvokeStaticInContext, invokedMethod, context)) {
      return;
    }
    if (Log.ENABLED) {
      Log.verbose(getClass(), "Register invokeStatic `%s`.", invokedMethod);
    }
    handleInvokeOfStaticTarget(invokedMethod, reason);
    invokeAnalyses.forEach(analysis -> analysis.traceInvokeStatic(invokedMethod, context));
  }

  void traceInvokeSuper(DexMethod invokedMethod, ProgramMethod context) {
    if (registerBackportInvoke(invokedMethod, context)) {
      return;
    }
    // We have to revisit super invokes based on the context they are found in. The same
    // method descriptor will hit different targets, depending on the context it is used in.
    DexMethod actualTarget = getInvokeSuperTarget(invokedMethod, context);
    if (!registerMethodWithTargetAndContext(
        methodAccessInfoCollection::registerInvokeSuperInContext, invokedMethod, context)) {
      return;
    }
    if (Log.ENABLED) {
      Log.verbose(getClass(), "Register invokeSuper `%s`.", actualTarget);
    }
    workList.enqueueMarkReachableSuperAction(invokedMethod, context);
    invokeAnalyses.forEach(analysis -> analysis.traceInvokeSuper(invokedMethod, context));
  }

  void traceInvokeVirtual(DexMethod invokedMethod, ProgramMethod context) {
    traceInvokeVirtual(invokedMethod, context, KeepReason.invokedFrom(context));
  }

  void traceInvokeVirtualFromLambda(DexMethod invokedMethod, ProgramMethod context) {
    traceInvokeVirtual(invokedMethod, context, KeepReason.invokedFromLambdaCreatedIn(context));
  }

  private void traceInvokeVirtual(
      DexMethod invokedMethod, ProgramMethod context, KeepReason reason) {
    if (registerBackportInvoke(invokedMethod, context)) {
      return;
    }

    if (invokedMethod == appView.dexItemFactory().classMethods.newInstance
        || invokedMethod == appView.dexItemFactory().constructorMethods.newInstance) {
      pendingReflectiveUses.add(context);
    } else if (appView.dexItemFactory().classMethods.isReflectiveMemberLookup(invokedMethod)) {
      // Implicitly add -identifiernamestring rule for the Java reflection in use.
      identifierNameStrings.add(invokedMethod);
      // Revisit the current method to implicitly add -keep rule for items with reflective access.
      pendingReflectiveUses.add(context);
    }
    if (!registerMethodWithTargetAndContext(
        methodAccessInfoCollection::registerInvokeVirtualInContext, invokedMethod, context)) {
      return;
    }
    if (Log.ENABLED) {
      Log.verbose(getClass(), "Register invokeVirtual `%s`.", invokedMethod);
    }
    markVirtualMethodAsReachable(invokedMethod, false, context, reason);
    invokeAnalyses.forEach(analysis -> analysis.traceInvokeVirtual(invokedMethod, context));
  }

  void traceNewInstance(DexType type, ProgramMethod context) {
    boolean skipTracing =
        registerDeferredActionForDeadProtoBuilder(
            type, context, () -> workList.enqueueTraceNewInstanceAction(type, context));
    if (skipTracing) {
      addDeadProtoTypeCandidate(type);
      return;
    }

    traceNewInstance(
        type,
        context,
        InstantiationReason.NEW_INSTANCE_INSTRUCTION,
        KeepReason.instantiatedIn(context));
  }

  void traceNewInstanceFromLambda(DexType type, ProgramMethod context) {
    traceNewInstance(
        type, context, InstantiationReason.LAMBDA, KeepReason.invokedFromLambdaCreatedIn(context));
  }

  private void traceNewInstance(
      DexType type,
      ProgramMethod context,
      InstantiationReason instantiationReason,
      KeepReason keepReason) {
    DexProgramClass clazz = getProgramClassOrNull(type);
    if (clazz != null) {
      if (clazz.isAnnotation() || clazz.isInterface()) {
        markTypeAsLive(clazz, graphReporter.registerClass(clazz, keepReason));
      } else {
        workList.enqueueMarkInstantiatedAction(clazz, context, instantiationReason, keepReason);
      }
    }
  }

  void traceInstanceFieldRead(DexField field, ProgramMethod currentMethod) {
    traceInstanceFieldRead(field, currentMethod, false);
  }

  void traceInstanceFieldReadFromMethodHandle(DexField field, ProgramMethod currentMethod) {
    traceInstanceFieldRead(field, currentMethod, true);
  }

  private void traceInstanceFieldRead(
      DexField fieldReference, ProgramMethod currentMethod, boolean fromMethodHandle) {
    if (!registerFieldRead(fieldReference, currentMethod)) {
      return;
    }

    // Must mark the field as targeted even if it does not exist.
    markFieldAsTargeted(fieldReference, currentMethod);

    FieldResolutionResult resolutionResult = resolveField(fieldReference);
    if (resolutionResult.isFailedOrUnknownResolution()) {
      return;
    }

    ProgramField field =
        resolutionResult.asSuccessfulResolution().getResolutionPair().asProgramField();
    if (field == null) {
      // No need to trace into the non-program code.
      return;
    }

    if (fromMethodHandle) {
      fieldAccessInfoCollection.get(field.getReference()).setReadFromMethodHandle();
    }

    if (Log.ENABLED) {
      Log.verbose(getClass(), "Register Iget `%s`.", fieldReference);
    }

    // If unused interface removal is enabled, then we won't necessarily mark the actual holder of
    // the field as live, if the holder is an interface.
    if (appView.options().enableUnusedInterfaceRemoval) {
      if (field.getReference() != fieldReference) {
        markTypeAsLive(
            field.getHolder(),
            graphReporter.reportClassReferencedFrom(field.getHolder(), currentMethod));
      }
    }

    workList.enqueueMarkReachableFieldAction(field, KeepReason.fieldReferencedIn(currentMethod));
  }

  void traceInstanceFieldWrite(DexField field, ProgramMethod currentMethod) {
    traceInstanceFieldWrite(field, currentMethod, false);
  }

  void traceInstanceFieldWriteFromMethodHandle(DexField field, ProgramMethod currentMethod) {
    traceInstanceFieldWrite(field, currentMethod, true);
  }

  private void traceInstanceFieldWrite(
      DexField fieldReference, ProgramMethod currentMethod, boolean fromMethodHandle) {
    if (!registerFieldWrite(fieldReference, currentMethod)) {
      return;
    }

    // Must mark the field as targeted even if it does not exist.
    markFieldAsTargeted(fieldReference, currentMethod);

    FieldResolutionResult resolutionResult = resolveField(fieldReference);
    if (resolutionResult.isFailedOrUnknownResolution()) {
      return;
    }

    ProgramField field =
        resolutionResult.asSuccessfulResolution().getResolutionPair().asProgramField();
    if (field == null) {
      // No need to trace into the non-program code.
      return;
    }

    if (fromMethodHandle) {
      fieldAccessInfoCollection.get(field.getReference()).setWrittenFromMethodHandle();
    }

    if (Log.ENABLED) {
      Log.verbose(getClass(), "Register Iput `%s`.", fieldReference);
    }

    // If unused interface removal is enabled, then we won't necessarily mark the actual holder of
    // the field as live, if the holder is an interface.
    if (appView.options().enableUnusedInterfaceRemoval) {
      if (field.getReference() != fieldReference) {
        markTypeAsLive(
            field.getHolder(),
            graphReporter.reportClassReferencedFrom(field.getHolder(), currentMethod));
      }
    }

    KeepReason reason = KeepReason.fieldReferencedIn(currentMethod);
    workList.enqueueMarkReachableFieldAction(field, reason);
  }

  void traceStaticFieldRead(DexField field, ProgramMethod currentMethod) {
    traceStaticFieldRead(field, currentMethod, false);
  }

  void traceStaticFieldReadFromMethodHandle(DexField field, ProgramMethod currentMethod) {
    traceStaticFieldRead(field, currentMethod, true);
  }

  private void traceStaticFieldRead(
      DexField fieldReference, ProgramMethod currentMethod, boolean fromMethodHandle) {
    if (!registerFieldRead(fieldReference, currentMethod)) {
      return;
    }

    FieldResolutionResult resolutionResult = resolveField(fieldReference);
    if (resolutionResult.isFailedOrUnknownResolution()) {
      // Must mark the field as targeted even if it does not exist.
      markFieldAsTargeted(fieldReference, currentMethod);
      return;
    }

    ProgramField field =
        resolutionResult.asSuccessfulResolution().getResolutionPair().asProgramField();
    if (field == null) {
      // No need to trace into the non-program code.
      return;
    }

    if (fromMethodHandle) {
      fieldAccessInfoCollection.get(field.getReference()).setReadFromMethodHandle();
    }

    if (Log.ENABLED) {
      Log.verbose(getClass(), "Register Sget `%s`.", fieldReference);
    }

    if (appView.options().protoShrinking().enableGeneratedExtensionRegistryShrinking) {
      // If it is a dead proto extension field, don't trace onwards.
      boolean skipTracing =
          appView.withGeneratedExtensionRegistryShrinker(
              shrinker ->
                  shrinker.isDeadProtoExtensionField(field, fieldAccessInfoCollection, keepInfo),
              false);
      if (skipTracing) {
        addDeadProtoTypeCandidate(field.getHolder());
        return;
      }
    }

    if (field.getReference() != fieldReference) {
      // Mark the non-rebound field access as targeted. Note that this should only be done if the
      // field is not a dead proto field (in which case we bail-out above).
      markFieldAsTargeted(fieldReference, currentMethod);
    }

    markStaticFieldAsLive(field, KeepReason.fieldReferencedIn(currentMethod));
  }

  void traceStaticFieldWrite(DexField field, ProgramMethod currentMethod) {
    traceStaticFieldWrite(field, currentMethod, false);
  }

  void traceStaticFieldWriteFromMethodHandle(DexField field, ProgramMethod currentMethod) {
    traceStaticFieldWrite(field, currentMethod, true);
  }

  private void traceStaticFieldWrite(
      DexField fieldReference, ProgramMethod currentMethod, boolean fromMethodHandle) {
    if (!registerFieldWrite(fieldReference, currentMethod)) {
      return;
    }

    FieldResolutionResult resolutionResult = resolveField(fieldReference);
    if (resolutionResult.isFailedOrUnknownResolution()) {
      // Must mark the field as targeted even if it does not exist.
      markFieldAsTargeted(fieldReference, currentMethod);
      return;
    }

    ProgramField field =
        resolutionResult.asSuccessfulResolution().getResolutionPair().asProgramField();
    if (field == null) {
      // No need to trace into the non-program code.
      return;
    }

    if (fromMethodHandle) {
      fieldAccessInfoCollection.get(field.getReference()).setWrittenFromMethodHandle();
    }

    if (Log.ENABLED) {
      Log.verbose(getClass(), "Register Sput `%s`.", fieldReference);
    }

    if (appView.options().protoShrinking().enableGeneratedExtensionRegistryShrinking) {
      // If it is a dead proto extension field, don't trace onwards.
      boolean skipTracing =
          appView.withGeneratedExtensionRegistryShrinker(
              shrinker ->
                  shrinker.isDeadProtoExtensionField(field, fieldAccessInfoCollection, keepInfo),
              false);
      if (skipTracing) {
        addDeadProtoTypeCandidate(field.getHolder());
        return;
      }
    }

    if (field.getReference() != fieldReference) {
      // Mark the non-rebound field access as targeted. Note that this should only be done if the
      // field is not a dead proto field (in which case we bail-out above).
      markFieldAsTargeted(fieldReference, currentMethod);
    }

    markStaticFieldAsLive(field, KeepReason.fieldReferencedIn(currentMethod));
  }

  private Function<DexProgramClass, KeepReasonWitness> classReferencedFromReporter(
      ProgramMethod currentMethod) {
    return clazz -> graphReporter.reportClassReferencedFrom(clazz, currentMethod);
  }

  private DexMethod getInvokeSuperTarget(DexMethod method, ProgramMethod currentMethod) {
    DexClass methodHolderClass = appView.definitionFor(method.holder);
    if (methodHolderClass != null && methodHolderClass.isInterface()) {
      return method;
    }
    DexProgramClass holderClass = currentMethod.getHolder();
    if (holderClass.superType == null || holderClass.isInterface()) {
      // We do not know better or this call is made from an interface.
      return method;
    }
    // Return the invoked method on the supertype.
    return appView.dexItemFactory().createMethod(holderClass.superType, method.proto, method.name);
  }

  //
  // Actual actions performed.
  //

  private boolean verifyMethodIsTargeted(ProgramMethod method) {
    DexEncodedMethod definition = method.getDefinition();
    assert !definition.isClassInitializer() : "Class initializers are never targeted";
    assert targetedMethods.contains(definition);
    return true;
  }

  private boolean verifyTypeIsLive(DexProgramClass clazz) {
    assert liveTypes.contains(clazz);
    return true;
  }

  private void markTypeAsLive(DexType type, KeepReason reason) {
    if (type.isArrayType()) {
      markTypeAsLive(type.toBaseType(appView.dexItemFactory()), reason);
      return;
    }
    if (!type.isClassType()) {
      // Ignore primitive types.
      return;
    }
    DexProgramClass clazz = getProgramClassOrNull(type);
    if (clazz == null) {
      return;
    }
    markTypeAsLive(clazz, reason);
  }

  private void markTypeAsLive(DexProgramClass clazz, KeepReason reason) {
    assert clazz != null;
    markTypeAsLive(clazz, graphReporter.registerClass(clazz, reason));
  }

  private void markTypeAsLive(DexType type, Function<DexProgramClass, KeepReasonWitness> reason) {
    if (type.isArrayType()) {
      markTypeAsLive(type.toBaseType(appView.dexItemFactory()), reason);
      return;
    }
    if (!type.isClassType()) {
      // Ignore primitive types.
      return;
    }
    DexProgramClass holder = getProgramClassOrNull(type);
    if (holder == null) {
      return;
    }
    markTypeAsLive(
        holder,
        scopedMethodsForLiveTypes.computeIfAbsent(type, ignore -> new ScopedDexMethodSet()),
        reason.apply(holder));
  }

  void markTypeAsLive(DexProgramClass clazz, KeepReasonWitness witness) {
    markTypeAsLive(
        clazz,
        scopedMethodsForLiveTypes.computeIfAbsent(clazz.type, ignore -> new ScopedDexMethodSet()),
        witness);
  }

  private void markTypeAsLive(
      DexProgramClass holder, ScopedDexMethodSet seen, KeepReasonWitness witness) {
    if (!liveTypes.add(holder, witness)) {
      return;
    }

    // Mark types in inner-class attributes referenced.
    for (InnerClassAttribute innerClassAttribute : holder.getInnerClasses()) {
      recordTypeReference(innerClassAttribute.getInner());
      recordTypeReference(innerClassAttribute.getOuter());
    }
    EnclosingMethodAttribute enclosingMethodAttribute = holder.getEnclosingMethodAttribute();
    if (enclosingMethodAttribute != null) {
      DexMethod enclosingMethod = enclosingMethodAttribute.getEnclosingMethod();
      if (enclosingMethod != null) {
        recordMethodReference(enclosingMethod);
      } else {
        recordTypeReference(enclosingMethodAttribute.getEnclosingClass());
      }
    }

    if (Log.ENABLED) {
      Log.verbose(getClass(), "Type `%s` has become live.", holder.type);
    }

    KeepReason reason = KeepReason.reachableFromLiveType(holder.type);

    for (DexType iface : holder.interfaces.values) {
      markInterfaceTypeAsLiveViaInheritanceClause(iface, holder);
    }

    if (holder.superType != null) {
      ScopedDexMethodSet seenForSuper =
          scopedMethodsForLiveTypes.computeIfAbsent(
              holder.superType, ignore -> new ScopedDexMethodSet());
      seen.setParent(seenForSuper);
      markTypeAsLive(holder.superType, reason);
    }

    // If this is an interface that has just become live, then report previously seen but unreported
    // implemented-by edges.
    transitionUnusedInterfaceToLive(holder);

    // We cannot remove virtual methods defined earlier in the type hierarchy if it is widening
    // access and is defined in an interface:
    //
    // public interface I {
    //   void clone();
    // }
    //
    // class Model implements I {
    //   public void clone() { ... } <-- this cannot be removed
    // }
    //
    // Any class loading of Model with Model.clone() removed will result in an illegal access
    // error because their exists an existing implementation (here it is Object.clone()). This is
    // only a problem in the DEX VM. We have to make this check no matter the output because
    // CF libraries can be used by Android apps. See b/136698023 for more information.
    ensureMethodsContinueToWidenAccess(holder, seen, reason);

    if (holder.isSerializable(appView)) {
      enqueueFirstNonSerializableClassInitializer(holder, reason);
    }

    processAnnotations(holder, holder);

    // If this type has deferred annotations, we have to process those now, too.
    if (holder.isAnnotation()) {
      Set<DexAnnotation> annotations = deferredAnnotations.remove(holder.type);
      if (annotations != null && !annotations.isEmpty()) {
        assert annotations.stream().allMatch(a -> a.annotation.type == holder.type);
        annotations.forEach(annotation -> processAnnotation(holder, holder, annotation));
      }
    }

    rootSet.forEachDependentInstanceConstructor(
        holder, appView, this::enqueueHolderWithDependentInstanceConstructor);
    rootSet.forEachDependentStaticMember(holder, appView, this::enqueueDependentMember);
    compatEnqueueHolderIfDependentNonStaticMember(
        holder, rootSet.getDependentKeepClassCompatRule(holder.getType()));

    analyses.forEach(analysis -> analysis.processNewlyLiveClass(holder, workList));
  }

  private void ensureMethodsContinueToWidenAccess(DexClass clazz) {
    assert !clazz.isProgramClass();
    ScopedDexMethodSet seen =
        scopedMethodsForLiveTypes.computeIfAbsent(clazz.type, ignore -> new ScopedDexMethodSet());
    clazz.virtualMethods().forEach(seen::addMethodIfMoreVisible);
  }

  private void ensureMethodsContinueToWidenAccess(
      DexProgramClass clazz, ScopedDexMethodSet seen, KeepReason reason) {
    clazz.forEachProgramVirtualMethodMatching(
        definition ->
            seen.addMethodIfMoreVisible(definition)
                    == AddMethodIfMoreVisibleResult.ADDED_MORE_VISIBLE
                && appView.appInfo().methodDefinedInInterfaces(definition, clazz.type),
        method -> markMethodAsTargeted(method, reason));
  }

  private void markInterfaceTypeAsLiveViaInheritanceClause(
      DexType type, DexProgramClass implementer) {
    DexProgramClass clazz = getProgramClassOrNull(type);
    if (clazz == null) {
      return;
    }

    if (!appView.options().enableUnusedInterfaceRemoval
        || rootSet.noUnusedInterfaceRemoval.contains(type)
        || mode.isTracingMainDex()) {
      markTypeAsLive(clazz, graphReporter.reportClassReferencedFrom(clazz, implementer));
    } else {
      if (liveTypes.contains(clazz)) {
        // The interface is already live, so make sure to report this implements-edge.
        graphReporter.reportClassReferencedFrom(clazz, implementer);
      } else {
        // No need to mark the type as live. If an interface type is only reachable via the
        // inheritance clause of another type it can simply be removed from the inheritance clause.
        // The interface is needed if it has a live default interface method or field, though.
        // Therefore, we record that this implemented-by edge has not been reported, such that we
        // can report it in the future if one its members becomes live.
        WorkList<DexProgramClass> worklist = WorkList.newIdentityWorkList();
        worklist.addIfNotSeen(clazz);
        while (worklist.hasNext()) {
          DexProgramClass current = worklist.next();
          if (liveTypes.contains(current)) {
            continue;
          }
          Set<DexProgramClass> implementors =
              unusedInterfaceTypes.computeIfAbsent(current, ignore -> Sets.newIdentityHashSet());
          if (implementors.add(implementer)) {
            for (DexType iface : current.interfaces.values) {
              DexProgramClass definition = getProgramClassOrNull(iface);
              if (definition != null) {
                worklist.addIfNotSeen(definition);
              }
            }
          }
        }
      }
    }
  }

  private void enqueueDependentMember(
      DexDefinition precondition,
      DexEncodedMember<?, ?> consequent,
      Set<ProguardKeepRuleBase> reasons) {
    internalEnqueueRootItem(consequent, reasons, precondition);
  }

  private void enqueueHolderWithDependentInstanceConstructor(
      ProgramMethod instanceInitializer, Set<ProguardKeepRuleBase> reasons) {
    DexProgramClass holder = instanceInitializer.getHolder();
    enqueueKeepRuleInstantiatedType(holder, reasons, instanceInitializer.getDefinition());
  }

  private void processAnnotations(DexProgramClass holder, DexDefinition annotatedItem) {
    processAnnotations(holder, annotatedItem, annotatedItem.annotations());
  }

  private void processAnnotations(
      DexProgramClass holder, DexDefinition annotatedItem, DexAnnotationSet annotations) {
    processAnnotations(holder, annotatedItem, annotations.annotations);
  }

  private void processAnnotations(
      DexProgramClass holder, DexDefinition annotatedItem, DexAnnotation[] annotations) {
    for (DexAnnotation annotation : annotations) {
      processAnnotation(holder, annotatedItem, annotation);
    }
  }

  private void processAnnotation(
      DexProgramClass holder, DexDefinition annotatedItem, DexAnnotation annotation) {
    assert annotatedItem == holder
        || annotatedItem.asDexEncodedMember().getReference().holder == holder.type;
    assert !holder.isDexClass() || holder.asDexClass().isProgramClass();
    DexType type = annotation.annotation.type;
    recordTypeReference(type);
    DexClass clazz = appView.definitionFor(type);
    boolean annotationTypeIsLibraryClass = clazz == null || clazz.isNotProgramClass();
    boolean isLive = annotationTypeIsLibraryClass || liveTypes.contains(clazz.asProgramClass());
    if (!shouldKeepAnnotation(appView, annotatedItem, annotation, isLive)) {
      // Remember this annotation for later.
      if (!annotationTypeIsLibraryClass) {
        deferredAnnotations.computeIfAbsent(type, ignore -> new HashSet<>()).add(annotation);
      }
      return;
    }
    KeepReason reason = KeepReason.annotatedOn(annotatedItem);
    graphReporter.registerAnnotation(annotation, reason);
    AnnotationReferenceMarker referenceMarker =
        new AnnotationReferenceMarker(annotation.annotation.type, appView.dexItemFactory(), reason);
    annotation.annotation.collectIndexedItems(referenceMarker);
  }

  private FieldResolutionResult resolveField(DexField field) {
    // Record the references in case they are not program types.
    recordTypeReference(field.holder);
    recordTypeReference(field.type);
    FieldResolutionResult resolutionResult = appInfo.resolveField(field);
    if (resolutionResult.isFailedOrUnknownResolution()) {
      reportMissingField(field);
    }
    return resolutionResult;
  }

  private SingleResolutionResult resolveMethod(DexMethod method, KeepReason reason) {
    // Record the references in case they are not program types.
    recordMethodReference(method);
    ResolutionResult resolutionResult = appInfo.unsafeResolveMethodDueToDexFormat(method);
    if (resolutionResult.isFailedResolution()) {
      reportMissingMethod(method);
      markFailedResolutionTargets(method, resolutionResult.asFailedResolution(), reason);
    }
    return resolutionResult.asSingleResolution();
  }

  private SingleResolutionResult resolveMethod(
      DexMethod method, KeepReason reason, boolean interfaceInvoke) {
    // Record the references in case they are not program types.
    recordMethodReference(method);
    ResolutionResult resolutionResult = appInfo.resolveMethod(method, interfaceInvoke);
    if (resolutionResult.isFailedResolution()) {
      reportMissingMethod(method);
      markFailedResolutionTargets(method, resolutionResult.asFailedResolution(), reason);
    }
    return resolutionResult.asSingleResolution();
  }

  private void handleInvokeOfStaticTarget(DexMethod reference, KeepReason reason) {
    SingleResolutionResult resolution = resolveMethod(reference, reason);
    if (resolution == null || resolution.getResolvedHolder().isNotProgramClass()) {
      return;
    }
    DexProgramClass clazz = resolution.getResolvedHolder().asProgramClass();
    DexEncodedMethod encodedMethod = resolution.getResolvedMethod();

    // We have to mark the resolved method as targeted even if it cannot actually be invoked
    // to make sure the invocation will keep failing in the appropriate way.
    ProgramMethod method = new ProgramMethod(clazz, encodedMethod);
    markMethodAsTargeted(method, reason);

    // Only mark methods for which invocation will succeed at runtime live.
    if (encodedMethod.isStatic()) {
      markDirectAndIndirectClassInitializersAsLive(clazz);
      markDirectStaticOrConstructorMethodAsLive(method, reason);
    }
  }

  private void markDirectAndIndirectClassInitializersAsLive(DexProgramClass clazz) {
    if (clazz.isInterface()) {
      // Accessing a static field or method on an interface does not trigger the class initializer
      // of any parent interfaces.
      markInterfaceInitializedDirectly(clazz);
      return;
    }

    WorkList<DexProgramClass> worklist = WorkList.newIdentityWorkList(clazz);
    while (worklist.hasNext()) {
      DexProgramClass current = worklist.next();
      if (current.isInterface()) {
        if (!markInterfaceInitializedIndirectly(current)) {
          continue;
        }
      } else {
        if (!markDirectClassInitializerAsLive(current)) {
          continue;
        }
      }

      // Mark all class initializers in all super types as live.
      for (DexType superType : current.allImmediateSupertypes()) {
        DexProgramClass superClass = getProgramClassOrNull(superType);
        if (superClass != null) {
          worklist.addIfNotSeen(superClass);
        }
      }
    }
  }

  /** Returns true if the class became initialized for the first time. */
  private boolean markDirectClassInitializerAsLive(DexProgramClass clazz) {
    ProgramMethod clinit = clazz.getProgramClassInitializer();
    KeepReasonWitness witness = graphReporter.reportReachableClassInitializer(clazz, clinit);
    if (!initializedClasses.add(clazz, witness)) {
      return false;
    }
    if (clinit != null && clinit.getDefinition().getOptimizationInfo().mayHaveSideEffects()) {
      markDirectStaticOrConstructorMethodAsLive(clinit, witness);
    }
    return true;
  }

  /**
   * Marks the interface as initialized directly and promotes the interface initializer to being
   * live if it isn't already.
   */
  private void markInterfaceInitializedDirectly(DexProgramClass clazz) {
    ProgramMethod clinit = clazz.getProgramClassInitializer();
    // Mark the interface as initialized directly.
    KeepReasonWitness witness = graphReporter.reportReachableClassInitializer(clazz, clinit);
    if (!directlyInitializedInterfaces.add(clazz, witness)) {
      return;
    }
    // Promote the interface initializer to being live if it isn't already.
    if (clinit == null || !clinit.getDefinition().getOptimizationInfo().mayHaveSideEffects()) {
      return;
    }
    if (indirectlyInitializedInterfaces.contains(clazz)
        && clazz.getMethodCollection().hasVirtualMethods(DexEncodedMethod::isDefaultMethod)) {
      assert liveMethods.contains(clinit);
      return;
    }
    markDirectStaticOrConstructorMethodAsLive(clinit, witness);
  }

  /**
   * Marks the interface as initialized indirectly and promotes the interface initializer to being
   * live if the interface has a default interface method and is not already live.
   *
   * @return true if the interface became initialized indirectly for the first time.
   */
  private boolean markInterfaceInitializedIndirectly(DexProgramClass clazz) {
    ProgramMethod clinit = clazz.getProgramClassInitializer();
    // Mark the interface as initialized indirectly.
    KeepReasonWitness witness = graphReporter.reportReachableClassInitializer(clazz, clinit);
    if (!indirectlyInitializedInterfaces.add(clazz, witness)) {
      return false;
    }
    // Promote the interface initializer to being live if it has a default interface method and
    // isn't already live.
    if (clinit == null
        || !clinit.getDefinition().getOptimizationInfo().mayHaveSideEffects()
        || !clazz.getMethodCollection().hasVirtualMethods(DexEncodedMethod::isDefaultMethod)) {
      return true;
    }
    if (directlyInitializedInterfaces.contains(clazz)) {
      assert liveMethods.contains(clinit);
      return true;
    }
    markDirectStaticOrConstructorMethodAsLive(clinit, witness);
    return true;
  }

  // Package protected due to entry point from worklist.
  void markNonStaticDirectMethodAsReachable(DexMethod method, KeepReason reason) {
    handleInvokeOfDirectTarget(method, reason);
  }

  private void handleInvokeOfDirectTarget(DexMethod reference, KeepReason reason) {
    DexType holder = reference.holder;
    DexProgramClass clazz = getProgramClassOrNull(holder);
    if (clazz == null) {
      recordMethodReference(reference);
      return;
    }
    // TODO(zerny): Is it ok that we lookup in both the direct and virtual pool here?
    DexEncodedMethod encodedMethod = clazz.lookupMethod(reference);
    if (encodedMethod == null) {
      reportMissingMethod(reference);
      return;
    }

    ProgramMethod method = new ProgramMethod(clazz, encodedMethod);

    // We have to mark the resolved method as targeted even if it cannot actually be invoked
    // to make sure the invocation will keep failing in the appropriate way.
    markMethodAsTargeted(method, reason);

    // Only mark methods for which invocation will succeed at runtime live.
    if (encodedMethod.isStatic()) {
      return;
    }

    markDirectStaticOrConstructorMethodAsLive(method, reason);

    // It is valid to have an invoke-direct instruction in a default interface method that
    // targets another default method in the same interface (see testInvokeSpecialToDefault-
    // Method). In a class, that would lead to a verification error.
    if (encodedMethod.isNonPrivateVirtualMethod()
        && virtualMethodsTargetedByInvokeDirect.add(encodedMethod.method)) {
      enqueueMarkMethodLiveAction(method, reason);
    }
  }

  private void ensureFromLibraryOrThrow(DexType type, DexClass context) {
    if (mode.isTracingMainDex()) {
      // b/72312389: android.jar contains parts of JUnit and most developers include JUnit in
      // their programs. This leads to library classes extending program classes. When tracing
      // main dex lists we allow this.
      return;
    }
    DexProgramClass holder = getProgramClassOrNull(type);
    if (holder == null) {
      return;
    }
    if (forceProguardCompatibility) {
      // To ensure that the program works correctly we have to pin all super types and members
      // in the tree.
      KeepReason keepReason = KeepReason.reachableFromLiveType(context.type);
      keepClassAndAllMembers(holder, keepReason);
      appInfo.forEachSuperType(
          holder,
          (dexType, ignored) -> {
            DexProgramClass superClass = getProgramClassOrNull(dexType);
            if (superClass != null) {
              keepClassAndAllMembers(superClass, keepReason);
            }
          });
    }
    if (dontWarnPatterns.matches(context.type)) {
      // Ignore.
      return;
    }
    // Only report an error during the first round of treeshaking.
    if (mode.isInitialTreeShaking()) {
      Diagnostic message =
          new StringDiagnostic(
              "Library class "
                  + context.type.toSourceString()
                  + (holder.isInterface() ? " implements " : " extends ")
                  + "program class "
                  + type.toSourceString());
      if (forceProguardCompatibility) {
        options.reporter.warning(message);
      } else {
        options.reporter.error(message);
      }
    }
  }

  private void shouldNotBeMinified(DexReference reference) {
    if (options.isMinificationEnabled()) {
      rootSet.shouldNotBeMinified(reference);
    }
  }

  private void keepClassAndAllMembers(DexProgramClass clazz, KeepReason keepReason) {
    KeepReasonWitness keepReasonWitness = graphReporter.registerClass(clazz, keepReason);
    markClassAsInstantiatedWithCompatRule(clazz.asProgramClass(), keepReasonWitness);
    keepInfo.keepClass(clazz);
    shouldNotBeMinified(clazz.getReference());
    clazz.forEachProgramField(
        field -> {
          keepInfo.keepField(field);
          shouldNotBeMinified(field.getReference());
          markFieldAsKept(field, keepReasonWitness);
        });
    clazz.forEachProgramMethod(
        method -> {
          keepInfo.keepMethod(method);
          shouldNotBeMinified(method.getReference());
          markMethodAsKept(method, keepReasonWitness);
        });
  }

  private void reportMissingClass(DexType clazz) {
    assert !mode.isFinalTreeShaking()
            || appView.dexItemFactory().isPossiblyCompilerSynthesizedType(clazz)
            || initialDeadProtoTypes.contains(clazz)
            || initialMissingTypes.contains(clazz)
        : "Unexpected missing class `" + clazz.toSourceString() + "`";
    boolean newReport = missingTypes.add(clazz);
    if (Log.ENABLED && newReport) {
      Log.verbose(Enqueuer.class, "Class `%s` is missing.", clazz);
    }
  }

  private void reportMissingMethod(DexMethod method) {
    if (Log.ENABLED && reportedMissing.add(method)) {
      Log.verbose(Enqueuer.class, "Method `%s` is missing.", method);
    }
  }

  private void reportMissingField(DexField field) {
    if (Log.ENABLED && reportedMissing.add(field)) {
      Log.verbose(Enqueuer.class, "Field `%s` is missing.", field);
    }
  }

  private void markMethodAsTargeted(ProgramMethod method, KeepReason reason) {
    DexEncodedMethod definition = method.getDefinition();
    DexProgramClass holder = method.getHolder();
    if (!targetedMethods.add(definition, reason)) {
      // Already targeted.
      return;
    }
    markReferencedTypesAsLive(method);
    processAnnotations(holder, definition);
    definition.parameterAnnotationsList.forEachAnnotation(
        annotation -> processAnnotation(holder, definition, annotation));

    if (Log.ENABLED) {
      Log.verbose(getClass(), "Method `%s` is targeted.", method);
    }
    if (forceProguardCompatibility) {
      // Keep targeted default methods in compatibility mode. The tree pruner will otherwise make
      // these methods abstract, whereas Proguard does not (seem to) touch their code.
      if (!definition.isAbstract() && holder.isInterface()) {
        markMethodAsLiveWithCompatRule(method);
      }
    }
  }

  /**
   * Adds the class to the set of instantiated classes and marks its fields and methods live
   * depending on the currently seen invokes and field reads.
   */
  // Package protected due to entry point from worklist.
  void processNewlyInstantiatedClass(
      DexProgramClass clazz,
      ProgramMethod context,
      InstantiationReason instantiationReason,
      KeepReason keepReason) {
    assert !clazz.isAnnotation();
    assert !clazz.isInterface();

    // Notify analyses. This is done even if `clazz` has already been marked as instantiated,
    // because each analysis may depend on seeing all the (clazz, reason) pairs. Thus, not doing so
    // could lead to nondeterminism.
    analyses.forEach(
        analysis -> analysis.processNewlyInstantiatedClass(clazz.asProgramClass(), context));

    if (!markInstantiatedClass(clazz, context, instantiationReason, keepReason)) {
      return;
    }

    if (Log.ENABLED) {
      Log.verbose(getClass(), "Class `%s` is instantiated, processing...", clazz);
    }
    // This class becomes live, so it and all its supertypes become live types.
    markTypeAsLive(clazz, graphReporter.registerClass(clazz, keepReason));
    // Instantiation triggers class initialization.
    markDirectAndIndirectClassInitializersAsLive(clazz);
    // For all methods of the class, if we have seen a call, mark the method live.
    // We only do this for virtual calls, as the other ones will be done directly.
    transitionMethodsForInstantiatedClass(clazz);
    // For all instance fields visible from the class, mark them live if we have seen a read.
    transitionFieldsForInstantiatedClass(clazz);
    // Add all dependent instance members to the workqueue.
    transitionDependentItemsForInstantiatedClass(clazz);
  }

  // TODO(b/146016987): Make this the single instantiation entry rather than the worklist action.
  private boolean markInstantiatedClass(
      DexProgramClass clazz,
      ProgramMethod context,
      InstantiationReason instantiationReason,
      KeepReason keepReason) {
    assert !clazz.isInterface();
    return objectAllocationInfoCollection.recordDirectAllocationSite(
        clazz, context, instantiationReason, keepReason, appInfo);
  }

  void markAnnotationAsInstantiated(DexProgramClass clazz, KeepReasonWitness witness) {
    assert clazz.isAnnotation();
    markTypeAsLive(clazz, witness);
    transitionDependentItemsForInstantiatedInterface(clazz);
  }

  void markInterfaceAsInstantiated(DexProgramClass clazz, KeepReasonWitness witness) {
    assert !clazz.isAnnotation();
    assert clazz.isInterface();
    if (!objectAllocationInfoCollection.recordInstantiatedInterface(clazz, appInfo)) {
      return;
    }
    markTypeAsLive(clazz, witness);
    transitionDependentItemsForInstantiatedInterface(clazz);
  }

  private void markLambdaAsInstantiated(LambdaDescriptor descriptor, ProgramMethod context) {
    // Each descriptor is unique, so there is no check for already marking the lambda.
    for (DexType iface : descriptor.interfaces) {
      checkLambdaInterface(iface, context);
      objectAllocationInfoCollection.recordInstantiatedLambdaInterface(iface, descriptor, appInfo);
    }
  }

  private void checkLambdaInterface(DexType itf, ProgramMethod context) {
    DexClass clazz = definitionFor(itf);
    if (clazz == null) {
      StringDiagnostic message =
          new StringDiagnostic(
              "Lambda expression implements missing interface `" + itf.toSourceString() + "`",
              context.getOrigin());
      options.reporter.warning(message);
    } else if (!clazz.isInterface()) {
      StringDiagnostic message =
          new StringDiagnostic(
              "Lambda expression expected to implement an interface, but found "
                  + "`"
                  + itf.toSourceString()
                  + "`",
              context.getOrigin());
      options.reporter.warning(message);
    }
  }

  private void transitionMethodsForInstantiatedLambda(LambdaDescriptor lambda) {
    transitionMethodsForInstantiatedObject(
        InstantiatedObject.of(lambda),
        definitionFor(appInfo.dexItemFactory().objectType),
        lambda.interfaces);
  }

  private void transitionMethodsForInstantiatedClass(DexProgramClass clazz) {
    assert !clazz.isAnnotation();
    assert !clazz.isInterface();
    transitionMethodsForInstantiatedObject(
        InstantiatedObject.of(clazz), clazz, Collections.emptyList());
  }

  /**
   * Marks all methods live that are overrides of reachable methods for a given instantiation.
   *
   * <p>Only reachable methods in the hierarchy of the given instantiation and above are considered,
   * and only the lowest such reachable target (ie, mirroring resolution). All library and classpath
   * methods are considered reachable.
   */
  private void transitionMethodsForInstantiatedObject(
      InstantiatedObject instantiation, DexClass clazz, List<DexType> interfaces) {
    Set<Wrapper<DexMethod>> seen = new HashSet<>();
    WorkList<DexType> worklist = WorkList.newIdentityWorkList();
    worklist.addIfNotSeen(interfaces);
    // First we lookup and mark all targets on the instantiated class for each reachable method in
    // the super chain (inclusive).
    DexClass initialClass = clazz;
    while (clazz != null) {
      if (clazz.isProgramClass()) {
        markProgramMethodOverridesAsLive(instantiation, initialClass, clazz.asProgramClass(), seen);
      } else {
        markLibraryAndClasspathMethodOverridesAsLive(instantiation, clazz);
      }
      worklist.addIfNotSeen(Arrays.asList(clazz.interfaces.values));
      clazz = clazz.superType != null ? definitionFor(clazz.superType) : null;
    }
    // The targets for methods on the type and its supertype that are reachable are now marked.
    // In a second step, we look at interfaces. We order the search this way such that a
    // method reachable on a class takes precedence when reporting edges. That order mirrors JVM
    // resolution/dispatch.
    while (worklist.hasNext()) {
      DexType type = worklist.next();
      DexClass iface = definitionFor(type);
      if (iface == null) {
        continue;
      }
      assert iface.superType == appInfo.dexItemFactory().objectType;
      if (iface.isNotProgramClass()) {
        markLibraryAndClasspathMethodOverridesAsLive(instantiation, iface);
      } else {
        markProgramMethodOverridesAsLive(instantiation, initialClass, iface.asProgramClass(), seen);
      }
      worklist.addIfNotSeen(Arrays.asList(iface.interfaces.values));
    }
  }

  private Set<DexMethod> getReachableVirtualTargets(DexProgramClass clazz) {
    return reachableVirtualTargets.getOrDefault(clazz, Collections.emptySet());
  }

  private void markProgramMethodOverridesAsLive(
      InstantiatedObject instantiation,
      DexClass initialClass,
      DexProgramClass superClass,
      Set<Wrapper<DexMethod>> seenMethods) {
    for (DexMethod method : getReachableVirtualTargets(superClass)) {
      assert method.holder == superClass.type;
      Wrapper<DexMethod> signature = MethodSignatureEquivalence.get().wrap(method);
      if (!seenMethods.contains(signature)) {
        SingleResolutionResult resolution =
            appInfo.resolveMethodOn(superClass, method).asSingleResolution();
        assert resolution != null;
        assert resolution.getResolvedHolder().isProgramClass();
        if (resolution != null) {
          if (!initialClass.isProgramClass()
              || resolution
                  .isAccessibleForVirtualDispatchFrom(initialClass.asProgramClass(), appInfo)
                  .isTrue()) {
            seenMethods.add(signature);
          }
          if (resolution.getResolvedHolder().isProgramClass()) {
            markLiveOverrides(
                instantiation, superClass, resolution.getResolutionPair().asProgramMethod());
          }
        }
      }
    }
  }

  private void markLiveOverrides(
      InstantiatedObject instantiation,
      DexProgramClass initialHolder,
      ProgramMethod resolutionMethod) {
    // The validity of the reachable method is checked at the point it becomes "reachable" and is
    // resolved. If the method is private, then the dispatch is not "virtual" and the method is
    // simply marked live on its holder.
    if (resolutionMethod.getDefinition().isPrivateMethod()) {
      markVirtualMethodAsLive(
          resolutionMethod,
          graphReporter.reportReachableMethodAsLive(
              resolutionMethod.getDefinition().method, resolutionMethod));
      return;
    }
    // Otherwise, we set the initial holder type to be the holder of the reachable method, which
    // ensures that access will be generally valid.
    SingleResolutionResult resolution =
        new SingleResolutionResult(
            initialHolder, resolutionMethod.getHolder(), resolutionMethod.getDefinition());
    LookupTarget lookup = resolution.lookupVirtualDispatchTarget(instantiation, appInfo);
    if (lookup != null) {
      markVirtualDispatchTargetAsLive(
          lookup,
          programMethod ->
              graphReporter.reportReachableMethodAsLive(
                  resolutionMethod.getDefinition().method, programMethod));
    }
  }

  private void markLibraryAndClasspathMethodOverridesAsLive(
      InstantiatedObject instantiation, DexClass libraryClass) {
    assert libraryClass.isNotProgramClass();
    if (mode.isTracingMainDex()) {
      // Library roots must be specified for tracing of library methods. For classpath the expected
      // use case is that the classes will be classloaded, thus they should have no bearing on the
      // content of the main dex file.
      return;
    }
    for (DexEncodedMethod method : libraryClass.virtualMethods()) {
      assert !method.isPrivateMethod();
      // Note: It would be reasonable to not process methods already seen during the marking of
      // program usages, but that would cause the methods to not be marked as library overrides.
      markLibraryOrClasspathOverrideLive(
          instantiation, libraryClass, appInfo.resolveMethodOn(libraryClass, method.method));

      // Due to API conversion, some overrides can be hidden since they will be rewritten. See
      // class comment of DesugaredLibraryAPIConverter and vivifiedType logic.
      // In the first enqueuer phase, the signature has not been desugared, so firstResolution
      // maintains the library override. In the second enqueuer phase, the signature has been
      // desugared, and the second resolution maintains the the library override.
      if (instantiation.isClass()
          && appView.rewritePrefix.hasRewrittenTypeInSignature(method.method.proto, appView)) {
        DexMethod methodToResolve =
            DesugaredLibraryAPIConverter.methodWithVivifiedTypeInSignature(
                method.method, method.holder(), appView);
        assert methodToResolve != method.method;
        markLibraryOrClasspathOverrideLive(
            instantiation,
            libraryClass,
            appInfo.resolveMethodOn(instantiation.asClass(), methodToResolve));
      }
    }
  }

  private void markLibraryOrClasspathOverrideLive(
      InstantiatedObject instantiation,
      DexClass libraryOrClasspathClass,
      ResolutionResult resolution) {
    LookupTarget lookup = resolution.lookupVirtualDispatchTarget(instantiation, appInfo);
    if (lookup == null) {
      return;
    }
    if (!shouldMarkLibraryMethodOverrideAsReachable(lookup)) {
      return;
    }
    markVirtualDispatchTargetAsLive(
        lookup,
        method ->
            graphReporter.reportLibraryMethodAsLive(
                instantiation, method, libraryOrClasspathClass));
    if (instantiation.isClass()) {
      // TODO(b/149976493): We need to mark these for lambdas too!
      markOverridesAsLibraryMethodOverrides(
          instantiation.asClass(), lookup.asMethodTarget().getDefinition().method);
    }
  }

  private void markOverridesAsLibraryMethodOverrides(
      DexProgramClass instantiatedClass, DexMethod libraryMethodOverride) {
    WorkList<DexType> worklist = WorkList.newIdentityWorkList();
    worklist.addIfNotSeen(instantiatedClass.type);
    while (worklist.hasNext()) {
      DexType type = worklist.next();
      DexProgramClass clazz = getProgramClassOrNull(type);
      if (clazz == null) {
        continue;
      }
      DexEncodedMethod override = clazz.lookupVirtualMethod(libraryMethodOverride);
      if (override != null) {
        if (override.isLibraryMethodOverride().isTrue()) {
          continue;
        }
        override.setLibraryMethodOverride(OptionalBool.TRUE);
      }
      clazz.forEachImmediateSupertype(worklist::addIfNotSeen);
    }
  }

  /**
   * Marks all fields live that can be reached by a read assuming that the given type or one of its
   * subtypes is instantiated.
   */
  private void transitionFieldsForInstantiatedClass(DexProgramClass clazz) {
    do {
      ProgramFieldSet reachableFields = reachableInstanceFields.get(clazz);
      if (reachableFields != null) {
        // TODO(b/120959039): Should the reason this field is reachable come from the set?
        KeepReason reason = KeepReason.reachableFromLiveType(clazz.type);
        reachableFields.forEach(field -> markInstanceFieldAsLive(field, reason));
      }
      clazz = getProgramClassOrNull(clazz.superType);
    } while (clazz != null && !objectAllocationInfoCollection.isInstantiatedDirectly(clazz));
  }

  private void transitionDependentItemsForInstantiatedClass(DexProgramClass clazz) {
    assert !clazz.isAnnotation();
    assert !clazz.isInterface();
    transitionDependentItemsForInstantiatedItem(clazz);
  }

  private void transitionDependentItemsForInstantiatedInterface(DexProgramClass clazz) {
    assert clazz.isInterface();
    transitionDependentItemsForInstantiatedItem(clazz);
  }

  private void transitionDependentItemsForInstantiatedItem(DexProgramClass clazz) {
    do {
      // Handle keep rules that are dependent on the class being instantiated.
      rootSet.forEachDependentNonStaticMember(clazz, appView, this::enqueueDependentMember);

      // Visit the super type.
      clazz =
          clazz.superType != null
              ? asProgramClassOrNull(appView.definitionFor(clazz.superType))
              : null;
    } while (clazz != null && !objectAllocationInfoCollection.isInstantiatedDirectly(clazz));
  }

  private void transitionUnusedInterfaceToLive(DexProgramClass clazz) {
    if (clazz.isInterface()) {
      Set<DexProgramClass> implementedBy = unusedInterfaceTypes.remove(clazz);
      if (implementedBy != null) {
        for (DexProgramClass implementer : implementedBy) {
          markTypeAsLive(clazz, graphReporter.reportClassReferencedFrom(clazz, implementer));
        }
      }
    } else {
      assert !unusedInterfaceTypes.containsKey(clazz);
    }
  }

  private void markFieldAsTargeted(DexField field, ProgramMethod context) {
    markTypeAsLive(field.type, clazz -> graphReporter.reportClassReferencedFrom(clazz, context));
    markTypeAsLive(field.holder, clazz -> graphReporter.reportClassReferencedFrom(clazz, context));
  }

  private void markStaticFieldAsLive(ProgramField field, KeepReason reason) {
    // Mark the type live here, so that the class exists at runtime.
    markTypeAsLive(
        field.getHolder(), graphReporter.reportClassReferencedFrom(field.getHolder(), field));
    markTypeAsLive(
        field.getReference().type, clazz -> graphReporter.reportClassReferencedFrom(clazz, field));

    markDirectAndIndirectClassInitializersAsLive(field.getHolder());

    // This field might be an instance field reachable from a static context, e.g. a getStatic that
    // resolves to an instance field. We have to keep the instance field nonetheless, as otherwise
    // we might unmask a shadowed static field and hence change semantics.
    if (field.getDefinition().isStatic()) {
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Adding static field `%s` to live set.", field);
      }
    } else {
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Adding instance field `%s` to live set (static context).", field);
      }
    }
    processAnnotations(field.getHolder(), field.getDefinition());
    liveFields.add(field, reason);

    // Add all dependent members to the workqueue.
    enqueueRootItems(rootSet.getDependentItems(field.getDefinition()));

    checkMemberForSoftPinning(field);

    // Notify analyses.
    analyses.forEach(analysis -> analysis.processNewlyLiveField(field));
  }

  private void markInstanceFieldAsLive(ProgramField field, KeepReason reason) {
    markTypeAsLive(field.getHolder(), graphReporter.registerClass(field.getHolder(), reason));
    markTypeAsLive(field.getReference().type, reason);
    if (Log.ENABLED) {
      Log.verbose(getClass(), "Adding instance field `%s` to live set.", field);
    }
    processAnnotations(field.getHolder(), field.getDefinition());
    liveFields.add(field, reason);

    // Add all dependent members to the workqueue.
    enqueueRootItems(rootSet.getDependentItems(field.getDefinition()));

    checkMemberForSoftPinning(field);

    // Notify analyses.
    analyses.forEach(analysis -> analysis.processNewlyLiveField(field));
  }

  private void markDirectStaticOrConstructorMethodAsLive(ProgramMethod method, KeepReason reason) {
    if (!enqueueMarkMethodLiveAction(method, reason)) {
      // Already marked live.
      return;
    }
    // Should already have marked the type live previously.
    assert method.getDefinition().isClassInitializer() || verifyMethodIsTargeted(method);
    assert verifyTypeIsLive(method.getHolder());
    if (Log.ENABLED) {
      Log.verbose(getClass(), "Method `%s` has become live due to direct invoke", method);
    }
  }

  private void markVirtualMethodAsLive(ProgramMethod method, KeepReason reason) {
    assert method != null;
    // Only explicit keep rules or reflective use should make abstract methods live.
    assert !method.getDefinition().isAbstract()
        || reason.isDueToKeepRule()
        || reason.isDueToReflectiveUse();
    if (enqueueMarkMethodLiveAction(method, reason)) {
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Adding virtual method `%s` to live set.", method);
      }
    }
  }

  public boolean isFieldReferenced(DexEncodedField field) {
    FieldAccessInfoImpl info = fieldAccessInfoCollection.get(field.field);
    return info != null;
  }

  public boolean isFieldLive(ProgramField field) {
    return liveFields.contains(field);
  }

  public boolean isFieldLive(DexEncodedField field) {
    return liveFields.contains(field);
  }

  public boolean isFieldRead(ProgramField field) {
    FieldAccessInfoImpl info = fieldAccessInfoCollection.get(field.getReference());
    return info != null && info.isRead();
  }

  public boolean isFieldWrittenInMethodSatisfying(
      ProgramField field, Predicate<ProgramMethod> predicate) {
    FieldAccessInfoImpl info = fieldAccessInfoCollection.get(field.getReference());
    return info != null && info.isWrittenInMethodSatisfying(predicate);
  }

  public boolean isFieldWrittenOutsideDefaultConstructor(ProgramField field) {
    FieldAccessInfoImpl info = fieldAccessInfoCollection.get(field.getReference());
    if (info == null) {
      return false;
    }
    DexEncodedMethod defaultInitializer = field.getHolder().getDefaultInitializer();
    return defaultInitializer != null
        ? info.isWrittenOutside(defaultInitializer)
        : info.isWritten();
  }

  public boolean isMemberLive(DexEncodedMember<?, ?> member) {
    assert member != null;
    return member.isDexEncodedField()
        ? liveFields.contains(member.asDexEncodedField())
        : liveMethods.contains(member.asDexEncodedMethod());
  }

  public boolean isMethodLive(DexEncodedMethod method) {
    return liveMethods.contains(method);
  }

  public boolean isMethodTargeted(DexEncodedMethod method) {
    return targetedMethods.contains(method);
  }

  public boolean isTypeLive(DexClass clazz) {
    return clazz.isProgramClass()
        ? isTypeLive(clazz.asProgramClass())
        : isNonProgramTypeLive(clazz);
  }

  public boolean isTypeLive(DexProgramClass clazz) {
    return liveTypes.contains(clazz);
  }

  public boolean isNonProgramTypeLive(DexClass clazz) {
    assert !clazz.isProgramClass();
    return liveNonProgramTypes.contains(clazz);
  }

  public void forAllLiveClasses(Consumer<DexProgramClass> consumer) {
    liveTypes.items.forEach(consumer);
  }

  // Package protected due to entry point from worklist.
  void markInstanceFieldAsReachable(ProgramField field, KeepReason reason) {
    if (Log.ENABLED) {
      Log.verbose(getClass(), "Marking instance field `%s` as reachable.", field);
    }

    markTypeAsLive(
        field.getHolder(), graphReporter.reportClassReferencedFrom(field.getHolder(), field));
    markTypeAsLive(
        field.getReference().type, clazz -> graphReporter.reportClassReferencedFrom(clazz, field));

    // We might have a instance field access that is dispatched to a static field. In such case,
    // we have to keep the static field, so that the dispatch fails at runtime in the same way that
    // it did before. We have to keep the field even if the receiver has no live inhabitants, as
    // field resolution happens before the receiver is inspected.
    if (field.getDefinition().isStatic()) {
      markStaticFieldAsLive(field, reason);
    } else if (objectAllocationInfoCollection.isInstantiatedDirectlyOrHasInstantiatedSubtype(
        field.getHolder())) {
      markInstanceFieldAsLive(field, reason);
    } else {
      // Add the field to the reachable set if the type later becomes instantiated.
      reachableInstanceFields
          .computeIfAbsent(field.getHolder(), ignore -> ProgramFieldSet.create())
          .add(field);
    }
  }

  private void markVirtualMethodAsReachable(
      DexMethod method, boolean interfaceInvoke, ProgramMethod contextOrNull, KeepReason reason) {
    if (method.holder.isArrayType()) {
      // This is an array type, so the actual class will be generated at runtime. We treat this
      // like an invoke on a direct subtype of java.lang.Object that has no further subtypes.
      // As it has no subtypes, it cannot affect liveness of the program we are processing.
      // Ergo, we can ignore it. We need to make sure that the element type is available, though.
      markTypeAsLive(method.holder, reason);
      return;
    }

    // Note that all virtual methods derived from library methods are kept regardless of being
    // reachable, so the following only needs to consider reachable targets in the program.
    // TODO(b/70160030): Revise this to support tree shaking library methods on non-escaping types.
    DexProgramClass holder = getProgramClassOrNull(method.holder);
    if (holder == null) {
      // TODO(b/139464956): clean this.
      // Ensure that the full proto of the targeted method is referenced.
      recordMethodReference(method);
      return;
    }

    SingleResolutionResult resolution = resolveMethod(method, reason, interfaceInvoke);
    if (resolution == null) {
      return;
    }

    if (resolution.getResolvedHolder().isNotProgramClass()) {
      // TODO(b/70160030): If the resolution is on a library method, then the keep edge needs to go
      // directly to the target method in the program. Thus this method will need to ensure that
      // 'reason' is not already reported (eg, must be delayed / non-witness) and report that for
      // each possible target edge below.
      return;
    }

    // If the method has already been marked, just report the new reason for the resolved target.
    if (getReachableVirtualTargets(holder).contains(method)) {
      graphReporter.registerMethod(resolution.getResolvedMethod(), reason);
      return;
    }

    if (Log.ENABLED) {
      Log.verbose(getClass(), "Marking virtual method `%s` as reachable.", method);
    }

    // We have to mark the resolution targeted, even if it does not become live, we
    // need at least an abstract version of it so that it can be targeted.
    DexProgramClass resolvedHolder = resolution.getResolvedHolder().asProgramClass();
    DexEncodedMethod resolvedMethod = resolution.getResolvedMethod();
    markMethodAsTargeted(new ProgramMethod(resolvedHolder, resolvedMethod), reason);

    DexProgramClass contextHolder = contextOrNull != null ? contextOrNull.getHolder() : null;
    if (contextOrNull != null
        && resolution.isAccessibleForVirtualDispatchFrom(contextHolder, appInfo).isFalse()) {
      // Not accessible from this context, so this call will cause a runtime exception.
      return;
    }

    // If the resolved method is not a virtual target, eg, is static, dispatch will fail too.
    if (!resolvedMethod.isVirtualMethod()) {
      // This can only happen when context is null, otherwise the access check above will fail.
      assert contextOrNull == null;
      return;
    }

    // The method resolved and is accessible, so currently live overrides become live.
    reachableVirtualTargets.computeIfAbsent(holder, k -> Sets.newIdentityHashSet()).add(method);

    resolution
        .lookupVirtualDispatchTargets(
            contextHolder,
            appInfo,
            (type, subTypeConsumer, lambdaConsumer) ->
                objectAllocationInfoCollection.forEachInstantiatedSubType(
                    type, subTypeConsumer, lambdaConsumer, appInfo),
            definition -> keepInfo.isPinned(definition.getReference(), appInfo))
        .forEach(
            target ->
                markVirtualDispatchTargetAsLive(
                    target,
                    programMethod ->
                        graphReporter.reportReachableMethodAsLive(
                            resolvedMethod.method, programMethod)));
  }

  private void markVirtualDispatchTargetAsLive(
      LookupTarget target, Function<ProgramMethod, KeepReasonWitness> reason) {
    if (target.isMethodTarget()) {
      markVirtualDispatchTargetAsLive(target.asMethodTarget(), reason);
    } else {
      assert target.isLambdaTarget();
      markVirtualDispatchTargetAsLive(target.asLambdaTarget(), reason);
    }
  }

  private void markVirtualDispatchTargetAsLive(
      DexClassAndMethod target, Function<ProgramMethod, KeepReasonWitness> reason) {
    ProgramMethod programMethod = target.asProgramMethod();
    if (programMethod != null && !programMethod.getDefinition().isAbstract()) {
      markVirtualMethodAsLive(programMethod, reason.apply(programMethod));
    }
  }

  private void markVirtualDispatchTargetAsLive(
      LookupLambdaTarget target, Function<ProgramMethod, KeepReasonWitness> reason) {
    ProgramMethod implementationMethod = target.getImplementationMethod().asProgramMethod();
    if (implementationMethod != null) {
      enqueueMarkMethodLiveAction(implementationMethod, reason.apply(implementationMethod));
    }
  }

  private void markFailedResolutionTargets(
      DexMethod symbolicMethod, FailedResolutionResult failedResolution, KeepReason reason) {
    failedResolutionTargets.add(symbolicMethod);
    failedResolution.forEachFailureDependency(
        method -> {
          DexProgramClass clazz = getProgramClassOrNull(method.holder());
          if (clazz != null) {
            failedResolutionTargets.add(method.method);
            markMethodAsTargeted(new ProgramMethod(clazz, method), reason);
          }
        });
  }

  private DexMethod generatedEnumValuesMethod(DexClass enumClass) {
    DexType arrayOfEnumClass =
        appView
            .dexItemFactory()
            .createType(
                appView.dexItemFactory().createString("[" + enumClass.type.toDescriptorString()));
    DexProto proto = appView.dexItemFactory().createProto(arrayOfEnumClass);
    return appView
        .dexItemFactory()
        .createMethod(enumClass.type, proto, appView.dexItemFactory().createString("values"));
  }

  private void markEnumValuesAsReachable(DexProgramClass clazz, KeepReason reason) {
    ProgramMethod valuesMethod = clazz.lookupProgramMethod(generatedEnumValuesMethod(clazz));
    if (valuesMethod != null) {
      // TODO(sgjesse): Does this have to be enqueued as a root item? Right now it is done as the
      // marking for not renaming it is in the root set.
      workList.enqueueMarkMethodKeptAction(valuesMethod, reason);
      keepInfo.joinMethod(valuesMethod, joiner -> joiner.pin().disallowMinification());
      shouldNotBeMinified(valuesMethod.getReference());
    }
  }

  // Package protected due to entry point from worklist.
  void markSuperMethodAsReachable(DexMethod reference, ProgramMethod from) {
    KeepReason reason = KeepReason.targetedBySuperFrom(from);
    SingleResolutionResult resolution = resolveMethod(reference, reason);
    if (resolution == null) {
      return;
    }
    // If the resolution is in the program, mark it targeted.
    if (resolution.getResolvedHolder().isProgramClass()) {
      markMethodAsTargeted(
          new ProgramMethod(
              resolution.getResolvedHolder().asProgramClass(), resolution.getResolvedMethod()),
          reason);
    }
    // If invoke target is invalid (inaccessible or not an instance-method) record it and stop.
    DexEncodedMethod target = resolution.lookupInvokeSuperTarget(from.getHolder(), appInfo);
    if (target == null) {
      failedResolutionTargets.add(resolution.getResolvedMethod().method);
      return;
    }

    DexProgramClass clazz = getProgramClassOrNull(target.holder());
    if (clazz == null) {
      return;
    }

    ProgramMethod method = new ProgramMethod(clazz, target);

    if (Log.ENABLED) {
      Log.verbose(getClass(), "Adding super constraint from `%s` to `%s`", from, target.method);
    }
    if (superInvokeDependencies
        .computeIfAbsent(from.getDefinition(), ignore -> ProgramMethodSet.create())
        .add(method)) {
      if (liveMethods.contains(from)) {
        markMethodAsTargeted(method, KeepReason.invokedViaSuperFrom(from));
        if (!target.accessFlags.isAbstract()) {
          markVirtualMethodAsLive(method, KeepReason.invokedViaSuperFrom(from));
        }
      }
    }
  }

  // Returns the set of live types.
  public Set<DexProgramClass> traceMainDex(
      RootSet rootSet, ExecutorService executorService, Timing timing) throws ExecutionException {
    assert analyses.isEmpty();
    assert mode.isTracingMainDex();
    this.rootSet = rootSet;
    // Translate the result of root-set computation into enqueuer actions.
    enqueueRootItems(rootSet.noShrinking);
    trace(executorService, timing);
    options.reporter.failIfPendingErrors();
    return liveTypes.getItems();
  }

  public AppInfoWithLiveness traceApplication(
      RootSet rootSet,
      ProguardClassFilter dontWarnPatterns,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    this.rootSet = rootSet;
    this.dontWarnPatterns = dontWarnPatterns;
    // Translate the result of root-set computation into enqueuer actions.
    if (appView.options().getProguardConfiguration() != null
        && !options.kotlinOptimizationOptions().disableKotlinSpecificOptimizations) {
      registerAnalysis(
          new KotlinMetadataEnqueuerExtension(
              appView, enqueuerDefinitionSupplier, initialPrunedTypes));
    }
    if (appView.options().getProguardConfiguration() != null
        && appView.options().getProguardConfiguration().getKeepAttributes().signature) {
      registerAnalysis(new GenericSignatureEnqueuerAnalysis(enqueuerDefinitionSupplier));
    }
    if (mode.isInitialTreeShaking()) {
      // This is simulating the effect of the "root set" applied rules.
      // This is done only in the initial pass, in subsequent passes the "rules" are reapplied
      // by iterating the instances.
      assert appView.options().isMinificationEnabled() || rootSet.noObfuscation.isEmpty();
      for (DexReference reference : rootSet.noObfuscation) {
        keepInfo.evaluateRule(reference, appInfo, Joiner::disallowMinification);
      }
    } else if (appView.getKeepInfo() != null) {
      appView
          .getKeepInfo()
          .getRuleInstances()
          .forEach(
              (reference, rules) -> {
                for (Consumer<Joiner<?, ?, ?>> rule : rules) {
                  keepInfo.evaluateRule(reference, appInfo, rule);
                }
              });
    }
    if (appView.options().isShrinking() || appView.options().getProguardConfiguration() == null) {
      enqueueRootItems(rootSet.noShrinking);
    } else {
      // Add everything if we are not shrinking.
      assert appView.options().getProguardConfiguration().getKeepAllRule() != null;
      ImmutableSet<ProguardKeepRuleBase> keepAllSet =
          ImmutableSet.of(appView.options().getProguardConfiguration().getKeepAllRule());
      for (DexProgramClass dexProgramClass : appView.appInfo().classes()) {
        for (DexEncodedMethod method : dexProgramClass.methods()) {
          this.enqueueRootItem(method, keepAllSet);
        }
        for (DexEncodedField field : dexProgramClass.fields()) {
          this.enqueueRootItem(field, keepAllSet);
        }
        this.enqueueRootItem(dexProgramClass, keepAllSet);
      }
    }
    trace(executorService, timing);
    options.reporter.failIfPendingErrors();
    finalizeLibraryMethodOverrideInformation();
    analyses.forEach(analyses -> analyses.done(this));
    assert verifyKeptGraph();
    if (mode.isWhyAreYouKeeping()) {
      // For why are you keeping the information is reported through the kept graph callbacks and
      // no AppInfo is returned.
      return null;
    }
    AppInfoWithLiveness appInfoWithLiveness = createAppInfo(appInfo);
    if (options.testing.enqueuerInspector != null) {
      options.testing.enqueuerInspector.accept(appInfoWithLiveness, mode);
    }
    return appInfoWithLiveness;
  }

  public NestedGraphLens buildGraphLens() {
    return lambdaRewriter != null ? lambdaRewriter.fixup() : null;
  }

  private void keepClassWithRules(DexProgramClass clazz, Set<ProguardKeepRuleBase> rules) {
    keepInfo.joinClass(clazz, info -> applyKeepRules(clazz, rules, info));
  }

  private void keepMethodWithRules(ProgramMethod method, Set<ProguardKeepRuleBase> rules) {
    keepInfo.joinMethod(method, info -> applyKeepRules(method, rules, info));
  }

  private void keepFieldWithRules(ProgramField field, Set<ProguardKeepRuleBase> rules) {
    keepInfo.joinField(field, info -> applyKeepRules(field, rules, info));
  }

  private void applyKeepRules(
      ProgramDefinition definition,
      Set<ProguardKeepRuleBase> rules,
      KeepInfo.Joiner<?, ?, ?> joiner) {
    for (ProguardKeepRuleBase rule : rules) {
      ProguardKeepRuleModifiers modifiers =
          (rule.isProguardIfRule() ? rule.asProguardIfRule().getSubsequentRule() : rule)
              .getModifiers();
      if (!modifiers.allowsShrinking) {
        // TODO(b/159589281): Evaluate this interpretation.
        joiner.pin();
        if (definition.getAccessFlags().isPackagePrivateOrProtected()) {
          joiner.requireAccessModificationForRepackaging();
        }
      }
      if (!modifiers.allowsObfuscation) {
        joiner.disallowMinification();
      }
      if (!modifiers.allowsAccessModification) {
        joiner.disallowAccessModification();
      }
    }
  }

  private static class SyntheticAdditions {

    Map<DexType, Pair<DexProgramClass, ProgramMethod>> syntheticInstantiations =
        new IdentityHashMap<>();

    Map<DexMethod, ProgramMethod> liveMethods = new IdentityHashMap<>();

    Map<DexType, DexClasspathClass> syntheticClasspathClasses = new IdentityHashMap<>();

    // Subset of live methods that need have keep requirements.
    List<Pair<ProgramMethod, Consumer<KeepMethodInfo.Joiner>>> liveMethodsWithKeepActions =
        new ArrayList<>();

    // Subset of synthesized classes that need to be added to the main-dex file.
    Set<DexProgramClass> mainDexTypes = Sets.newIdentityHashSet();

    boolean isEmpty() {
      boolean empty =
          syntheticInstantiations.isEmpty()
              && liveMethods.isEmpty()
              && syntheticClasspathClasses.isEmpty();
      assert !empty || (liveMethodsWithKeepActions.isEmpty() && mainDexTypes.isEmpty());
      return empty;
    }

    void addInstantiatedClass(
        DexProgramClass clazz, ProgramMethod context, boolean isMainDexClass) {
      assert !syntheticInstantiations.containsKey(clazz.type);
      syntheticInstantiations.put(clazz.type, new Pair<>(clazz, context));
      if (isMainDexClass) {
        mainDexTypes.add(clazz);
      }
    }

    void addClasspathClass(DexClasspathClass clazz) {
      DexClasspathClass old = syntheticClasspathClasses.put(clazz.type, clazz);
      assert old == null;
    }

    void addLiveMethod(ProgramMethod method) {
      DexMethod signature = method.getDefinition().method;
      assert !liveMethods.containsKey(signature);
      liveMethods.put(signature, method);
    }

    void addLiveMethodWithKeepAction(
        ProgramMethod method, Consumer<KeepMethodInfo.Joiner> keepAction) {
      addLiveMethod(method);
      liveMethodsWithKeepActions.add(new Pair<>(method, keepAction));
    }

    void amendApplication(Builder appBuilder) {
      assert !isEmpty();
      for (Pair<DexProgramClass, ProgramMethod> clazzAndContext :
          syntheticInstantiations.values()) {
        appBuilder.addProgramClass(clazzAndContext.getFirst());
      }
      appBuilder.addClasspathClasses(syntheticClasspathClasses.values());
    }

    void amendMainDexClasses(MainDexClasses mainDexClasses) {
      assert !isEmpty();
      mainDexClasses.addAll(mainDexTypes);
    }

    void enqueueWorkItems(Enqueuer enqueuer) {
      assert !isEmpty();
      assert enqueuer.mode.isInitialTreeShaking();
      // All synthetic additions are initial tree shaking only. No need to track keep reasons.
      KeepReasonWitness fakeReason = enqueuer.graphReporter.fakeReportShouldNotBeUsed();

      liveMethodsWithKeepActions.forEach(
          item -> enqueuer.keepInfo.joinMethod(item.getFirst(), item.getSecond()));
      for (Pair<DexProgramClass, ProgramMethod> clazzAndContext :
          syntheticInstantiations.values()) {
        enqueuer.workList.enqueueMarkInstantiatedAction(
            clazzAndContext.getFirst(),
            clazzAndContext.getSecond(),
            InstantiationReason.SYNTHESIZED_CLASS,
            fakeReason);
      }
      for (ProgramMethod liveMethod : liveMethods.values()) {
        assert !enqueuer.targetedMethods.contains(liveMethod.getDefinition());
        enqueuer.markMethodAsTargeted(liveMethod, fakeReason);
        enqueuer.enqueueMarkMethodLiveAction(liveMethod, fakeReason);
      }
      enqueuer.liveNonProgramTypes.addAll(syntheticClasspathClasses.values());
    }
  }

  private void synthesize() {
    if (!mode.isInitialTreeShaking()) {
      return;
    }
    // First part of synthesis is to create and register all reachable synthetic additions.
    // In particular these additions are order independent, i.e., it does not matter which are
    // registered first and no dependencies may exist among them.
    SyntheticAdditions additions = new SyntheticAdditions();
    synthesizeInterfaceMethodBridges(additions);
    synthesizeLambdas(additions);
    synthesizeLibraryConversionWrappers(additions);
    synthesizeBackports(additions);
    if (additions.isEmpty()) {
      return;
    }

    // Now all additions are computed, the application is atomically extended with those additions.
    appInfo =
        appInfo.rebuildWithClassHierarchy(
            app -> {
              Builder appBuilder = app.asDirect().builder();
              additions.amendApplication(appBuilder);
              return appBuilder.build();
            });
    additions.amendMainDexClasses(appInfo.getMainDexClasses());
    appView.setAppInfo(appInfo);
    subtypingInfo = new SubtypingInfo(appView);

    // Finally once all synthesized items "exist" it is now safe to continue tracing. The new work
    // items are enqueued and the fixed point will continue once this subroutine returns.
    additions.enqueueWorkItems(this);
  }

  private void synthesizeInterfaceMethodBridges(SyntheticAdditions additions) {
    for (ProgramMethod bridge : syntheticInterfaceMethodBridges.values()) {
      DexProgramClass holder = bridge.getHolder();
      DexEncodedMethod method = bridge.getDefinition();
      holder.addVirtualMethod(method);
      additions.addLiveMethodWithKeepAction(bridge, KeepMethodInfo.Joiner::pin);
    }
    syntheticInterfaceMethodBridges.clear();
  }

  private void synthesizeBackports(SyntheticAdditions additions) {
    for (ProgramMethod method : methodsWithBackports.values()) {
      backportRewriter.desugar(method, appInfo, additions::addLiveMethod);
    }
  }

  private void synthesizeLambdas(SyntheticAdditions additions) {
    if (lambdaRewriter == null || lambdaClasses.isEmpty()) {
      assert lambdaCallSites.isEmpty();
      assert classesWithSerializableLambdas.isEmpty();
      return;
    }
    for (Pair<LambdaClass, ProgramMethod> lambdaClassAndContext : lambdaClasses.values()) {
      // Add all desugared classes to the application, main-dex list, and mark them instantiated.
      LambdaClass lambdaClass = lambdaClassAndContext.getFirst();
      ProgramMethod context = lambdaClassAndContext.getSecond();
      DexProgramClass programClass = lambdaClass.getOrCreateLambdaClass();
      additions.addInstantiatedClass(programClass, context, lambdaClass.addToMainDexList.get());
      // Mark the instance constructor targeted and live.
      DexEncodedMethod constructor = programClass.lookupDirectMethod(lambdaClass.constructor);
      KeepReason reason = KeepReason.instantiatedIn(context);
      ProgramMethod method = new ProgramMethod(programClass, constructor);
      markMethodAsTargeted(method, reason);
      markDirectStaticOrConstructorMethodAsLive(method, reason);
    }

    // Rewrite all of the invoke-dynamic instructions to lambda class instantiations.
    lambdaCallSites.forEach(this::rewriteLambdaCallSites);

    // Remove all '$deserializeLambda$' methods which are not supported by desugaring.
    for (DexProgramClass clazz : classesWithSerializableLambdas) {
      clazz.removeMethod(appView.dexItemFactory().deserializeLambdaMethod);
    }

    // Clear state before next fixed point iteration.
    lambdaClasses.clear();
    lambdaCallSites.clear();
    classesWithSerializableLambdas.clear();
  }

  private void finalizeLibraryMethodOverrideInformation() {
    for (DexProgramClass liveType : liveTypes.getItems()) {
      for (DexEncodedMethod method : liveType.virtualMethods()) {
        if (method.isLibraryMethodOverride().isUnknown()) {
          method.setLibraryMethodOverride(OptionalBool.FALSE);
        }
      }
    }
  }

  private boolean verifyKeptGraph() {
    if (appView.options().testing.verifyKeptGraphInfo) {
      for (DexProgramClass liveType : liveTypes.getItems()) {
        assert graphReporter.verifyRootedPath(liveType);
      }
    }
    return true;
  }

  private AppInfoWithLiveness createAppInfo(AppInfoWithClassHierarchy appInfo) {
    // Once all tracing is done, we generate accessor methods for lambdas.
    // These are assumed to be simple forwarding or access flag updates, thus no further tracing
    // is needed. These cannot be generated as part of lambda synthesis as changing a direct method
    // to a static method will invalidate the reachable method sets for tracing methods.
    ensureLambdaAccessibility();

    // Compute the set of dead proto types.
    deadProtoTypeCandidates.removeIf(this::isTypeLive);
    Set<DexType> deadProtoTypes =
        SetUtils.newIdentityHashSet(deadProtoTypeCandidates.size() + initialDeadProtoTypes.size());
    deadProtoTypeCandidates.forEach(deadProtoType -> deadProtoTypes.add(deadProtoType.type));
    deadProtoTypes.addAll(initialDeadProtoTypes);

    // Remove the temporary mappings that have been inserted into the field access info collection
    // and verify that the mapping is then one-to-one.
    fieldAccessInfoCollection.removeIf(
        (field, info) -> field != info.getField() || info == MISSING_FIELD_ACCESS_INFO);
    assert fieldAccessInfoCollection.verifyMappingIsOneToOne();

    // Verify all references on the input app before synthesizing definitions.
    assert verifyReferences(appInfo.app());

    // Prune the root set items that turned out to be dead.
    // TODO(b/150736225): Pruning of dead root set items is still incomplete.
    rootSet.pruneDeadItems(appView, this);

    // Ensure references from all hard coded factory items.
    appView.dexItemFactory().forEachPossiblyCompilerSynthesizedType(this::recordTypeReference);

    // Rebuild a new app only containing referenced types.
    Set<DexLibraryClass> libraryClasses = Sets.newIdentityHashSet();
    Set<DexClasspathClass> classpathClasses = Sets.newIdentityHashSet();
    for (DexClass clazz : liveNonProgramTypes) {
      if (clazz.isLibraryClass()) {
        libraryClasses.add(clazz.asLibraryClass());
      } else if (clazz.isClasspathClass()) {
        classpathClasses.add(clazz.asClasspathClass());
      } else {
        assert false;
      }
    }

    // Add just referenced non-program types. We can't replace the program classes at this point as
    // they are needed in tree pruning.
    Builder appBuilder = appInfo.app().asDirect().builder();
    appBuilder.replaceLibraryClasses(libraryClasses);
    appBuilder.replaceClasspathClasses(classpathClasses);
    DirectMappedDexApplication app = appBuilder.build();

    // Verify the references on the pruned application after type synthesis.
    assert verifyReferences(app);
    assert verifyMissingTypes();

    AppInfoWithLiveness appInfoWithLiveness =
        new AppInfoWithLiveness(
            appInfo.getSyntheticItems().commit(app),
            appInfo.getClassToFeatureSplitMap(),
            appInfo.getMainDexClasses(),
            deadProtoTypes,
            mode.isFinalTreeShaking()
                ? Sets.union(initialMissingTypes, missingTypes)
                : missingTypes,
            SetUtils.mapIdentityHashSet(liveTypes.getItems(), DexProgramClass::getType),
            Collections.unmodifiableSet(instantiatedAppServices),
            Enqueuer.toDescriptorSet(targetedMethods.getItems()),
            Collections.unmodifiableSet(failedResolutionTargets),
            Collections.unmodifiableSet(bootstrapMethods),
            Collections.unmodifiableSet(methodsTargetedByInvokeDynamic),
            Collections.unmodifiableSet(virtualMethodsTargetedByInvokeDirect),
            toDescriptorSet(liveMethods.getItems()),
            // Filter out library fields and pinned fields, because these are read by default.
            fieldAccessInfoCollection,
            methodAccessInfoCollection.build(),
            objectAllocationInfoCollection.build(appInfo),
            callSites,
            keepInfo,
            rootSet.mayHaveSideEffects,
            rootSet.noSideEffects,
            rootSet.assumedValues,
            rootSet.alwaysInline,
            rootSet.forceInline,
            rootSet.neverInline,
            rootSet.whyAreYouNotInlining,
            rootSet.keepConstantArguments,
            rootSet.keepUnusedArguments,
            rootSet.reprocess,
            rootSet.neverReprocess,
            rootSet.alwaysClassInline,
            rootSet.neverClassInline,
            rootSet.noUnusedInterfaceRemoval,
            rootSet.noVerticalClassMerging,
            rootSet.noHorizontalClassMerging,
            rootSet.noStaticClassMerging,
            rootSet.neverPropagateValue,
            joinIdentifierNameStrings(rootSet.identifierNameStrings, identifierNameStrings),
            Collections.emptySet(),
            Collections.emptyMap(),
            EnumValueInfoMapCollection.empty(),
            lockCandidates,
            initClassReferences);
    appInfo.markObsolete();
    return appInfoWithLiveness;
  }

  private void ensureLambdaAccessibility() {
    if (lambdaRewriter == null) {
      return;
    }
    lambdaRewriter
        .getKnownLambdaClasses()
        .forEach(
            (type, lambda) -> {
              DexProgramClass synthesizedClass = getProgramClassOrNull(type);
              assert synthesizedClass != null;
              assert liveTypes.contains(synthesizedClass);
              if (synthesizedClass == null) {
                return;
              }
              DexMethod method = lambda.descriptor.getMainMethod();
              if (!liveMethods.contains(synthesizedClass.lookupMethod(method))) {
                return;
              }
              ProgramMethod accessor = lambda.target.ensureAccessibilityIfNeeded(false);
              if (accessor != null) {
                liveMethods.add(accessor, graphReporter.fakeReportShouldNotBeUsed());
              }
            });
    unpinLambdaMethods();
  }

  // TODO(b/157700141): Determine if this is the right way to allow modification of pinned lambdas.
  private void unpinLambdaMethods() {
    assert lambdaRewriter != null;
    for (DexMethod method : lambdaRewriter.getForcefullyMovedMethods()) {
      keepInfo.unsafeUnpinMethod(method);
      rootSet.prune(method);
    }
  }

  private boolean verifyMissingTypes() {
    if (initialMissingTypes == null) {
      assert !mode.isFinalTreeShaking();
      return true;
    }
    missingTypes.forEach(
        missingType -> {
          assert initialMissingTypes.contains(missingType)
                  // TODO(b/157107464): See if we can clean this up.
                  || initialPrunedTypes.contains(missingType)
                  || missingType.isD8R8SynthesizedClassType()
              : missingType;
        });
    return true;
  }

  private boolean verifyReferences(DexApplication app) {
    WorkList<DexClass> worklist = WorkList.newIdentityWorkList();
    for (DexProgramClass clazz : liveTypes.getItems()) {
      worklist.addIfNotSeen(clazz);
    }
    while (worklist.hasNext()) {
      DexClass clazz = worklist.next();
      assert verifyReferencedType(clazz, worklist, app);
    }
    return true;
  }

  private boolean verifyReferencedType(
      DexType type, WorkList<DexClass> worklist, DexApplication app) {
    if (type.isArrayType()) {
      type = type.toBaseType(appView.dexItemFactory());
    }
    if (!type.isClassType()) {
      return true;
    }
    DexClass clazz = app.definitionFor(type);
    if (clazz == null) {
      assert missingTypes.contains(type) : "Expected type to be in missing types': " + type;
    } else {
      assert !missingTypes.contains(type) : "Type with definition also in missing types: " + type;
      // Eager assert while the context is still present.
      assert clazz.isProgramClass() || liveNonProgramTypes.contains(clazz)
          : "Expected type to be in live non-program types: " + clazz;
      worklist.addIfNotSeen(clazz);
    }
    return true;
  }

  private boolean verifyReferencedType(
      DexClass clazz, WorkList<DexClass> worklist, DexApplication app) {
    for (DexType supertype : clazz.allImmediateSupertypes()) {
      assert verifyReferencedType(supertype, worklist, app);
    }
    assert clazz.isProgramClass() || liveNonProgramTypes.contains(clazz)
        : "Expected type to be in live non-program types: " + clazz;
    for (DexEncodedField field : clazz.fields()) {
      if (clazz.isNotProgramClass() || isFieldReferenced(field)) {
        assert verifyReferencedType(field.field.type, worklist, app);
      }
    }
    for (DexEncodedMethod method : clazz.methods()) {
      if (clazz.isNotProgramClass() || isMethodTargeted(method)) {
        assert verifyReferencedMethod(method, worklist, app);
      }
    }
    return true;
  }

  private boolean verifyReferencedMethod(
      DexEncodedMethod method, WorkList<DexClass> worklist, DexApplication app) {
    assert verifyReferencedType(method.method.proto.returnType, worklist, app);
    for (DexType param : method.method.proto.parameters.values) {
      assert verifyReferencedType(param, worklist, app);
    }
    return true;
  }

  private void synthesizeLibraryConversionWrappers(SyntheticAdditions additions) {
    if (desugaredLibraryWrapperAnalysis == null) {
      return;
    }

    // Generate first the callbacks since they may require extra wrappers.
    ProgramMethodSet callbacks = desugaredLibraryWrapperAnalysis.generateCallbackMethods();
    callbacks.forEach(additions::addLiveMethod);

    // Generate wrappers on classpath so types are defined.
    desugaredLibraryWrapperAnalysis.generateWrappers(additions::addClasspathClass);
  }

  private void rewriteLambdaCallSites(
      DexEncodedMethod method, Map<DexCallSite, LambdaClass> callSites) {
    assert !callSites.isEmpty();
    int replaced = LambdaRewriter.desugarLambdas(method, callSites::get);
    assert replaced == callSites.size();
  }

  private static <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
      Set<R> toDescriptorSet(Set<D> set) {
    ImmutableSet.Builder<R> builder = new ImmutableSet.Builder<>();
    for (D item : set) {
      builder.add(item.getReference());
    }
    return builder.build();
  }

  private static Object2BooleanMap<DexReference> joinIdentifierNameStrings(
      Set<DexReference> explicit, Set<DexReference> implicit) {
    Object2BooleanMap<DexReference> result = new Object2BooleanArrayMap<>();
    for (DexReference e : explicit) {
      result.putIfAbsent(e, true);
    }
    for (DexReference i : implicit) {
      result.putIfAbsent(i, false);
    }
    return result;
  }

  private void trace(ExecutorService executorService, Timing timing) throws ExecutionException {
    timing.begin("Grow the tree.");
    try {
      while (true) {
        long numberOfLiveItems = getNumberOfLiveItems();
        while (!workList.isEmpty()) {
          EnqueuerAction action = workList.poll();
          action.run(this);
        }

        // Continue fix-point processing if -if rules are enabled by items that newly became live.
        long numberOfLiveItemsAfterProcessing = getNumberOfLiveItems();
        if (numberOfLiveItemsAfterProcessing > numberOfLiveItems) {
          // Build the mapping of active if rules. We use a single collection of if-rules to allow
          // removing if rules that have a constant sequent keep rule when they materialize.
          if (activeIfRules == null) {
            activeIfRules = new HashMap<>();
            IfRuleClassPartEquivalence equivalence = new IfRuleClassPartEquivalence();
            for (ProguardIfRule ifRule : rootSet.ifRules) {
              Wrapper<ProguardIfRule> wrap = equivalence.wrap(ifRule);
              activeIfRules.computeIfAbsent(wrap, ignore -> new LinkedHashSet<>()).add(ifRule);
            }
          }
          ConsequentRootSetBuilder consequentSetBuilder =
              new ConsequentRootSetBuilder(appView, subtypingInfo, this);
          IfRuleEvaluator ifRuleEvaluator =
              new IfRuleEvaluator(
                  appView,
                  subtypingInfo,
                  this,
                  executorService,
                  activeIfRules,
                  consequentSetBuilder);
          addConsequentRootSet(ifRuleEvaluator.run(), false);
          assert getNumberOfLiveItems() == numberOfLiveItemsAfterProcessing;
          if (!workList.isEmpty()) {
            continue;
          }
        }

        // Continue fix-point processing while there are additional work items to ensure items that
        // are passed to Java reflections are traced.
        if (!pendingReflectiveUses.isEmpty()) {
          pendingReflectiveUses.forEach(this::handleReflectiveBehavior);
          pendingReflectiveUses.clear();
        }
        if (!workList.isEmpty()) {
          continue;
        }

        // Notify each analysis that a fixpoint has been reached, and give each analysis an
        // opportunity to add items to the worklist.
        analyses.forEach(analysis -> analysis.notifyFixpoint(this, workList, timing));
        if (!workList.isEmpty()) {
          continue;
        }

        addConsequentRootSet(computeDelayedInterfaceMethodSyntheticBridges(), true);
        rootSet.delayedRootSetActionItems.clear();

        if (!workList.isEmpty()) {
          continue;
        }

        synthesize();
        if (!workList.isEmpty()) {
          continue;
        }

        // Reached the fixpoint.
        break;
      }

      if (Log.ENABLED) {
        Set<DexEncodedMethod> allLive = Sets.newIdentityHashSet();
        Set<DexEncodedMethod> reachableNotLive = Sets.difference(allLive, liveMethods.getItems());
        Log.debug(getClass(), "%s methods are reachable but not live", reachableNotLive.size());
        Log.info(getClass(), "Only reachable: %s", reachableNotLive);
        SetView<DexEncodedMethod> targetedButNotLive = Sets
            .difference(targetedMethods.getItems(), liveMethods.getItems());
        Log.debug(getClass(), "%s methods are targeted but not live", targetedButNotLive.size());
        Log.info(getClass(), "Targeted but not live: %s", targetedButNotLive);
      }
    } finally {
      timing.end();
    }
  }

  private long getNumberOfLiveItems() {
    long result = liveTypes.items.size();
    result += liveMethods.items.size();
    result += liveFields.fields.size();
    return result;
  }

  private void addConsequentRootSet(ConsequentRootSet consequentRootSet, boolean addNoShrinking) {
    consequentRootSet.forEachClassWithDependentItems(
        appView,
        clazz -> {
          if (isTypeLive(clazz)) {
            consequentRootSet.forEachDependentInstanceConstructor(
                clazz, appView, this::enqueueHolderWithDependentInstanceConstructor);
            consequentRootSet.forEachDependentStaticMember(
                clazz, appView, this::enqueueDependentMember);
            if (objectAllocationInfoCollection.isInstantiatedDirectlyOrHasInstantiatedSubtype(
                clazz)) {
              consequentRootSet.forEachDependentNonStaticMember(
                  clazz, appView, this::enqueueDependentMember);
            }
            compatEnqueueHolderIfDependentNonStaticMember(
                clazz, consequentRootSet.getDependentKeepClassCompatRule(clazz.type));
          }
        });
    consequentRootSet.forEachMemberWithDependentItems(
        appView,
        (member, dependentItems) -> {
          if (isMemberLive(member)) {
            enqueueRootItems(dependentItems);
          }
        });
    consequentRootSet.dependentSoftPinned.forEach(
        (reference, dependentItems) -> {
          if (isLiveProgramReference(reference)) {
            dependentItems.forEachReference(
                item -> {
                  if (isLiveProgramReference(item)) {
                    keepInfo.joinInfo(item, appView, Joiner::pin);
                  }
                });
          }
        });

    // TODO(b/132600955): This modifies the root set. Should the consequent be persistent?
    rootSet.addConsequentRootSet(consequentRootSet, addNoShrinking);
    if (mode.isInitialTreeShaking()) {
      for (DexReference reference : consequentRootSet.noObfuscation) {
        keepInfo.evaluateRule(reference, appView, Joiner::disallowMinification);
      }
      consequentRootSet.softPinned.forEachReference(
          reference -> keepInfo.evaluateRule(reference, appView, Joiner::pin));
    }
    enqueueRootItems(consequentRootSet.noShrinking);
    // Check for compatibility rules indicating that the holder must be implicitly kept.
    if (forceProguardCompatibility) {
      consequentRootSet.dependentKeepClassCompatRule.forEach(
          (precondition, compatRules) -> {
            assert precondition.isDexType();
            DexProgramClass preconditionHolder = getProgramClassOrNull(precondition.asDexType());
            compatEnqueueHolderIfDependentNonStaticMember(preconditionHolder, compatRules);
          });
    }
  }

  private boolean isLiveProgramReference(DexReference reference) {
    if (reference.isDexType()) {
      DexProgramClass clazz =
          DexProgramClass.asProgramClassOrNull(definitionFor(reference.asDexType()));
      return clazz != null && isTypeLive(clazz);
    }
    DexMember<?, ?> member = reference.asDexMember();
    DexProgramClass holder = DexProgramClass.asProgramClassOrNull(definitionFor(member.holder));
    ProgramMember<?, ?> programMember = member.lookupOnProgramClass(holder);
    return programMember != null && isMemberLive(programMember.getDefinition());
  }

  private ConsequentRootSet computeDelayedInterfaceMethodSyntheticBridges() {
    RootSetBuilder builder = new RootSetBuilder(appView, subtypingInfo);
    for (DelayedRootSetActionItem delayedRootSetActionItem : rootSet.delayedRootSetActionItems) {
      if (delayedRootSetActionItem.isInterfaceMethodSyntheticBridgeAction()) {
        handleInterfaceMethodSyntheticBridgeAction(
            delayedRootSetActionItem.asInterfaceMethodSyntheticBridgeAction(), builder);
      }
    }
    return builder.buildConsequentRootSet();
  }

  private final Map<DexMethod, ProgramMethod> syntheticInterfaceMethodBridges =
      new LinkedHashMap<>();

  private void handleInterfaceMethodSyntheticBridgeAction(
      InterfaceMethodSyntheticBridgeAction action, RootSetBuilder builder) {
    ProgramMethod methodToKeep = action.getMethodToKeep();
    ProgramMethod singleTarget = action.getSingleTarget();
    DexEncodedMethod singleTargetMethod = singleTarget.getDefinition();
    if (rootSet.noShrinking.containsMethod(singleTarget.getReference())) {
      return;
    }
    if (methodToKeep != singleTarget) {
      assert null == methodToKeep.getHolder().lookupMethod(methodToKeep.getDefinition().method);
      ProgramMethod old =
          syntheticInterfaceMethodBridges.put(methodToKeep.getDefinition().method, methodToKeep);
      if (old == null) {
        if (singleTargetMethod.isLibraryMethodOverride().isTrue()) {
          methodToKeep.getDefinition().setLibraryMethodOverride(OptionalBool.TRUE);
        }
        DexProgramClass singleTargetHolder = singleTarget.getHolder();
        assert singleTargetHolder.isInterface();
        markVirtualMethodAsReachable(
            singleTargetMethod.method,
            singleTargetHolder.isInterface(),
            null,
            graphReporter.fakeReportShouldNotBeUsed());
        enqueueMarkMethodLiveAction(singleTarget, graphReporter.fakeReportShouldNotBeUsed());
      }
    }
    action.getAction().accept(builder);
  }

  void retainAnnotationForFinalTreeShaking(List<DexAnnotation> annotations) {
    assert mode.isInitialTreeShaking();
    if (annotationRemoverBuilder != null) {
      annotations.forEach(annotationRemoverBuilder::retainAnnotation);
    }
  }

  // Package protected due to entry point from worklist.
  void markMethodAsKept(ProgramMethod target, KeepReason reason) {
    DexEncodedMethod definition = target.getDefinition();
    DexProgramClass holder = target.getHolder();
    DexMethod reference = target.getReference();
    if (definition.isVirtualMethod()) {
      // A virtual method. Mark it as reachable so that subclasses, if instantiated, keep
      // their overrides. However, we don't mark it live, as a keep rule might not imply that
      // the corresponding class is live.
      markVirtualMethodAsReachable(reference, holder.isInterface(), null, reason);
      if (holder.isInterface()) {
        // Reachability for default methods is based on live subtypes in general. For keep rules,
        // we need special handling as we essentially might have live subtypes that are outside of
        // the current compilation unit. Keep either the default-method or its implementation
        // method.
        // TODO(b/120959039): Codify the kept-graph expectations for these cases in tests.
        if (definition.isNonAbstractVirtualMethod()) {
          markVirtualMethodAsLive(target, reason);
        } else {
          DexEncodedMethod implementation = definition.getDefaultInterfaceMethodImplementation();
          if (implementation != null) {
            DexProgramClass companion = getProgramClassOrNull(implementation.holder());
            markTypeAsLive(companion, graphReporter.reportCompanionClass(holder, companion));
            markVirtualMethodAsLive(
                new ProgramMethod(companion, implementation),
                graphReporter.reportCompanionMethod(definition, implementation));
          }
        }
      }
    } else {
      markMethodAsTargeted(target, reason);
      markDirectStaticOrConstructorMethodAsLive(target, reason);
    }
  }

  // Package protected due to entry point from worklist.
  void markFieldAsKept(ProgramField field, KeepReason reason) {
    if (field.getDefinition().isStatic()) {
      markStaticFieldAsLive(field, reason);
    } else {
      markInstanceFieldAsReachable(field, reason);
    }
  }

  private boolean shouldMarkLibraryMethodOverrideAsReachable(LookupTarget override) {
    if (override.isLambdaTarget()) {
      return true;
    }
    ProgramMethod programMethod = override.asMethodTarget().asProgramMethod();
    if (programMethod == null) {
      return false;
    }
    DexProgramClass clazz = programMethod.getHolder();
    DexEncodedMethod method = programMethod.getDefinition();
    assert method.isVirtualMethod();

    if (method.isAbstract() || method.isPrivateMethod()) {
      return false;
    }

    if (appView.isClassEscapingIntoLibrary(clazz.type)) {
      return true;
    }

    // If there is an instantiated subtype of `clazz` that escapes into the library and does not
    // override `method` then we need to mark the method as being reachable.
    Set<DexProgramClass> immediateSubtypes = getImmediateSubtypesInInstantiatedHierarchy(clazz);
    if (immediateSubtypes.isEmpty()) {
      return false;
    }
    Deque<DexProgramClass> worklist = new ArrayDeque<>(immediateSubtypes);
    Set<DexProgramClass> visited = SetUtils.newIdentityHashSet(immediateSubtypes);

    while (!worklist.isEmpty()) {
      DexProgramClass current = worklist.removeFirst();
      assert visited.contains(current);

      if (current.lookupVirtualMethod(method.method) != null) {
        continue;
      }

      if (appView.isClassEscapingIntoLibrary(current.type)) {
        return true;
      }

      for (DexProgramClass subtype : getImmediateSubtypesInInstantiatedHierarchy(current)) {
        if (visited.add(subtype)) {
          worklist.add(subtype);
        }
      }
    }

    return false;
  }

  private Set<DexProgramClass> getImmediateSubtypesInInstantiatedHierarchy(DexProgramClass clazz) {
    Set<DexClass> subtypes =
        objectAllocationInfoCollection.getImmediateSubtypesInInstantiatedHierarchy(clazz.type);
    if (subtypes == null) {
      return Collections.emptySet();
    }
    Set<DexProgramClass> programClasses = SetUtils.newIdentityHashSet(subtypes.size());
    for (DexClass subtype : subtypes) {
      if (subtype.isProgramClass()) {
        programClasses.add(subtype.asProgramClass());
      }
    }
    return programClasses;
  }

  // Package protected due to entry point from worklist.
  void markMethodAsLive(ProgramMethod method, KeepReason reason) {
    DexProgramClass holder = method.getHolder();
    DexEncodedMethod definition = method.getDefinition();

    assert liveMethods.contains(definition);

    if (definition.isStatic()) {
      markDirectAndIndirectClassInitializersAsLive(method.getHolder());
    }

    ProgramMethodSet superCallTargets = superInvokeDependencies.get(method.getDefinition());
    if (superCallTargets != null) {
      for (ProgramMethod superCallTarget : superCallTargets) {
        if (Log.ENABLED) {
          Log.verbose(getClass(), "Found super invoke constraint on `%s`.", superCallTarget);
        }
        markMethodAsTargeted(superCallTarget, KeepReason.invokedViaSuperFrom(method));
        markVirtualMethodAsLive(superCallTarget, KeepReason.invokedViaSuperFrom(method));
      }
    }
    markParameterAndReturnTypesAsLive(method);
    processAnnotations(holder, definition);
    definition.parameterAnnotationsList.forEachAnnotation(
        annotation -> processAnnotation(holder, definition, annotation));
    method.registerCodeReferences(useRegistryFactory.create(appView, method, this));

    // Add all dependent members to the workqueue.
    enqueueRootItems(rootSet.getDependentItems(definition));

    checkMemberForSoftPinning(method);

    // Notify analyses.
    analyses.forEach(analysis -> analysis.processNewlyLiveMethod(method));
  }

  private void checkMemberForSoftPinning(ProgramMember<?, ?> member) {
    DexMember<?, ?> reference = member.getDefinition().getReference();
    Set<ProguardKeepRuleBase> softPinRules = rootSet.softPinned.getRulesForReference(reference);
    if (softPinRules != null) {
      assert softPinRules.stream().noneMatch(r -> r.getModifiers().allowsOptimization);
      keepInfo.joinInfo(reference, appInfo, Joiner::pin);
    }
    // Identify dependent soft pinning.
    MutableItemsWithRules items = rootSet.dependentSoftPinned.get(member.getHolderType());
    if (items != null && items.containsReference(reference)) {
      assert items.getRulesForReference(reference).stream()
          .noneMatch(r -> r.getModifiers().allowsOptimization);
      keepInfo.joinInfo(reference, appInfo, Joiner::pin);
    }
  }

  private void markReferencedTypesAsLive(ProgramMethod method) {
    markTypeAsLive(
        method.getHolderType(), clazz -> graphReporter.reportClassReferencedFrom(clazz, method));
    markParameterAndReturnTypesAsLive(method);
  }

  private void markParameterAndReturnTypesAsLive(ProgramMethod method) {
    for (DexType parameterType : method.getDefinition().parameters().values) {
      markTypeAsLive(
          parameterType, clazz -> graphReporter.reportClassReferencedFrom(clazz, method));
    }
    markTypeAsLive(
        method.getDefinition().returnType(),
        clazz -> graphReporter.reportClassReferencedFrom(clazz, method));
  }

  private void markClassAsInstantiatedWithReason(DexProgramClass clazz, KeepReason reason) {
    workList.enqueueMarkInstantiatedAction(clazz, null, InstantiationReason.REFLECTION, reason);
    if (clazz.hasDefaultInitializer()) {
      workList.enqueueMarkReachableDirectAction(clazz.getDefaultInitializer().method, reason);
    }
  }

  private void markClassAsInstantiatedWithCompatRule(
      DexProgramClass clazz, KeepReasonWitness witness) {
    if (clazz.isAnnotation()) {
      markTypeAsLive(clazz, witness);
    } else if (clazz.isInterface()) {
      markInterfaceAsInstantiated(clazz, witness);
    } else {
      workList.enqueueMarkInstantiatedAction(clazz, null, InstantiationReason.KEEP_RULE, witness);
      if (clazz.hasDefaultInitializer()) {
        ProgramMethod defaultInitializer = clazz.getProgramDefaultInitializer();
        workList.enqueueMarkReachableDirectAction(
            defaultInitializer.getReference(),
            graphReporter.reportCompatKeepDefaultInitializer(defaultInitializer));
      }
    }
  }

  private void markMethodAsLiveWithCompatRule(ProgramMethod method) {
    enqueueMarkMethodLiveAction(method, graphReporter.reportCompatKeepMethod(method));
  }

  private void handleReflectiveBehavior(ProgramMethod method) {
    IRCode code = method.buildIR(appView);
    InstructionIterator iterator = code.instructionIterator();
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      handleReflectiveBehavior(method, instruction);
    }
  }

  private void handleReflectiveBehavior(ProgramMethod method, Instruction instruction) {
    if (!instruction.isInvokeMethod()) {
      return;
    }
    InvokeMethod invoke = instruction.asInvokeMethod();
    DexMethod invokedMethod = invoke.getInvokedMethod();
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    if (invokedMethod == dexItemFactory.classMethods.newInstance) {
      handleJavaLangClassNewInstance(method, invoke);
      return;
    }
    if (invokedMethod == dexItemFactory.constructorMethods.newInstance) {
      handleJavaLangReflectConstructorNewInstance(method, invoke);
      return;
    }
    if (invokedMethod == dexItemFactory.enumMembers.valueOf) {
      handleJavaLangEnumValueOf(method, invoke);
      return;
    }
    if (invokedMethod == dexItemFactory.proxyMethods.newProxyInstance) {
      handleJavaLangReflectProxyNewProxyInstance(method, invoke);
      return;
    }
    if (dexItemFactory.serviceLoaderMethods.isLoadMethod(invokedMethod)) {
      handleServiceLoaderInvocation(method, invoke);
      return;
    }
    if (!isReflectionMethod(dexItemFactory, invokedMethod)) {
      return;
    }
    IdentifierNameStringLookupResult<?> identifierLookupResult =
        identifyIdentifier(invoke, appView, method);
    if (identifierLookupResult == null) {
      return;
    }
    DexReference referencedItem = identifierLookupResult.getReference();
    if (referencedItem.isDexType()) {
      assert identifierLookupResult.isTypeResult();
      IdentifierNameStringTypeLookupResult identifierTypeLookupResult =
          identifierLookupResult.asTypeResult();
      DexProgramClass clazz = getProgramClassOrNullFromReflectiveAccess(referencedItem.asDexType());
      if (clazz == null) {
        return;
      }
      markTypeAsLive(clazz, KeepReason.reflectiveUseIn(method));
      if (clazz.canBeInstantiatedByNewInstance()
          && identifierTypeLookupResult.isTypeInstantiatedFromUse(options)) {
        workList.enqueueMarkInstantiatedAction(
            clazz, null, InstantiationReason.REFLECTION, KeepReason.reflectiveUseIn(method));
        if (clazz.hasDefaultInitializer()) {
          ProgramMethod initializer = clazz.getProgramDefaultInitializer();
          KeepReason reason = KeepReason.reflectiveUseIn(method);
          markMethodAsTargeted(initializer, reason);
          markDirectStaticOrConstructorMethodAsLive(initializer, reason);
        }
      } else if (identifierTypeLookupResult.isTypeInitializedFromUse()) {
        markDirectAndIndirectClassInitializersAsLive(clazz);
      }
    } else if (referencedItem.isDexField()) {
      DexField field = referencedItem.asDexField();
      DexProgramClass clazz = getProgramClassOrNull(field.holder);
      if (clazz == null) {
        return;
      }
      DexEncodedField encodedField = clazz.lookupField(field);
      if (encodedField == null) {
        return;
      }
      // Normally, we generate a -keepclassmembers rule for the field, such that the field is only
      // kept if it is a static field, or if the holder or one of its subtypes are instantiated.
      // However, if the invoked method is a field updater, then we always need to keep instance
      // fields since the creation of a field updater throws a NoSuchFieldException if the field
      // is not present.
      boolean keepClass =
          !encodedField.isStatic()
              && dexItemFactory.atomicFieldUpdaterMethods.isFieldUpdater(invokedMethod);
      if (keepClass) {
        workList.enqueueMarkInstantiatedAction(
            clazz, null, InstantiationReason.REFLECTION, KeepReason.reflectiveUseIn(method));
      }
      if (!keepInfo.getFieldInfo(encodedField, clazz).isPinned()) {
        ProgramField programField = new ProgramField(clazz, encodedField);
        keepInfo.pinField(programField);
        markFieldAsKept(programField, KeepReason.reflectiveUseIn(method));
      }
    } else {
      assert referencedItem.isDexMethod();
      DexMethod targetedMethodReference = referencedItem.asDexMethod();
      DexProgramClass clazz = getProgramClassOrNull(targetedMethodReference.holder);
      if (clazz == null) {
        return;
      }
      DexEncodedMethod targetedMethodDefinition = clazz.lookupMethod(targetedMethodReference);
      if (targetedMethodDefinition == null) {
        return;
      }
      ProgramMethod targetedMethod = new ProgramMethod(clazz, targetedMethodDefinition);
      KeepReason reason = KeepReason.reflectiveUseIn(method);
      if (targetedMethodDefinition.isStatic() || targetedMethodDefinition.isInstanceInitializer()) {
        markMethodAsTargeted(targetedMethod, reason);
        markDirectStaticOrConstructorMethodAsLive(targetedMethod, reason);
      } else {
        markVirtualMethodAsLive(targetedMethod, reason);
      }
    }
  }

  /** Handles reflective uses of {@link Class#newInstance()}. */
  private void handleJavaLangClassNewInstance(ProgramMethod method, InvokeMethod invoke) {
    if (!invoke.isInvokeVirtual()) {
      assert false;
      return;
    }

    DexType instantiatedType =
        ConstantValueUtils.getDexTypeRepresentedByValue(
            invoke.asInvokeVirtual().getReceiver(), appView);
    if (instantiatedType == null || !instantiatedType.isClassType()) {
      // Give up, we can't tell which class is being instantiated, or the type is not a class type.
      // The latter should not happen in practice.
      return;
    }

    DexProgramClass clazz = getProgramClassOrNullFromReflectiveAccess(instantiatedType);
    if (clazz == null) {
      return;
    }
    ProgramMethod defaultInitializer = clazz.getProgramDefaultInitializer();
    if (defaultInitializer != null) {
      KeepReason reason = KeepReason.reflectiveUseIn(method);
      markClassAsInstantiatedWithReason(clazz, reason);
      markMethodAsTargeted(defaultInitializer, reason);
      markDirectStaticOrConstructorMethodAsLive(defaultInitializer, reason);
    }
  }

  /** Handles reflective uses of {@link java.lang.reflect.Constructor#newInstance(Object...)}. */
  private void handleJavaLangReflectConstructorNewInstance(
      ProgramMethod method, InvokeMethod invoke) {
    if (!invoke.isInvokeVirtual()) {
      assert false;
      return;
    }

    Value constructorValue = invoke.asInvokeVirtual().getReceiver().getAliasedValue();
    if (constructorValue.isPhi() || !constructorValue.definition.isInvokeVirtual()) {
      // Give up, we can't tell which class is being instantiated.
      return;
    }

    InvokeVirtual constructorDefinition = constructorValue.definition.asInvokeVirtual();
    DexMethod invokedMethod = constructorDefinition.getInvokedMethod();
    if (invokedMethod != appView.dexItemFactory().classMethods.getConstructor
        && invokedMethod != appView.dexItemFactory().classMethods.getDeclaredConstructor) {
      // Give up, we can't tell which constructor is being invoked.
      return;
    }

    DexType instantiatedType =
        ConstantValueUtils.getDexTypeRepresentedByValue(
            constructorDefinition.getReceiver(), appView);
    if (instantiatedType == null || !instantiatedType.isClassType()) {
      // Give up, we can't tell which constructor is being invoked, or the type is not a class type.
      // The latter should not happen in practice.
      return;
    }

    DexProgramClass clazz = getProgramClassOrNullFromReflectiveAccess(instantiatedType);
    if (clazz == null) {
      return;
    }
    Value parametersValue = constructorDefinition.inValues().get(1);
    if (parametersValue.isPhi() || !parametersValue.definition.isNewArrayEmpty()) {
      // Give up, we can't tell which constructor is being invoked.
      return;
    }

    Value parametersSizeValue = parametersValue.definition.asNewArrayEmpty().size();
    if (parametersSizeValue.isPhi() || !parametersSizeValue.definition.isConstNumber()) {
      // Give up, we can't tell which constructor is being invoked.
      return;
    }

    ProgramMethod initializer = null;

    int parametersSize = parametersSizeValue.definition.asConstNumber().getIntValue();
    if (parametersSize == 0) {
      initializer = clazz.getProgramDefaultInitializer();
    } else {
      DexType[] parameterTypes = new DexType[parametersSize];
      int missingIndices = parametersSize;
      for (Instruction user : parametersValue.uniqueUsers()) {
        if (user.isArrayPut()) {
          ArrayPut arrayPutInstruction = user.asArrayPut();
          if (arrayPutInstruction.array() != parametersValue) {
            return;
          }

          Value indexValue = arrayPutInstruction.index();
          if (indexValue.isPhi() || !indexValue.definition.isConstNumber()) {
            return;
          }
          int index = indexValue.definition.asConstNumber().getIntValue();
          if (index >= parametersSize) {
            return;
          }

          DexType type =
              ConstantValueUtils.getDexTypeRepresentedByValue(arrayPutInstruction.value(), appView);
          if (type == null) {
            return;
          }

          if (parameterTypes[index] == type) {
            continue;
          }
          if (parameterTypes[index] != null) {
            return;
          }
          parameterTypes[index] = type;
          missingIndices--;
        }
      }

      if (missingIndices == 0) {
        initializer = clazz.getProgramInitializer(parameterTypes);
      }
    }

    if (initializer != null) {
      KeepReason reason = KeepReason.reflectiveUseIn(method);
      markClassAsInstantiatedWithReason(clazz, reason);
      markMethodAsTargeted(initializer, reason);
      markDirectStaticOrConstructorMethodAsLive(initializer, reason);
    }
  }

  /**
   * Handles reflective uses of {@link java.lang.reflect.Proxy#newProxyInstance(ClassLoader,
   * Class[], InvocationHandler)}.
   */
  private void handleJavaLangReflectProxyNewProxyInstance(
      ProgramMethod method, InvokeMethod invoke) {
    if (!invoke.isInvokeStatic()) {
      assert false;
      return;
    }

    Value interfacesValue = invoke.arguments().get(1);
    if (interfacesValue.isPhi() || !interfacesValue.definition.isNewArrayEmpty()) {
      // Give up, we can't tell which interfaces the proxy implements.
      return;
    }

    for (Instruction user : interfacesValue.uniqueUsers()) {
      if (!user.isArrayPut()) {
        continue;
      }

      ArrayPut arrayPut = user.asArrayPut();
      DexType type = ConstantValueUtils.getDexTypeRepresentedByValue(arrayPut.value(), appView);
      if (type == null || !type.isClassType()) {
        continue;
      }

      DexProgramClass clazz = getProgramClassOrNull(type);
      if (clazz != null && clazz.isInterface()) {
        // Add this interface to the set of pinned items to ensure that we do not merge the
        // interface into its unique subtype, if any.
        // TODO(b/145344105): This should be superseded by the unknown interface hierarchy.
        keepInfo.pinClass(clazz);
        KeepReason reason = KeepReason.reflectiveUseIn(method);
        markInterfaceAsInstantiated(clazz, graphReporter.registerClass(clazz, reason));

        // Also pin all of its virtual methods to ensure that the devirtualizer does not perform
        // illegal rewritings of invoke-interface instructions into invoke-virtual instructions.
        clazz.forEachProgramVirtualMethod(
            virtualMethod -> {
              keepInfo.pinMethod(virtualMethod);
              markVirtualMethodAsReachable(virtualMethod.getReference(), true, null, reason);
            });
      }
    }
  }

  private void handleJavaLangEnumValueOf(ProgramMethod method, InvokeMethod invoke) {
    // The use of java.lang.Enum.valueOf(java.lang.Class, java.lang.String) will indirectly
    // access the values() method of the enum class passed as the first argument. The method
    // SomeEnumClass.valueOf(java.lang.String) which is generated by javac for all enums will
    // call this method.
    if (invoke.inValues().get(0).isConstClass()) {
      DexType type = invoke.inValues().get(0).definition.asConstClass().getValue();
      DexProgramClass clazz = getProgramClassOrNull(type);
      if (clazz != null && clazz.accessFlags.isEnum()) {
        markEnumValuesAsReachable(clazz, KeepReason.invokedFrom(method));
      }
    }
  }

  private void handleServiceLoaderInvocation(ProgramMethod method, InvokeMethod invoke) {
    if (invoke.inValues().size() == 0) {
      // Should never happen.
      return;
    }

    Value argument = invoke.inValues().get(0).getAliasedValue();
    if (!argument.isPhi() && argument.definition.isConstClass()) {
      DexType serviceType = argument.definition.asConstClass().getValue();
      if (!appView.appServices().allServiceTypes().contains(serviceType)) {
        // Should never happen.
        if (Log.ENABLED) {
          options.reporter.warning(
              new StringDiagnostic(
                  "The type `"
                      + serviceType.toSourceString()
                      + "` is being passed to the method `"
                      + invoke.getInvokedMethod().toSourceString()
                      + "`, but was not found in `META-INF/services/`.",
                  method.getOrigin()));
        }
        return;
      }

      handleServiceInstantiation(serviceType, KeepReason.reflectiveUseIn(method));
    } else {
      KeepReason reason = KeepReason.reflectiveUseIn(method);
      for (DexType serviceType : appView.appServices().allServiceTypes()) {
        handleServiceInstantiation(serviceType, reason);
      }
    }
  }

  private void handleServiceInstantiation(DexType serviceType, KeepReason reason) {
    instantiatedAppServices.add(serviceType);

    List<DexType> serviceImplementationTypes =
        appView.appServices().serviceImplementationsFor(serviceType);
    for (DexType serviceImplementationType : serviceImplementationTypes) {
      if (!serviceImplementationType.isClassType()) {
        // Should never happen.
        continue;
      }

      DexProgramClass serviceImplementationClass = getProgramClassOrNull(serviceImplementationType);
      if (serviceImplementationClass != null && serviceImplementationClass.isProgramClass()) {
        markClassAsInstantiatedWithReason(serviceImplementationClass, reason);
      }
    }
  }

  private static class SetWithReportedReason<T> {

    private final Set<T> items = Sets.newIdentityHashSet();
    private final Map<T, List<Action>> deferredActions = new IdentityHashMap<>();

    boolean add(T item, KeepReasonWitness witness) {
      assert witness != null;
      if (items.add(item)) {
        deferredActions.getOrDefault(item, Collections.emptyList()).forEach(Action::execute);
        return true;
      }
      return false;
    }

    boolean contains(T item) {
      return items.contains(item);
    }

    boolean registerDeferredAction(T item, Action action) {
      if (!items.contains(item)) {
        deferredActions.computeIfAbsent(item, ignore -> new ArrayList<>()).add(action);
        return true;
      }
      return false;
    }

    Set<T> getItems() {
      return Collections.unmodifiableSet(items);
    }
  }

  private class LiveFieldsSet {

    private final Set<DexEncodedField> fields = Sets.newIdentityHashSet();

    private final BiConsumer<DexEncodedField, KeepReason> register;

    LiveFieldsSet(BiConsumer<DexEncodedField, KeepReason> register) {
      this.register = register;
    }

    boolean add(ProgramField field, KeepReason reason) {
      DexEncodedField definition = field.getDefinition();
      register.accept(definition, reason);
      transitionUnusedInterfaceToLive(field.getHolder());
      return fields.add(definition);
    }

    boolean contains(DexEncodedField field) {
      return fields.contains(field);
    }

    boolean contains(ProgramField field) {
      return contains(field.getDefinition());
    }
  }

  private class LiveMethodsSet {

    private final Set<DexEncodedMethod> items = Sets.newIdentityHashSet();

    private final BiConsumer<DexEncodedMethod, KeepReason> register;

    LiveMethodsSet(BiConsumer<DexEncodedMethod, KeepReason> register) {
      this.register = register;
    }

    boolean add(ProgramMethod method, KeepReason reason) {
      DexEncodedMethod definition = method.getDefinition();
      register.accept(definition, reason);
      transitionUnusedInterfaceToLive(method.getHolder());
      return items.add(definition);
    }

    boolean contains(DexEncodedMethod method) {
      return items.contains(method);
    }

    boolean contains(ProgramMethod method) {
      return contains(method.getDefinition());
    }

    Set<DexEncodedMethod> getItems() {
      return Collections.unmodifiableSet(items);
    }
  }

  private static class SetWithReason<T> {

    private final Set<T> items = Sets.newIdentityHashSet();

    private final BiConsumer<T, KeepReason> register;

    public SetWithReason(BiConsumer<T, KeepReason> register) {
      this.register = register;
    }

    boolean add(T item, KeepReason reason) {
      register.accept(item, reason);
      return items.add(item);
    }

    boolean contains(T item) {
      return items.contains(item);
    }

    Set<T> getItems() {
      return Collections.unmodifiableSet(items);
    }
  }

  private class AnnotationReferenceMarker implements IndexedItemCollection {

    private final DexItem annotationHolder;
    private final DexItemFactory dexItemFactory;
    private final KeepReason reason;

    private AnnotationReferenceMarker(
        DexItem annotationHolder, DexItemFactory dexItemFactory, KeepReason reason) {
      this.annotationHolder = annotationHolder;
      this.dexItemFactory = dexItemFactory;
      this.reason = reason;
    }

    @Override
    public boolean addClass(DexProgramClass dexProgramClass) {
      return false;
    }

    @Override
    public boolean addField(DexField fieldReference) {
      recordFieldReference(fieldReference);
      DexProgramClass holder = getProgramClassOrNull(fieldReference.holder);
      if (holder == null) {
        return false;
      }
      ProgramField field = holder.lookupProgramField(fieldReference);
      if (field == null) {
        return false;
      }
      // There is no dispatch on annotations, so only keep what is directly referenced.
      if (field.getReference() != fieldReference) {
        return false;
      }
      if (field.getDefinition().isStatic()) {
        FieldAccessInfoImpl fieldAccessInfo =
            fieldAccessInfoCollection.contains(fieldReference)
                ? fieldAccessInfoCollection.get(fieldReference)
                : fieldAccessInfoCollection.extend(
                    fieldReference, new FieldAccessInfoImpl(fieldReference));
        fieldAccessInfo.setReadFromAnnotation();
        markStaticFieldAsLive(field, KeepReason.referencedInAnnotation(annotationHolder));
        // When an annotation has a field of an enum type with a default value then Java VM
        // will use the values() method on that enum class.
        if (options.isGeneratingClassFiles()
            && annotationHolder == dexItemFactory.annotationDefault) {
          if (field.getHolder().isEnum()) {
            markEnumValuesAsReachable(
                field.getHolder(), KeepReason.referencedInAnnotation(annotationHolder));
          }
        }
      } else {
        // There is no dispatch on annotations, so only keep what is directly referenced.
        markInstanceFieldAsReachable(field, KeepReason.referencedInAnnotation(annotationHolder));
      }
      return false;
    }

    @Override
    public boolean addMethod(DexMethod method) {
      // Record the references in case they are not program types.
      recordMethodReference(method);
      DexProgramClass holder = getProgramClassOrNull(method.holder);
      if (holder == null) {
        return false;
      }
      DexEncodedMethod target = holder.lookupDirectMethod(method);
      if (target != null) {
        // There is no dispatch on annotations, so only keep what is directly referenced.
        if (target.method == method) {
          markDirectStaticOrConstructorMethodAsLive(
              new ProgramMethod(holder, target),
              KeepReason.referencedInAnnotation(annotationHolder));
        }
      } else {
        target = holder.lookupVirtualMethod(method);
        // There is no dispatch on annotations, so only keep what is directly referenced.
        if (target != null && target.method == method) {
          markMethodAsTargeted(
              new ProgramMethod(holder, target),
              KeepReason.referencedInAnnotation(annotationHolder));
        }
      }
      return false;
    }

    @Override
    public boolean addString(DexString string) {
      return false;
    }

    @Override
    public boolean addProto(DexProto proto) {
      return false;
    }

    @Override
    public boolean addCallSite(DexCallSite callSite) {
      return false;
    }

    @Override
    public boolean addMethodHandle(DexMethodHandle methodHandle) {
      return false;
    }

    @Override
    public boolean addType(DexType type) {
      // Annotations can also contain the void type, which is not a class type, so filter it out
      // here.
      if (type != dexItemFactory.voidType) {
        markTypeAsLive(type, reason);
      }
      return false;
    }
  }

  public static class EnqueuerDefinitionSupplier implements DexDefinitionSupplier {

    private final Enqueuer enqueuer;
    private final AppView<?> appView;

    private EnqueuerDefinitionSupplier(AppView<?> appView, Enqueuer enqueuer) {
      this.appView = appView;
      this.enqueuer = enqueuer;
    }

    @Override
    public DexClass definitionFor(DexType type) {
      return enqueuer.definitionFor(type);
    }

    @Override
    public DexItemFactory dexItemFactory() {
      return appView.dexItemFactory();
    }
  }
}
