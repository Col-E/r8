// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult.isOverriding;
import static com.android.tools.r8.utils.collections.ThrowingSet.isThrowingSet;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Definition;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.DispatchTargetLookupResult;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.FieldAccessInfoCollectionImpl;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.InstantiatedSubTypeInfo;
import com.android.tools.r8.graph.LookupMethodTarget;
import com.android.tools.r8.graph.LookupResult.LookupResultSuccess;
import com.android.tools.r8.graph.LookupTarget;
import com.android.tools.r8.graph.MethodAccessInfoCollection;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ObjectAllocationInfoCollection;
import com.android.tools.r8.graph.ObjectAllocationInfoCollectionImpl;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.SingleDispatchTargetLookupResult;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.graph.UnknownDispatchTargetLookupResult;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryAPIConverter;
import com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper;
import com.android.tools.r8.naming.SeedMapper;
import com.android.tools.r8.repackaging.RepackagingUtils;
import com.android.tools.r8.shaking.KeepInfo.Joiner;
import com.android.tools.r8.synthesis.CommittedItems;
import com.android.tools.r8.threading.TaskCollection;
import com.android.tools.r8.utils.CollectionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.PredicateSet;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.Visibility;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.collections.ThrowingSet;
import com.android.tools.r8.utils.structural.Ordered;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Encapsulates liveness and reachability information for an application. */
public class AppInfoWithLiveness extends AppInfoWithClassHierarchy
    implements InstantiatedSubTypeInfo {
  /** Set of reachable proto types that will be dead code eliminated. */
  private final Set<DexType> deadProtoTypes;
  /**
   * Set of types that are mentioned in the program. We at least need an empty abstract classitem
   * for these.
   */
  private final Set<DexType> liveTypes;
  /**
   * Set of methods that are the immediate target of an invoke. They might not actually be live but
   * are required so that invokes can find the method. If such a method is not live (i.e. not
   * contained in {@link #liveMethods}, it may be marked as abstract and its implementation may be
   * removed.
   */
  private Set<DexMethod> targetedMethods;

  /** Classes that lead to resolution errors such as non-existing or invalid targets. */
  private final Set<DexType> failedClassResolutionTargets;

  /** Method targets that lead to resolution errors such as non-existing or invalid targets. */
  private final Set<DexMethod> failedMethodResolutionTargets;

  /** Field targets that lead to resolution errors, such as non-existing or invalid targets. */
  private final Set<DexField> failedFieldResolutionTargets;

  /**
   * Set of program methods that are used as the bootstrap method for an invoke-dynamic instruction.
   */
  private final Set<DexMethod> bootstrapMethods;

  /** Set of virtual methods that are the immediate target of an invoke-direct. */
  private final Set<DexMethod> virtualMethodsTargetedByInvokeDirect;
  /**
   * Set of methods that belong to live classes and can be reached by invokes. These need to be
   * kept.
   */
  private Set<DexMethod> liveMethods;

  /**
   * Information about all fields that are accessed by the program. The information includes whether
   * a given field is read/written by the program, and it also includes all indirect accesses to
   * each field. The latter is used, for example, during member rebinding.
   */
  private final FieldAccessInfoCollectionImpl fieldAccessInfoCollection;

  /** Set of all methods referenced in invokes along with their calling contexts. */
  private final MethodAccessInfoCollection methodAccessInfoCollection;
  /** Information about instantiated classes and their allocation sites. */
  private final ObjectAllocationInfoCollectionImpl objectAllocationInfoCollection;
  /**
   * Set of live call sites in the code. Note that if desugaring has taken place call site objects
   * will have been removed from the code.
   */
  public final Map<DexCallSite, ProgramMethodSet> callSites;
  /** Collection of keep requirements for the program. */
  private final KeepInfoCollection keepInfo;
  /** All items with assumemayhavesideeffects rule. */
  public final Map<DexReference, ProguardMemberRule> mayHaveSideEffects;
  /** All methods that should be inlined if possible due to a configuration directive. */
  private final Set<DexMethod> alwaysInline;
  /**
   * All methods that *must* never be inlined as a result of having a single caller due to a
   * configuration directive (testing only).
   */
  private final Set<DexMethod> neverInlineDueToSingleCaller;
  /** Items for which to print inlining decisions for (testing only). */
  private final Set<DexMethod> whyAreYouNotInlining;
  /** All methods that must be reprocessed (testing only). */
  private final Set<DexMethod> reprocess;
  /** All methods that must not be reprocessed (testing only). */
  private final Set<DexMethod> neverReprocess;
  /** All types that should be inlined if possible due to a configuration directive. */
  public final PredicateSet<DexType> alwaysClassInline;
  /** All types that *must* never be inlined due to a configuration directive (testing only). */
  private final Set<DexType> neverClassInline;

  private final Set<DexType> noClassMerging;
  private final Set<DexType> noHorizontalClassMerging;
  private final Set<DexType> noVerticalClassMerging;

  /**
   * Set of lock candidates (i.e., types whose class reference may flow to a monitor instruction).
   */
  private final Set<DexType> lockCandidates;
  /**
   * A map from seen init-class references to the minimum required visibility of the corresponding
   * static field.
   */
  public final Map<DexType, Visibility> initClassReferences;
  /**
   * Set of all methods including a RecordFieldValues instruction. Set only in final tree shaking.
   */
  public final Set<DexMethod> recordFieldValuesReferences;
  /**
   * All methods and fields whose value *must* never be propagated due to a configuration directive.
   * (testing only).
   */
  private final Set<DexMember<?, ?>> neverPropagateValue;
  /**
   * All items with -identifiernamestring rule. Bound boolean value indicates the rule is explicitly
   * specified by users (<code>true</code>) or not, i.e., implicitly added by R8 (<code>false</code>
   * ).
   */
  public final Object2BooleanMap<DexMember<?, ?>> identifierNameStrings;
  /** A set of types that have been removed by the {@link TreePruner}. */
  final Set<DexType> prunedTypes;
  /** A map from switchmap class types to their corresponding switchmaps. */
  final Map<DexField, Int2ReferenceMap<DexField>> switchMaps;

  /* A cache to improve the lookup performance of lookupSingleVirtualTarget */
  private final SingleTargetLookupCache singleTargetLookupCache = new SingleTargetLookupCache();

  // TODO(zerny): Clean up the constructors so we have just one.
  AppInfoWithLiveness(
      CommittedItems committedItems,
      ClassToFeatureSplitMap classToFeatureSplitMap,
      MainDexInfo mainDexInfo,
      MissingClasses missingClasses,
      Set<DexType> deadProtoTypes,
      Set<DexType> liveTypes,
      Set<DexMethod> targetedMethods,
      Set<DexType> failedClassResolutionTargets,
      Set<DexMethod> failedMethodResolutionTargets,
      Set<DexField> failedFieldResolutionTargets,
      Set<DexMethod> bootstrapMethods,
      Set<DexMethod> virtualMethodsTargetedByInvokeDirect,
      Set<DexMethod> liveMethods,
      FieldAccessInfoCollectionImpl fieldAccessInfoCollection,
      MethodAccessInfoCollection methodAccessInfoCollection,
      ObjectAllocationInfoCollectionImpl objectAllocationInfoCollection,
      Map<DexCallSite, ProgramMethodSet> callSites,
      KeepInfoCollection keepInfo,
      Map<DexReference, ProguardMemberRule> mayHaveSideEffects,
      Set<DexMethod> alwaysInline,
      Set<DexMethod> neverInlineDueToSingleCaller,
      Set<DexMethod> whyAreYouNotInlining,
      Set<DexMethod> reprocess,
      Set<DexMethod> neverReprocess,
      PredicateSet<DexType> alwaysClassInline,
      Set<DexType> neverClassInline,
      Set<DexType> noClassMerging,
      Set<DexType> noVerticalClassMerging,
      Set<DexType> noHorizontalClassMerging,
      Set<DexMember<?, ?>> neverPropagateValue,
      Object2BooleanMap<DexMember<?, ?>> identifierNameStrings,
      Set<DexType> prunedTypes,
      Map<DexField, Int2ReferenceMap<DexField>> switchMaps,
      Set<DexType> lockCandidates,
      Map<DexType, Visibility> initClassReferences,
      Set<DexMethod> recordFieldValuesReferences) {
    super(committedItems, classToFeatureSplitMap, mainDexInfo, missingClasses);
    this.deadProtoTypes = deadProtoTypes;
    this.liveTypes = liveTypes;
    this.targetedMethods = targetedMethods;
    this.failedClassResolutionTargets = failedClassResolutionTargets;
    this.failedMethodResolutionTargets = failedMethodResolutionTargets;
    this.failedFieldResolutionTargets = failedFieldResolutionTargets;
    this.bootstrapMethods = bootstrapMethods;
    this.virtualMethodsTargetedByInvokeDirect = virtualMethodsTargetedByInvokeDirect;
    this.liveMethods = liveMethods;
    this.fieldAccessInfoCollection = fieldAccessInfoCollection;
    this.methodAccessInfoCollection = methodAccessInfoCollection;
    this.objectAllocationInfoCollection = objectAllocationInfoCollection;
    this.keepInfo = keepInfo;
    this.mayHaveSideEffects = mayHaveSideEffects;
    this.callSites = callSites;
    this.alwaysInline = alwaysInline;
    this.neverInlineDueToSingleCaller = neverInlineDueToSingleCaller;
    this.whyAreYouNotInlining = whyAreYouNotInlining;
    this.reprocess = reprocess;
    this.neverReprocess = neverReprocess;
    this.alwaysClassInline = alwaysClassInline;
    this.neverClassInline = neverClassInline;
    this.noClassMerging = noClassMerging;
    this.noVerticalClassMerging = noVerticalClassMerging;
    this.noHorizontalClassMerging = noHorizontalClassMerging;
    this.neverPropagateValue = neverPropagateValue;
    this.identifierNameStrings = identifierNameStrings;
    this.prunedTypes = prunedTypes;
    this.switchMaps = switchMaps;
    this.lockCandidates = lockCandidates;
    this.initClassReferences = initClassReferences;
    this.recordFieldValuesReferences = recordFieldValuesReferences;
    assert verify();
  }

  private AppInfoWithLiveness(AppInfoWithLiveness previous, CommittedItems committedItems) {
    this(
        committedItems,
        previous.getClassToFeatureSplitMap(),
        previous.getMainDexInfo(),
        previous.getMissingClasses(),
        previous.deadProtoTypes,
        CollectionUtils.addAll(previous.liveTypes, committedItems.getCommittedProgramTypes()),
        previous.targetedMethods,
        previous.failedClassResolutionTargets,
        previous.failedMethodResolutionTargets,
        previous.failedFieldResolutionTargets,
        previous.bootstrapMethods,
        previous.virtualMethodsTargetedByInvokeDirect,
        previous.liveMethods,
        previous.fieldAccessInfoCollection,
        previous.methodAccessInfoCollection,
        previous.objectAllocationInfoCollection,
        previous.callSites,
        previous.keepInfo,
        previous.mayHaveSideEffects,
        previous.alwaysInline,
        previous.neverInlineDueToSingleCaller,
        previous.whyAreYouNotInlining,
        previous.reprocess,
        previous.neverReprocess,
        previous.alwaysClassInline,
        previous.neverClassInline,
        previous.noClassMerging,
        previous.noVerticalClassMerging,
        previous.noHorizontalClassMerging,
        previous.neverPropagateValue,
        previous.identifierNameStrings,
        previous.prunedTypes,
        previous.switchMaps,
        previous.lockCandidates,
        previous.initClassReferences,
        previous.recordFieldValuesReferences);
  }

  private AppInfoWithLiveness(
      AppInfoWithLiveness previous, PrunedItems prunedItems, TaskCollection<?> tasks)
      throws ExecutionException {
    this(
        previous.getSyntheticItems().commitPrunedItems(prunedItems),
        previous.getClassToFeatureSplitMap().withoutPrunedItems(prunedItems),
        previous.getMainDexInfo().withoutPrunedItems(prunedItems),
        previous.getMissingClasses(),
        previous.deadProtoTypes,
        pruneClasses(previous.liveTypes, prunedItems, tasks),
        pruneMethods(previous.targetedMethods, prunedItems, tasks),
        pruneClasses(previous.failedClassResolutionTargets, prunedItems, tasks),
        pruneMethods(previous.failedMethodResolutionTargets, prunedItems, tasks),
        pruneFields(previous.failedFieldResolutionTargets, prunedItems, tasks),
        pruneMethods(previous.bootstrapMethods, prunedItems, tasks),
        pruneMethods(previous.virtualMethodsTargetedByInvokeDirect, prunedItems, tasks),
        pruneMethods(previous.liveMethods, prunedItems, tasks),
        previous.fieldAccessInfoCollection,
        previous.methodAccessInfoCollection.withoutPrunedItems(prunedItems),
        previous.objectAllocationInfoCollection.withoutPrunedItems(prunedItems),
        pruneCallSites(previous.callSites, prunedItems),
        extendPinnedItems(previous, prunedItems.getAdditionalPinnedItems()),
        previous.mayHaveSideEffects,
        pruneMethods(previous.alwaysInline, prunedItems, tasks),
        pruneMethods(previous.neverInlineDueToSingleCaller, prunedItems, tasks),
        pruneMethods(previous.whyAreYouNotInlining, prunedItems, tasks),
        pruneMethods(previous.reprocess, prunedItems, tasks),
        pruneMethods(previous.neverReprocess, prunedItems, tasks),
        previous.alwaysClassInline,
        pruneClasses(previous.neverClassInline, prunedItems, tasks),
        pruneClasses(previous.noClassMerging, prunedItems, tasks),
        pruneClasses(previous.noVerticalClassMerging, prunedItems, tasks),
        pruneClasses(previous.noHorizontalClassMerging, prunedItems, tasks),
        pruneMembers(previous.neverPropagateValue, prunedItems, tasks),
        pruneMapFromMembers(previous.identifierNameStrings, prunedItems, tasks),
        prunedItems.hasRemovedClasses()
            ? CollectionUtils.mergeSets(previous.prunedTypes, prunedItems.getRemovedClasses())
            : previous.prunedTypes,
        previous.switchMaps,
        pruneClasses(previous.lockCandidates, prunedItems, tasks),
        pruneMapFromClasses(previous.initClassReferences, prunedItems, tasks),
        previous.recordFieldValuesReferences);
  }

  private static Map<DexCallSite, ProgramMethodSet> pruneCallSites(
      Map<DexCallSite, ProgramMethodSet> callSites, PrunedItems prunedItems) {
    callSites
        .entrySet()
        .removeIf(
            entry -> {
              ProgramMethodSet contexts = entry.getValue();
              ProgramMethodSet prunedContexts = contexts.withoutPrunedItems(prunedItems);
              if (prunedContexts.isEmpty()) {
                return true;
              }
              entry.setValue(prunedContexts);
              return false;
            });
    return callSites;
  }

  private static Set<DexType> pruneClasses(
      Set<DexType> methods, PrunedItems prunedItems, TaskCollection<?> tasks)
      throws ExecutionException {
    return pruneItems(methods, prunedItems.getRemovedClasses(), tasks);
  }

  private static Set<DexField> pruneFields(
      Set<DexField> fields, PrunedItems prunedItems, TaskCollection<?> tasks)
      throws ExecutionException {
    return pruneItems(fields, prunedItems.getRemovedFields(), tasks);
  }

  private static Set<DexMember<?, ?>> pruneMembers(
      Set<DexMember<?, ?>> members, PrunedItems prunedItems, TaskCollection<?> tasks)
      throws ExecutionException {
    if (prunedItems.hasRemovedMembers()) {
      tasks.submit(
          () -> {
            Set<DexField> removedFields = prunedItems.getRemovedFields();
            Set<DexMethod> removedMethods = prunedItems.getRemovedMethods();
            if (members.size() <= removedFields.size() + removedMethods.size()) {
              members.removeIf(
                  member ->
                      member.isDexField()
                          ? removedFields.contains(member.asDexField())
                          : removedMethods.contains(member.asDexMethod()));
            } else {
              removedFields.forEach(members::remove);
              removedMethods.forEach(members::remove);
            }
          });
    }
    return members;
  }

  private static Set<DexMethod> pruneMethods(
      Set<DexMethod> methods, PrunedItems prunedItems, TaskCollection<?> tasks)
      throws ExecutionException {
    return pruneItems(methods, prunedItems.getRemovedMethods(), tasks);
  }

  private static <T> Set<T> pruneItems(Set<T> items, Set<T> removedItems, TaskCollection<?> tasks)
      throws ExecutionException {
    if (!isThrowingSet(items) && !removedItems.isEmpty()) {
      tasks.submit(
          () -> {
            if (items.size() <= removedItems.size()) {
              items.removeAll(removedItems);
            } else {
              removedItems.forEach(items::remove);
            }
          });
    }
    return items;
  }

  private static <V> Map<DexType, V> pruneMapFromClasses(
      Map<DexType, V> map, PrunedItems prunedItems, TaskCollection<?> tasks)
      throws ExecutionException {
    return pruneMap(map, prunedItems.getRemovedClasses(), tasks);
  }

  private static Object2BooleanMap<DexMember<?, ?>> pruneMapFromMembers(
      Object2BooleanMap<DexMember<?, ?>> map, PrunedItems prunedItems, TaskCollection<?> tasks)
      throws ExecutionException {
    if (prunedItems.hasRemovedMembers()) {
      tasks.submit(
          () -> {
            prunedItems.getRemovedFields().forEach(map::removeBoolean);
            prunedItems.getRemovedMethods().forEach(map::removeBoolean);
          });
    }
    return map;
  }

  private static <K, V> Map<K, V> pruneMap(
      Map<K, V> map, Set<K> removedItems, TaskCollection<?> tasks) throws ExecutionException {
    if (!removedItems.isEmpty()) {
      tasks.submit(
          () -> {
            if (map.size() <= removedItems.size()) {
              map.keySet().removeAll(removedItems);
            } else {
              removedItems.forEach(map::remove);
            }
          });
    }
    return map;
  }

  @Override
  public void notifyHorizontalClassMergerFinished(
      HorizontalClassMerger.Mode horizontalClassMergerMode) {
    if (horizontalClassMergerMode.isInitial()
        && !options().getAccessModifierOptions().isLegacyAccessModifierEnabled()) {
      getMethodAccessInfoCollection().destroy();
    }
  }

  public void notifyMemberRebindingFinished(AppView<AppInfoWithLiveness> appView) {
    getFieldAccessInfoCollection().restrictToProgram(appView);
    if (!options().getAccessModifierOptions().isLegacyAccessModifierEnabled()) {
      getMethodAccessInfoCollection().destroyNonDirectNonSuperInvokes();
    }
  }

  public void notifyRedundantBridgeRemoverFinished(boolean initial) {
    if (initial && !options().getAccessModifierOptions().isLegacyAccessModifierEnabled()) {
      getMethodAccessInfoCollection().destroySuperInvokes();
    }
  }

  @Override
  public void notifyMinifierFinished() {
    liveMethods = ThrowingSet.get();
    if (!options().getAccessModifierOptions().isLegacyAccessModifierEnabled()) {
      getMethodAccessInfoCollection().destroy();
    }
  }

  public void notifyTreePrunerFinished(Enqueuer.Mode mode) {
    if (mode.isInitialTreeShaking()) {
      liveMethods = ThrowingSet.get();
    }
    targetedMethods = ThrowingSet.get();
  }

  private boolean verify() {
    assert keepInfo.verifyPinnedTypesAreLive(liveTypes, options());
    assert objectAllocationInfoCollection.verifyAllocatedTypesAreLive(
        liveTypes, getMissingClasses(), this);
    return true;
  }

  @Override
  public AppInfoWithLiveness rebuildWithMainDexInfo(MainDexInfo mainDexInfo) {
    return new AppInfoWithLiveness(
        getSyntheticItems().commit(app()),
        getClassToFeatureSplitMap(),
        mainDexInfo,
        getMissingClasses(),
        deadProtoTypes,
        liveTypes,
        targetedMethods,
        failedClassResolutionTargets,
        failedMethodResolutionTargets,
        failedFieldResolutionTargets,
        bootstrapMethods,
        virtualMethodsTargetedByInvokeDirect,
        liveMethods,
        fieldAccessInfoCollection,
        methodAccessInfoCollection,
        objectAllocationInfoCollection,
        callSites,
        keepInfo,
        mayHaveSideEffects,
        alwaysInline,
        neverInlineDueToSingleCaller,
        whyAreYouNotInlining,
        reprocess,
        neverReprocess,
        alwaysClassInline,
        neverClassInline,
        noClassMerging,
        noVerticalClassMerging,
        noHorizontalClassMerging,
        neverPropagateValue,
        identifierNameStrings,
        prunedTypes,
        switchMaps,
        lockCandidates,
        initClassReferences,
        recordFieldValuesReferences);
  }

  private static KeepInfoCollection extendPinnedItems(
      AppInfoWithLiveness previous, Collection<? extends DexReference> additionalPinnedItems) {
    if (additionalPinnedItems == null || additionalPinnedItems.isEmpty()) {
      return previous.keepInfo;
    }
    return previous.keepInfo.mutate(
        collection -> {
          for (DexReference reference : additionalPinnedItems) {
            if (reference.isDexType()) {
              DexProgramClass clazz =
                  asProgramClassOrNull(previous.definitionFor(reference.asDexType()));
              if (clazz != null) {
                collection.joinClass(clazz, Joiner::disallowShrinking);
              }
            } else if (reference.isDexMethod()) {
              DexMethod method = reference.asDexMethod();
              DexProgramClass clazz = asProgramClassOrNull(previous.definitionFor(method.holder));
              if (clazz != null) {
                ProgramMethod definition = clazz.lookupProgramMethod(method);
                if (definition != null) {
                  collection.joinMethod(definition, Joiner::disallowShrinking);
                }
              }
            } else {
              DexField field = reference.asDexField();
              DexProgramClass clazz = asProgramClassOrNull(previous.definitionFor(field.holder));
              if (clazz != null) {
                ProgramField definition = clazz.lookupProgramField(field);
                if (definition != null) {
                  collection.joinField(definition, Joiner::disallowShrinking);
                }
              }
            }
          }
        });
  }

  public AppInfoWithLiveness(
      AppInfoWithLiveness previous, Map<DexField, Int2ReferenceMap<DexField>> switchMaps) {
    super(
        previous.getSyntheticItems().commit(previous.app()),
        previous.getClassToFeatureSplitMap(),
        previous.getMainDexInfo(),
        previous.getMissingClasses());
    this.deadProtoTypes = previous.deadProtoTypes;
    this.liveTypes = previous.liveTypes;
    this.targetedMethods = previous.targetedMethods;
    this.failedClassResolutionTargets = previous.failedClassResolutionTargets;
    this.failedMethodResolutionTargets = previous.failedMethodResolutionTargets;
    this.failedFieldResolutionTargets = previous.failedFieldResolutionTargets;
    this.bootstrapMethods = previous.bootstrapMethods;
    this.virtualMethodsTargetedByInvokeDirect = previous.virtualMethodsTargetedByInvokeDirect;
    this.liveMethods = previous.liveMethods;
    this.fieldAccessInfoCollection = previous.fieldAccessInfoCollection;
    this.methodAccessInfoCollection = previous.methodAccessInfoCollection;
    this.objectAllocationInfoCollection = previous.objectAllocationInfoCollection;
    this.keepInfo = previous.keepInfo;
    this.mayHaveSideEffects = previous.mayHaveSideEffects;
    this.callSites = previous.callSites;
    this.alwaysInline = previous.alwaysInline;
    this.neverInlineDueToSingleCaller = previous.neverInlineDueToSingleCaller;
    this.whyAreYouNotInlining = previous.whyAreYouNotInlining;
    this.reprocess = previous.reprocess;
    this.neverReprocess = previous.neverReprocess;
    this.alwaysClassInline = previous.alwaysClassInline;
    this.neverClassInline = previous.neverClassInline;
    this.noClassMerging = previous.noClassMerging;
    this.noVerticalClassMerging = previous.noVerticalClassMerging;
    this.noHorizontalClassMerging = previous.noHorizontalClassMerging;
    this.neverPropagateValue = previous.neverPropagateValue;
    this.identifierNameStrings = previous.identifierNameStrings;
    this.prunedTypes = previous.prunedTypes;
    this.switchMaps = switchMaps;
    this.lockCandidates = previous.lockCandidates;
    this.initClassReferences = previous.initClassReferences;
    this.recordFieldValuesReferences = previous.recordFieldValuesReferences;
    previous.markObsolete();
    assert verify();
  }

  public static AppInfoWithLivenessModifier modifier() {
    return new AppInfoWithLivenessModifier();
  }

  @Override
  public DexClass definitionFor(DexType type) {
    DexClass definition = super.definitionFor(type);
    assert definition != null
            || deadProtoTypes.contains(type)
            || getMissingClasses().contains(type)
            // TODO(b/150693139): Remove these exceptions once fixed.
            || InterfaceDesugaringSyntheticHelper.isCompanionClassType(type)
            // TODO(b/150736225): Not sure how to remove these.
            || DesugaredLibraryAPIConverter.isVivifiedType(type)
        : "Failed lookup of non-missing type: " + type;
    return definition;
  }

  private CfVersion largestInputCfVersion = null;

  public boolean canUseConstClassInstructions(InternalOptions options) {
    if (!options.isGeneratingClassFiles()) {
      return true;
    }
    if (largestInputCfVersion == null) {
      computeLargestCfVersion();
    }
    return options.canUseConstClassInstructions(largestInputCfVersion);
  }

  private synchronized void computeLargestCfVersion() {
    if (largestInputCfVersion != null) {
      return;
    }
    for (DexProgramClass clazz : classes()) {
      // Skip synthetic classes which may not have a specified version.
      if (clazz.hasClassFileVersion()) {
        largestInputCfVersion =
            Ordered.maxIgnoreNull(largestInputCfVersion, clazz.getInitialClassFileVersion());
      }
    }
    assert largestInputCfVersion != null;
  }

  public boolean isLiveProgramClass(DexProgramClass clazz) {
    return liveTypes.contains(clazz.type);
  }

  public boolean isLiveProgramType(DexType type) {
    DexClass clazz = definitionFor(type);
    return clazz != null && clazz.isProgramClass() && isLiveProgramClass(clazz.asProgramClass());
  }

  public boolean isNonProgramTypeOrLiveProgramType(DexType type) {
    if (liveTypes.contains(type)) {
      return true;
    }
    if (prunedTypes.contains(type)) {
      return false;
    }
    DexClass clazz = definitionFor(type);
    return clazz == null || !clazz.isProgramClass();
  }

  public boolean isLiveMethod(DexMethod method) {
    return liveMethods.contains(method);
  }

  public boolean isTargetedMethod(DexMethod method) {
    return targetedMethods.contains(method);
  }

  public boolean isFailedClassResolutionTarget(DexType type) {
    return failedClassResolutionTargets.contains(type);
  }

  public boolean isFailedMethodResolutionTarget(DexMethod method) {
    return failedMethodResolutionTargets.contains(method);
  }

  public Set<DexMethod> getFailedMethodResolutionTargets() {
    return failedMethodResolutionTargets;
  }

  public boolean isFailedFieldResolutionTarget(DexField field) {
    return failedFieldResolutionTargets.contains(field);
  }

  public Set<DexField> getFailedFieldResolutionTargets() {
    return failedFieldResolutionTargets;
  }

  public boolean isBootstrapMethod(DexMethod method) {
    return bootstrapMethods.contains(method);
  }

  public boolean isBootstrapMethod(ProgramMethod method) {
    return isBootstrapMethod(method.getReference());
  }

  public Set<DexMethod> getVirtualMethodsTargetedByInvokeDirect() {
    return virtualMethodsTargetedByInvokeDirect;
  }

  public boolean isAlwaysInlineMethod(DexMethod method) {
    return alwaysInline.contains(method);
  }

  public boolean hasNoAlwaysInlineMethods() {
    return alwaysInline.isEmpty();
  }

  public boolean isNeverInlineDueToSingleCallerMethod(ProgramMethod method) {
    return neverInlineDueToSingleCaller.contains(method.getReference());
  }

  public boolean isWhyAreYouNotInliningMethod(DexMethod method) {
    return whyAreYouNotInlining.contains(method);
  }

  public boolean hasNoWhyAreYouNotInliningMethods() {
    return whyAreYouNotInlining.isEmpty();
  }

  public boolean isNeverReprocessMethod(ProgramMethod method) {
    return neverReprocess.contains(method.getReference())
        || method.getOptimizationInfo().hasBeenInlinedIntoSingleCallSite();
  }

  public Set<DexMethod> getReprocessMethods() {
    return reprocess;
  }

  public void forEachReachableInterface(Consumer<DexClass> consumer) {
    forEachReachableInterface(consumer, ImmutableList.of());
  }

  public void forEachReachableInterface(
      Consumer<DexClass> consumer, Iterable<DexType> additionalPaths) {
    WorkList<DexType> worklist = WorkList.newIdentityWorkList();
    worklist.addIfNotSeen(additionalPaths);
    worklist.addIfNotSeen(objectAllocationInfoCollection.getInstantiatedLambdaInterfaces());
    for (DexProgramClass clazz : classes()) {
      worklist.addIfNotSeen(clazz.type);
    }
    while (worklist.hasNext()) {
      DexType type = worklist.next();
      DexClass definition = definitionFor(type);
      if (definition == null) {
        continue;
      }
      if (definition.isInterface()) {
        consumer.accept(definition);
      }
      definition.forEachImmediateSupertype(worklist::addIfNotSeen);
    }
  }

  /**
   * Resolve the methods implemented by the lambda expression that created the {@code callSite}.
   *
   * <p>If {@code callSite} was not created as a result of a lambda expression (i.e. the metafactory
   * is not {@code LambdaMetafactory}), the empty set is returned.
   *
   * <p>If the metafactory is neither {@code LambdaMetafactory} nor {@code StringConcatFactory}, a
   * warning is issued.
   *
   * <p>The returned set of methods all have {@code callSite.methodName} as the method name.
   *
   * @param callSite Call site to resolve.
   * @return Methods implemented by the lambda expression that created the {@code callSite}.
   */
  @SuppressWarnings("ReferenceEquality")
  public Set<DexEncodedMethod> lookupLambdaImplementedMethods(
      DexCallSite callSite, AppView<AppInfoWithLiveness> appView) {
    assert checkIfObsolete();
    List<DexType> callSiteInterfaces = LambdaDescriptor.getInterfaces(callSite, appView);
    if (callSiteInterfaces == null || callSiteInterfaces.isEmpty()) {
      return Collections.emptySet();
    }
    Set<DexEncodedMethod> result = Sets.newIdentityHashSet();
    Deque<DexType> worklist = new ArrayDeque<>(callSiteInterfaces);
    Set<DexType> visited = Sets.newIdentityHashSet();
    while (!worklist.isEmpty()) {
      DexType iface = worklist.removeFirst();
      if (!visited.add(iface)) {
        // Already visited previously. May happen due to "diamond shapes" in the interface
        // hierarchy.
        continue;
      }
      DexClass clazz = definitionFor(iface);
      if (clazz == null) {
        // Skip this interface. If the lambda only implements missing library interfaces and not any
        // program interfaces, then minification and tree shaking are not interested in this
        // DexCallSite anyway, so skipping this interface is harmless. On the other hand, if
        // minification is run on a program with a lambda interface that implements both a missing
        // library interface and a present program interface, then we might minify the method name
        // on the program interface even though it should be kept the same as the (missing) library
        // interface method. That is a shame, but minification is not suited for incomplete programs
        // anyway.
        continue;
      }
      assert clazz.isInterface();
      for (DexEncodedMethod method : clazz.virtualMethods()) {
        if (method.getReference().name == callSite.methodName && method.accessFlags.isAbstract()) {
          result.add(method);
        }
      }
      Collections.addAll(worklist, clazz.interfaces.values);
    }
    return result;
  }

  /**
   * Const-classes is a conservative set of types that may be lock-candidates and cannot be merged.
   * When using synchronized blocks, we cannot ensure that const-class locks will not flow in. This
   * can potentially cause incorrect behavior when merging classes. A conservative choice is to not
   * merge any const-class classes. More info at b/142438687.
   */
  public boolean isLockCandidate(DexType type) {
    return lockCandidates.contains(type);
  }

  public Set<DexType> getDeadProtoTypes() {
    return deadProtoTypes;
  }

  public Int2ReferenceMap<DexField> getSwitchMap(DexField field) {
    assert checkIfObsolete();
    return switchMaps.get(field);
  }

  /** This method provides immutable access to `fieldAccessInfoCollection`. */
  public FieldAccessInfoCollection<? extends FieldAccessInfo> getFieldAccessInfoCollection() {
    return fieldAccessInfoCollection;
  }

  FieldAccessInfoCollectionImpl getMutableFieldAccessInfoCollection() {
    return fieldAccessInfoCollection;
  }

  /** This method provides immutable access to `methodAccessInfoCollection`. */
  public MethodAccessInfoCollection getMethodAccessInfoCollection() {
    return methodAccessInfoCollection;
  }

  /** This method provides immutable access to `objectAllocationInfoCollection`. */
  public ObjectAllocationInfoCollection getObjectAllocationInfoCollection() {
    return objectAllocationInfoCollection;
  }

  void mutateObjectAllocationInfoCollection(
      Consumer<ObjectAllocationInfoCollectionImpl.Builder> mutator) {
    objectAllocationInfoCollection.mutate(mutator, this);
  }

  void removeFromSingleTargetLookupCache(DexClass clazz) {
    singleTargetLookupCache.removeInstantiatedType(clazz.type, this);
  }

  private boolean isInstantiatedDirectly(DexProgramClass clazz) {
    assert checkIfObsolete();
    DexType type = clazz.type;
    return (!clazz.isInterface() && objectAllocationInfoCollection.isInstantiatedDirectly(clazz))
        // TODO(b/145344105): Model annotations in the object allocation info.
        || (clazz.isAnnotation() && liveTypes.contains(type));
  }

  public boolean isInstantiatedIndirectly(DexProgramClass clazz) {
    assert checkIfObsolete();
    return objectAllocationInfoCollection.hasInstantiatedStrictSubtype(clazz);
  }

  public boolean isInstantiatedDirectlyOrIndirectly(DexProgramClass clazz) {
    assert checkIfObsolete();
    return isInstantiatedDirectly(clazz) || isInstantiatedIndirectly(clazz);
  }

  public boolean isReachableOrReferencedField(DexEncodedField field) {
    assert checkIfObsolete();
    DexField reference = field.getReference();
    FieldAccessInfo info = getFieldAccessInfoCollection().get(reference);
    if (info != null) {
      assert info.isRead() || info.isWritten();
      return true;
    }
    assert getKeepInfo().getFieldInfo(field, this).isShrinkingAllowed(options());
    return false;
  }

  public boolean isFieldRead(DexClassAndField field) {
    assert checkIfObsolete();
    FieldAccessInfo info = getFieldAccessInfoCollection().get(field.getReference());
    if (info != null && info.isRead()) {
      return true;
    }
    if (isPinned(field)) {
      return true;
    }
    // For non-program classes we don't know whether a field is read.
    return !field.isProgramField();
  }

  public boolean isFieldWritten(DexClassAndField field) {
    assert checkIfObsolete();
    return isFieldWrittenByFieldPutInstruction(field) || isPinned(field);
  }

  public boolean isFieldWrittenByFieldPutInstruction(DexClassAndField field) {
    assert checkIfObsolete();
    FieldAccessInfo info = getFieldAccessInfoCollection().get(field.getReference());
    if (info != null && info.isWritten()) {
      // The field is written directly by the program itself.
      return true;
    }
    // For non-program classes we don't know whether a field is rewritten.
    return !field.isProgramField();
  }

  public boolean isFieldOnlyWrittenInMethod(DexClassAndField field, DexEncodedMethod method) {
    assert checkIfObsolete();
    assert isFieldWritten(field) : "Expected field `" + field.toSourceString() + "` to be written";
    if (isPinned(field)) {
      return false;
    }
    return isFieldOnlyWrittenInMethodIgnoringPinning(field, method);
  }

  public boolean isFieldOnlyWrittenInMethodIgnoringPinning(
      DexClassAndField field, DexEncodedMethod method) {
    assert checkIfObsolete();
    assert isFieldWritten(field) : "Expected field `" + field.toSourceString() + "` to be written";
    FieldAccessInfo fieldAccessInfo = getFieldAccessInfoCollection().get(field.getReference());
    return fieldAccessInfo != null
        && fieldAccessInfo.isWritten()
        && !fieldAccessInfo.isWrittenOutside(method);
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean isInstanceFieldWrittenOnlyInInstanceInitializers(DexClassAndField field) {
    assert checkIfObsolete();
    assert isFieldWritten(field) : "Expected field `" + field.toSourceString() + "` to be written";
    if (isPinned(field)) {
      return false;
    }
    FieldAccessInfo fieldAccessInfo = getFieldAccessInfoCollection().get(field.getReference());
    if (fieldAccessInfo == null || !fieldAccessInfo.isWritten()) {
      return false;
    }
    DexType holder = field.getHolderType();
    return fieldAccessInfo.isWrittenOnlyInMethodSatisfying(
        method ->
            method.getHolderType() == holder
                && method
                    .getDefinition()
                    .isOrWillBeInlinedIntoInstanceInitializer(dexItemFactory()));
  }

  public boolean isStaticFieldWrittenOnlyInEnclosingStaticInitializer(DexClassAndField field) {
    assert checkIfObsolete();
    assert isFieldWritten(field) : "Expected field `" + field.toSourceString() + "` to be written";
    DexEncodedMethod staticInitializer =
        definitionFor(field.getHolderType()).asProgramClass().getClassInitializer();
    return staticInitializer != null && isFieldOnlyWrittenInMethod(field, staticInitializer);
  }

  public boolean mayPropagateValueFor(
      AppView<AppInfoWithLiveness> appView, DexClassAndMember<?, ?> member) {
    assert checkIfObsolete();
    return member
        .getReference()
        .apply(
            field -> mayPropagateValueFor(appView, field),
            method -> mayPropagateValueFor(appView, method));
  }

  public boolean mayPropagateValueFor(AppView<AppInfoWithLiveness> appView, DexField field) {
    assert checkIfObsolete();
    if (neverPropagateValue.contains(field)) {
      return false;
    }
    if (isPinnedWithDefinitionLookup(field) && !field.getType().isAlwaysNull(appView)) {
      return false;
    }
    return true;
  }

  public boolean mayPropagateValueFor(AppView<AppInfoWithLiveness> appView, DexMethod method) {
    assert checkIfObsolete();
    if (neverPropagateValue.contains(method)) {
      return false;
    }
    if (!method.getReturnType().isAlwaysNull(appView)
        && !getKeepInfo()
            .getMethodInfoWithDefinitionLookup(method, this)
            .isOptimizationAllowed(options())) {
      return false;
    }
    return true;
  }

  public boolean isInstantiatedInterface(DexProgramClass clazz) {
    assert checkIfObsolete();
    return objectAllocationInfoCollection.isInterfaceWithUnknownSubtypeHierarchy(clazz);
  }

  @Override
  public boolean hasLiveness() {
    assert checkIfObsolete();
    return true;
  }

  @Override
  public AppInfoWithLiveness withLiveness() {
    assert checkIfObsolete();
    return this;
  }

  public boolean isClassInliningAllowed(DexProgramClass clazz) {
    return !isPinned(clazz) && !neverClassInline.contains(clazz.getType());
  }

  public boolean isMinificationAllowed(DexProgramClass clazz) {
    return options().isMinificationEnabled()
        && keepInfo.getInfo(clazz).isMinificationAllowed(options());
  }

  public boolean isMinificationAllowed(ProgramDefinition definition) {
    return options().isMinificationEnabled()
        && keepInfo.getInfo(definition).isMinificationAllowed(options());
  }

  public boolean isMinificationAllowed(DexDefinition definition) {
    return options().isMinificationEnabled()
        && keepInfo.getInfo(definition, this).isMinificationAllowed(options());
  }

  public boolean isMinificationAllowed(DexType reference) {
    return options().isMinificationEnabled()
        && keepInfo.getClassInfo(reference, this).isMinificationAllowed(options());
  }

  public boolean isAccessModificationAllowed(ProgramDefinition definition) {
    assert options().getProguardConfiguration().isAccessModificationAllowed();
    return keepInfo.getInfo(definition).isAccessModificationAllowed(options());
  }

  public boolean isRepackagingAllowed(DexProgramClass clazz, AppView<?> appView) {
    if (!options().isRepackagingEnabled()) {
      return false;
    }
    if (!keepInfo.getInfo(clazz).isRepackagingAllowed(options())) {
      return false;
    }
    if (RepackagingUtils.isPackageNameKept(clazz, appView.options())) {
      return false;
    }
    SeedMapper applyMappingSeedMapper = appView.getApplyMappingSeedMapper();
    return applyMappingSeedMapper == null || !applyMappingSeedMapper.hasMapping(clazz.type);
  }

  public boolean isPinnedWithDefinitionLookup(DexReference reference) {
    assert checkIfObsolete();
    return keepInfo.isPinnedWithDefinitionLookup(reference, options(), this);
  }

  public boolean isPinned(DexDefinition definition) {
    return keepInfo.isPinned(definition, options(), this);
  }

  public boolean isPinned(DexProgramClass clazz) {
    return keepInfo.isPinned(clazz, options());
  }

  public boolean isPinned(Definition definition) {
    assert definition != null;
    return definition.isProgramDefinition()
        && keepInfo.isPinned(definition.asProgramDefinition(), options());
  }

  public boolean hasPinnedInstanceInitializer(DexType type) {
    assert type.isClassType();
    DexProgramClass clazz = asProgramClassOrNull(definitionFor(type));
    if (clazz != null) {
      for (ProgramMethod method : clazz.directProgramMethods()) {
        if (method.getDefinition().isInstanceInitializer() && isPinned(method)) {
          return true;
        }
      }
    }
    return false;
  }

  public KeepInfoCollection getKeepInfo() {
    return keepInfo;
  }

  /**
   * Returns a copy of this AppInfoWithLiveness where the set of classes is pruned using the given
   * DexApplication object.
   */
  @Override
  public AppInfoWithLiveness prunedCopyFrom(
      PrunedItems prunedItems, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    assert getClass() == AppInfoWithLiveness.class;
    assert checkIfObsolete();
    if (prunedItems.isEmpty()) {
      assert app() == prunedItems.getPrunedApp();
      return this;
    }
    timing.begin("Pruning AppInfoWithLiveness");
    if (prunedItems.hasRemovedClasses()) {
      // Rebuild the hierarchy.
      objectAllocationInfoCollection.mutate(
          mutator -> mutator.removeAllocationsForPrunedItems(prunedItems), this);
      keepInfo.mutate(keepInfo -> keepInfo.removeKeepInfoForPrunedItems(prunedItems));
    } else if (prunedItems.hasRemovedMembers()) {
      keepInfo.mutate(keepInfo -> keepInfo.removeKeepInfoForPrunedItems(prunedItems));
    }
    TaskCollection<?> tasks = new TaskCollection<>(options(), executorService);
    AppInfoWithLiveness appInfoWithLiveness = new AppInfoWithLiveness(this, prunedItems, tasks);
    tasks.await();
    timing.end();
    return appInfoWithLiveness;
  }

  public AppInfoWithLiveness rebuildWithLiveness(CommittedItems committedItems) {
    return new AppInfoWithLiveness(this, committedItems);
  }

  public AppInfoWithLiveness rewrittenWithLens(
      DirectMappedDexApplication application, NonIdentityGraphLens lens, Timing timing) {
    assert checkIfObsolete();

    // Switchmap classes should never be affected by renaming.
    assert lens.assertFieldsNotModified(
        switchMaps.keySet().stream()
            .map(this::resolveField)
            .filter(FieldResolutionResult::isSingleFieldResolutionResult)
            .map(FieldResolutionResult::getResolvedField)
            .collect(Collectors.toList()));

    CommittedItems committedItems =
        getSyntheticItems().commitRewrittenWithLens(application, lens, timing);
    DexDefinitionSupplier definitionSupplier =
        committedItems.getApplication().getDefinitionsSupplier(committedItems);
    return new AppInfoWithLiveness(
        committedItems,
        getClassToFeatureSplitMap().rewrittenWithLens(lens, timing),
        getMainDexInfo().rewrittenWithLens(getSyntheticItems(), lens, timing),
        getMissingClasses(),
        deadProtoTypes,
        lens.rewriteReferences(liveTypes),
        lens.rewriteReferences(targetedMethods),
        lens.rewriteReferences(failedClassResolutionTargets),
        lens.rewriteReferences(failedMethodResolutionTargets),
        lens.rewriteFields(failedFieldResolutionTargets, timing),
        lens.rewriteReferences(bootstrapMethods),
        lens.rewriteReferences(virtualMethodsTargetedByInvokeDirect),
        lens.rewriteReferences(liveMethods),
        fieldAccessInfoCollection.rewrittenWithLens(definitionSupplier, lens, timing),
        methodAccessInfoCollection.rewrittenWithLens(definitionSupplier, lens, timing),
        objectAllocationInfoCollection.rewrittenWithLens(definitionSupplier, lens, timing),
        lens.rewriteCallSites(callSites, definitionSupplier, timing),
        keepInfo.rewrite(definitionSupplier, lens, application.options, timing),
        // Take any rule in case of collisions.
        lens.rewriteReferenceKeys(mayHaveSideEffects, (reference, rules) -> ListUtils.first(rules)),
        lens.rewriteReferences(alwaysInline),
        lens.rewriteReferences(neverInlineDueToSingleCaller),
        lens.rewriteReferences(whyAreYouNotInlining),
        lens.rewriteReferences(reprocess),
        lens.rewriteReferences(neverReprocess),
        alwaysClassInline.rewriteItems(lens::lookupType),
        lens.rewriteReferences(neverClassInline),
        lens.rewriteReferences(noClassMerging),
        lens.rewriteReferences(noVerticalClassMerging),
        lens.rewriteReferences(noHorizontalClassMerging),
        lens.rewriteReferences(neverPropagateValue),
        lens.rewriteReferenceKeys(identifierNameStrings),
        // Don't rewrite pruned types - the removed types are identified by their original name.
        prunedTypes,
        lens.rewriteFieldKeys(switchMaps),
        lens.rewriteReferences(lockCandidates),
        rewriteInitClassReferences(lens),
        lens.rewriteReferences(recordFieldValuesReferences));
  }

  public Map<DexType, Visibility> rewriteInitClassReferences(GraphLens lens) {
    return lens.rewriteTypeKeys(
        initClassReferences,
        (minimumRequiredVisibilityForCurrentMethod,
            otherMinimumRequiredVisibilityForCurrentMethod) -> {
          assert !minimumRequiredVisibilityForCurrentMethod.isPrivate();
          assert !otherMinimumRequiredVisibilityForCurrentMethod.isPrivate();
          if (minimumRequiredVisibilityForCurrentMethod.isPublic()
              || otherMinimumRequiredVisibilityForCurrentMethod.isPublic()) {
            return Visibility.PUBLIC;
          }
          if (minimumRequiredVisibilityForCurrentMethod.isProtected()
              || otherMinimumRequiredVisibilityForCurrentMethod.isProtected()) {
            return Visibility.PROTECTED;
          }
          return Visibility.PACKAGE_PRIVATE;
        });
  }

  /**
   * Returns true if the given type was part of the original program but has been removed during
   * tree shaking.
   */
  public boolean wasPruned(DexType type) {
    assert checkIfObsolete();
    return prunedTypes.contains(type);
  }

  public Set<DexType> getPrunedTypes() {
    assert checkIfObsolete();
    return prunedTypes;
  }

  public DexClassAndMethod lookupSingleTarget(
      AppView<AppInfoWithLiveness> appView,
      InvokeType type,
      DexMethod target,
      SingleResolutionResult<?> resolutionResult,
      ProgramMethod context,
      LibraryModeledPredicate modeledPredicate) {
    assert checkIfObsolete();
    if (!target.getHolderType().isClassType()) {
      return null;
    }
    switch (type) {
      case INTERFACE:
      case VIRTUAL:
        return lookupSingleVirtualTarget(
            appView,
            target,
            resolutionResult,
            context,
            type.isInterface(),
            modeledPredicate,
            DynamicType.unknown());
      case DIRECT:
        return lookupDirectTarget(target, context, appView);
      case STATIC:
        return lookupStaticTarget(target, context, appView);
      case SUPER:
        return lookupSuperTarget(target, context, appView);
      default:
        return null;
    }
  }

  /** For mapping invoke virtual instruction to single target method. */
  public DexClassAndMethod lookupSingleVirtualTargetForTesting(
      AppView<AppInfoWithLiveness> appView,
      DexMethod method,
      ProgramMethod context,
      boolean isInterface,
      LibraryModeledPredicate modeledPredicate,
      DynamicType dynamicReceiverType) {
    assert checkIfObsolete();
    SingleResolutionResult<?> resolutionResult =
        appView.appInfo().resolveMethodLegacy(method, isInterface).asSingleResolution();
    if (resolutionResult != null) {
      return lookupSingleVirtualTarget(
          appView,
          method,
          resolutionResult,
          context,
          isInterface,
          modeledPredicate,
          dynamicReceiverType);
    }
    return null;
  }

  public DexClassAndMethod lookupSingleVirtualTarget(
      AppView<AppInfoWithLiveness> appView,
      DexMethod method,
      SingleResolutionResult<?> resolutionResult,
      ProgramMethod context,
      boolean isInterface,
      LibraryModeledPredicate modeledPredicate,
      DynamicType dynamicReceiverType) {
    assert checkIfObsolete();
    assert dynamicReceiverType != null;
    if (method.getHolderType().isArrayType()) {
      return null;
    }
    TypeElement staticReceiverType = method.getHolderType().toTypeElement(appView);
    if (!appView
        .getOpenClosedInterfacesCollection()
        .isDefinitelyInstanceOfStaticType(appView, () -> dynamicReceiverType, staticReceiverType)) {
      return null;
    }
    DexClass initialResolutionHolder = definitionFor(method.holder);
    if (initialResolutionHolder == null || initialResolutionHolder.isInterface() != isInterface) {
      return null;
    }
    DexType refinedReceiverType =
        TypeAnalysis.toRefinedReceiverType(dynamicReceiverType, method, appView);
    DexClass refinedReceiverClass = definitionFor(refinedReceiverType);
    if (refinedReceiverClass == null) {
      // The refined receiver is not defined in the program and we cannot determine the target.
      return null;
    }
    if (!dynamicReceiverType.hasDynamicLowerBoundType()) {
      if (singleTargetLookupCache.hasPositiveCacheHit(refinedReceiverType, method)) {
        return singleTargetLookupCache.getPositiveCacheHit(refinedReceiverType, method);
      }
      if (singleTargetLookupCache.hasNegativeCacheHit(refinedReceiverType, method)) {
        return null;
      }
    }
    if (resolutionResult
        .isAccessibleForVirtualDispatchFrom(context.getHolder(), appView)
        .isFalse()) {
      return null;
    }
    // If the method is modeled, return the resolution.
    DexClassAndMethod resolvedMethod = resolutionResult.getResolutionPair();
    if (modeledPredicate.isModeled(resolutionResult.getResolvedHolder().getType())) {
      if (resolutionResult.getResolvedHolder().isFinal()
          || (resolvedMethod.getAccessFlags().isFinal()
              && resolvedMethod.getAccessFlags().isPublic())) {
        singleTargetLookupCache.addToCache(refinedReceiverType, method, resolvedMethod);
        return resolvedMethod;
      }
    }
    DispatchTargetLookupResult exactTarget =
        getMethodTargetFromExactRuntimeInformation(
            refinedReceiverType,
            dynamicReceiverType.getDynamicLowerBoundType(),
            resolutionResult,
            refinedReceiverClass);
    if (exactTarget != null) {
      // We are not caching single targets here because the cache does not include the
      // lower bound dimension.
      return exactTarget.isSingleResult()
          ? exactTarget.asSingleResult().getSingleDispatchTarget()
          : null;
    }
    if (refinedReceiverClass.isNotProgramClass()) {
      // The refined receiver is not defined in the program and we cannot determine the target.
      singleTargetLookupCache.addNoSingleTargetToCache(refinedReceiverType, method);
      return null;
    }
    DexClass resolvedHolder = resolutionResult.getResolvedHolder();
    // TODO(b/148769279): Disable lookup single target on lambda's for now.
    if (resolvedHolder.isInterface()
        && resolvedHolder.isProgramClass()
        && objectAllocationInfoCollection.isImmediateInterfaceOfInstantiatedLambda(
            resolvedHolder.asProgramClass())) {
      singleTargetLookupCache.addNoSingleTargetToCache(refinedReceiverType, method);
      return null;
    }
    DexClassAndMethod singleMethodTarget = null;
    DexProgramClass refinedLowerBound = null;
    if (dynamicReceiverType.hasDynamicLowerBoundType()) {
      DexClass refinedLowerBoundClass =
          definitionFor(dynamicReceiverType.getDynamicLowerBoundType().getClassType());
      if (refinedLowerBoundClass != null) {
        refinedLowerBound = refinedLowerBoundClass.asProgramClass();
        // TODO(b/154822960): Check if the lower bound is a subtype of the upper bound.
        if (refinedLowerBound != null && !isSubtype(refinedLowerBound.type, refinedReceiverType)) {
          refinedLowerBound = null;
        }
      }
    }
    LookupResultSuccess lookupResult =
        resolutionResult
            .lookupVirtualDispatchTargets(
                context.getHolder(),
                appView,
                refinedReceiverClass.asProgramClass(),
                refinedLowerBound)
            .asLookupResultSuccess();
    if (lookupResult != null && !lookupResult.isIncomplete()) {
      LookupTarget singleTarget = lookupResult.getSingleLookupTarget();
      if (singleTarget != null && singleTarget.isMethodTarget()) {
        singleMethodTarget = singleTarget.asMethodTarget().getTarget();
      }
    }
    if (!dynamicReceiverType.hasDynamicLowerBoundType()) {
      singleTargetLookupCache.addToCache(refinedReceiverType, method, singleMethodTarget);
    }
    return singleMethodTarget;
  }

  @SuppressWarnings("ReferenceEquality")
  private DispatchTargetLookupResult getMethodTargetFromExactRuntimeInformation(
      DexType refinedReceiverType,
      ClassTypeElement receiverLowerBoundType,
      SingleResolutionResult<?> resolution,
      DexClass refinedReceiverClass) {
    // If the lower-bound on the receiver type is the same as the upper-bound, then we have exact
    // runtime type information. In this case, the invoke will dispatch to the resolution result
    // from the runtime type of the receiver.
    if (receiverLowerBoundType != null
        && receiverLowerBoundType.getClassType() == refinedReceiverType) {
      if (refinedReceiverClass.isProgramClass()) {
        LookupMethodTarget methodTarget =
            resolution.lookupVirtualDispatchTarget(refinedReceiverClass.asProgramClass(), this);
        if (methodTarget == null
            || (methodTarget.getTarget().isProgramMethod()
                && !getKeepInfo()
                    .getMethodInfo(methodTarget.getTarget().asProgramMethod())
                    .isOptimizationAllowed(options()))) {
          // TODO(b/150640456): We should maybe only consider program methods.
          return new UnknownDispatchTargetLookupResult(resolution);
        }
        return new SingleDispatchTargetLookupResult(methodTarget.getTarget(), resolution);
      } else {
        // TODO(b/150640456): We should maybe only consider program methods.
        // If we resolved to a method on the refined receiver in the library, then we report the
        // method as a single target as well. This is a bit iffy since the library could change
        // implementation, but we use this for library modelling.
        DexClassAndMethod resolvedMethod = resolution.getResolutionPair();
        DexClassAndMethod targetOnReceiver =
            refinedReceiverClass.lookupVirtualClassMethod(resolvedMethod.getReference());
        if (targetOnReceiver != null && isOverriding(resolvedMethod, targetOnReceiver)) {
          return new SingleDispatchTargetLookupResult(targetOnReceiver, resolution);
        }
        return new UnknownDispatchTargetLookupResult(resolution);
      }
    }
    return null;
  }

  public AppInfoWithLiveness withSwitchMaps(Map<DexField, Int2ReferenceMap<DexField>> switchMaps) {
    assert checkIfObsolete();
    assert this.switchMaps.isEmpty();
    return new AppInfoWithLiveness(this, switchMaps);
  }

  /**
   * Visit all class definitions of classpath classes that are referenced in the compilation unit.
   *
   * <p>TODO(b/139464956): Only traverse the classpath types referenced from the live program.
   * Conservatively traces all classpath classes for now.
   */
  public void forEachReferencedClasspathClass(Consumer<DexClasspathClass> fn) {
    app().asDirect().classpathClasses().forEach(fn);
  }

  @Override
  public void forEachInstantiatedSubType(
      DexType type,
      Consumer<DexProgramClass> subTypeConsumer,
      Consumer<LambdaDescriptor> callSiteConsumer) {
    objectAllocationInfoCollection.forEachInstantiatedSubType(
        type, subTypeConsumer, callSiteConsumer, this);
  }

  public void forEachInstantiatedSubTypeInChain(
      DexProgramClass refinedReceiverUpperBound,
      DexProgramClass refinedReceiverLowerBound,
      Consumer<DexProgramClass> subTypeConsumer,
      Consumer<LambdaDescriptor> callSiteConsumer) {
    List<DexProgramClass> subTypes =
        computeProgramClassRelationChain(refinedReceiverLowerBound, refinedReceiverUpperBound);
    for (DexProgramClass subType : subTypes) {
      if (isInstantiatedOrPinned(subType)) {
        subTypeConsumer.accept(subType);
      }
    }
  }

  private boolean isInstantiatedOrPinned(DexProgramClass clazz) {
    return isInstantiatedDirectly(clazz) || isPinned(clazz) || isInstantiatedInterface(clazz);
  }

  public boolean isPinnedNotProgramOrLibraryOverride(DexDefinition definition) {
    if (isPinned(definition)) {
      return true;
    }
    if (definition.isDexEncodedMethod()) {
      DexEncodedMethod method = definition.asDexEncodedMethod();
      return !method.isProgramMethod(this) || method.isLibraryMethodOverride().isPossiblyTrue();
    }
    assert definition.isDexClass();
    DexClass clazz = definition.asDexClass();
    return clazz.isNotProgramClass() || isInstantiatedInterface(clazz.asProgramClass());
  }

  public SubtypingInfo computeSubtypingInfo() {
    return SubtypingInfo.create(this);
  }

  /** Predicate on types that *must* never be merged horizontally. */
  public boolean isNoHorizontalClassMergingOfType(DexType type) {
    return noClassMerging.contains(type) || noHorizontalClassMerging.contains(type);
  }

  /** Predicate on types that *must* never be merged vertically. */
  public boolean isNoVerticalClassMergingOfType(DexType type) {
    return noClassMerging.contains(type) || noVerticalClassMerging.contains(type);
  }

  public boolean verifyNoIteratingOverPrunedClasses() {
    classes()
        .forEach(
            clazz -> {
              assert !wasPruned(clazz.type) : clazz.type + " was not pruned";
            });
    return true;
  }
}
