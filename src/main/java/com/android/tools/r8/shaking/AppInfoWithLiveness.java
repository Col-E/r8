// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.graph.DexEncodedMethod.asProgramMethodOrNull;
import static com.android.tools.r8.graph.DexEncodedMethod.toMethodDefinitionOrNull;
import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult.isOverriding;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
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
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.FieldAccessInfoCollectionImpl;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.graph.InstantiatedSubTypeInfo;
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
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryAPIConverter;
import com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper;
import com.android.tools.r8.naming.SeedMapper;
import com.android.tools.r8.shaking.KeepInfo.Joiner;
import com.android.tools.r8.synthesis.CommittedItems;
import com.android.tools.r8.utils.CollectionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.PredicateSet;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.Visibility;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.structural.Ordered;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
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
  private final Set<DexMethod> targetedMethods;

  /** Method targets that lead to resolution errors such as non-existing or invalid targets. */
  private final Set<DexMethod> failedMethodResolutionTargets;

  /** Field targets that lead to resolution errors, such as non-existing or invalid targets. */
  private final Set<DexField> failedFieldResolutionTargets;

  /**
   * Set of program methods that are used as the bootstrap method for an invoke-dynamic instruction.
   */
  private final Set<DexMethod> bootstrapMethods;

  /** Set of methods that are the immediate target of an invoke-dynamic. */
  private final Set<DexMethod> methodsTargetedByInvokeDynamic;
  /** Set of virtual methods that are the immediate target of an invoke-direct. */
  private final Set<DexMethod> virtualMethodsTargetedByInvokeDirect;
  /**
   * Set of methods that belong to live classes and can be reached by invokes. These need to be
   * kept.
   */
  private final Set<DexMethod> liveMethods;
  /**
   * Information about all fields that are accessed by the program. The information includes whether
   * a given field is read/written by the program, and it also includes all indirect accesses to
   * each field. The latter is used, for example, during member rebinding.
   */
  private FieldAccessInfoCollectionImpl fieldAccessInfoCollection;
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
  /** All items with assumenosideeffects rule. */
  public final Map<DexMember<?, ?>, ProguardMemberRule> noSideEffects;
  /** All items with assumevalues rule. */
  public final Map<DexMember<?, ?>, ProguardMemberRule> assumedValues;
  /** All methods that should be inlined if possible due to a configuration directive. */
  private final Set<DexMethod> alwaysInline;
  /**
   * All methods that *must* never be inlined as a result of having a single caller due to a
   * configuration directive (testing only).
   */
  private final Set<DexMethod> neverInlineDueToSingleCaller;
  /** Items for which to print inlining decisions for (testing only). */
  private final Set<DexMethod> whyAreYouNotInlining;
  /** All methods that may not have any parameters with a constant value removed. */
  private final Set<DexMethod> keepConstantArguments;
  /** All methods that may not have any unused arguments removed. */
  private final Set<DexMethod> keepUnusedArguments;
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
      CommittedItems syntheticItems,
      ClassToFeatureSplitMap classToFeatureSplitMap,
      MainDexInfo mainDexInfo,
      Set<DexType> deadProtoTypes,
      MissingClasses missingClasses,
      Set<DexType> liveTypes,
      Set<DexMethod> targetedMethods,
      Set<DexMethod> failedMethodResolutionTargets,
      Set<DexField> failedFieldResolutionTargets,
      Set<DexMethod> bootstrapMethods,
      Set<DexMethod> methodsTargetedByInvokeDynamic,
      Set<DexMethod> virtualMethodsTargetedByInvokeDirect,
      Set<DexMethod> liveMethods,
      FieldAccessInfoCollectionImpl fieldAccessInfoCollection,
      MethodAccessInfoCollection methodAccessInfoCollection,
      ObjectAllocationInfoCollectionImpl objectAllocationInfoCollection,
      Map<DexCallSite, ProgramMethodSet> callSites,
      KeepInfoCollection keepInfo,
      Map<DexReference, ProguardMemberRule> mayHaveSideEffects,
      Map<DexMember<?, ?>, ProguardMemberRule> noSideEffects,
      Map<DexMember<?, ?>, ProguardMemberRule> assumedValues,
      Set<DexMethod> alwaysInline,
      Set<DexMethod> neverInlineDueToSingleCaller,
      Set<DexMethod> whyAreYouNotInlining,
      Set<DexMethod> keepConstantArguments,
      Set<DexMethod> keepUnusedArguments,
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
    super(syntheticItems, classToFeatureSplitMap, mainDexInfo, missingClasses);
    this.deadProtoTypes = deadProtoTypes;
    this.liveTypes = liveTypes;
    this.targetedMethods = targetedMethods;
    this.failedMethodResolutionTargets = failedMethodResolutionTargets;
    this.failedFieldResolutionTargets = failedFieldResolutionTargets;
    this.bootstrapMethods = bootstrapMethods;
    this.methodsTargetedByInvokeDynamic = methodsTargetedByInvokeDynamic;
    this.virtualMethodsTargetedByInvokeDirect = virtualMethodsTargetedByInvokeDirect;
    this.liveMethods = liveMethods;
    this.fieldAccessInfoCollection = fieldAccessInfoCollection;
    this.methodAccessInfoCollection = methodAccessInfoCollection;
    this.objectAllocationInfoCollection = objectAllocationInfoCollection;
    this.keepInfo = keepInfo;
    this.mayHaveSideEffects = mayHaveSideEffects;
    this.noSideEffects = noSideEffects;
    this.assumedValues = assumedValues;
    this.callSites = callSites;
    this.alwaysInline = alwaysInline;
    this.neverInlineDueToSingleCaller = neverInlineDueToSingleCaller;
    this.whyAreYouNotInlining = whyAreYouNotInlining;
    this.keepConstantArguments = keepConstantArguments;
    this.keepUnusedArguments = keepUnusedArguments;
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
        previous.deadProtoTypes,
        previous.getMissingClasses(),
        CollectionUtils.addAll(previous.liveTypes, committedItems.getCommittedProgramTypes()),
        previous.targetedMethods,
        previous.failedMethodResolutionTargets,
        previous.failedFieldResolutionTargets,
        previous.bootstrapMethods,
        previous.methodsTargetedByInvokeDynamic,
        previous.virtualMethodsTargetedByInvokeDirect,
        previous.liveMethods,
        previous.fieldAccessInfoCollection,
        previous.methodAccessInfoCollection,
        previous.objectAllocationInfoCollection,
        previous.callSites,
        previous.keepInfo,
        previous.mayHaveSideEffects,
        previous.noSideEffects,
        previous.assumedValues,
        previous.alwaysInline,
        previous.neverInlineDueToSingleCaller,
        previous.whyAreYouNotInlining,
        previous.keepConstantArguments,
        previous.keepUnusedArguments,
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
      AppInfoWithLiveness previous,
      PrunedItems prunedItems,
      ExecutorService executorService,
      List<Future<?>> futures) {
    this(
        previous.getSyntheticItems().commitPrunedItems(prunedItems),
        previous.getClassToFeatureSplitMap().withoutPrunedItems(prunedItems),
        previous.getMainDexInfo().withoutPrunedItems(prunedItems),
        previous.deadProtoTypes,
        previous.getMissingClasses(),
        pruneClasses(previous.liveTypes, prunedItems, executorService, futures),
        pruneMethods(previous.targetedMethods, prunedItems, executorService, futures),
        pruneMethods(previous.failedMethodResolutionTargets, prunedItems, executorService, futures),
        pruneFields(previous.failedFieldResolutionTargets, prunedItems, executorService, futures),
        pruneMethods(previous.bootstrapMethods, prunedItems, executorService, futures),
        pruneMethods(
            previous.methodsTargetedByInvokeDynamic, prunedItems, executorService, futures),
        pruneMethods(
            previous.virtualMethodsTargetedByInvokeDirect, prunedItems, executorService, futures),
        pruneMethods(previous.liveMethods, prunedItems, executorService, futures),
        previous.fieldAccessInfoCollection,
        previous.methodAccessInfoCollection,
        previous.objectAllocationInfoCollection.withoutPrunedItems(prunedItems),
        previous.callSites,
        extendPinnedItems(previous, prunedItems.getAdditionalPinnedItems()),
        previous.mayHaveSideEffects,
        pruneMapFromMembers(previous.noSideEffects, prunedItems, executorService, futures),
        pruneMapFromMembers(previous.assumedValues, prunedItems, executorService, futures),
        pruneMethods(previous.alwaysInline, prunedItems, executorService, futures),
        pruneMethods(previous.neverInlineDueToSingleCaller, prunedItems, executorService, futures),
        pruneMethods(previous.whyAreYouNotInlining, prunedItems, executorService, futures),
        pruneMethods(previous.keepConstantArguments, prunedItems, executorService, futures),
        pruneMethods(previous.keepUnusedArguments, prunedItems, executorService, futures),
        pruneMethods(previous.reprocess, prunedItems, executorService, futures),
        pruneMethods(previous.neverReprocess, prunedItems, executorService, futures),
        previous.alwaysClassInline,
        pruneClasses(previous.neverClassInline, prunedItems, executorService, futures),
        pruneClasses(previous.noClassMerging, prunedItems, executorService, futures),
        pruneClasses(previous.noVerticalClassMerging, prunedItems, executorService, futures),
        pruneClasses(previous.noHorizontalClassMerging, prunedItems, executorService, futures),
        pruneMembers(previous.neverPropagateValue, prunedItems, executorService, futures),
        pruneMapFromMembers(previous.identifierNameStrings, prunedItems, executorService, futures),
        prunedItems.hasRemovedClasses()
            ? CollectionUtils.mergeSets(previous.prunedTypes, prunedItems.getRemovedClasses())
            : previous.prunedTypes,
        previous.switchMaps,
        pruneClasses(previous.lockCandidates, prunedItems, executorService, futures),
        pruneMapFromClasses(previous.initClassReferences, prunedItems, executorService, futures),
        previous.recordFieldValuesReferences);
  }

  private static Set<DexType> pruneClasses(
      Set<DexType> methods,
      PrunedItems prunedItems,
      ExecutorService executorService,
      List<Future<?>> futures) {
    return pruneItems(methods, prunedItems.getRemovedClasses(), executorService, futures);
  }

  private static Set<DexField> pruneFields(
      Set<DexField> fields,
      PrunedItems prunedItems,
      ExecutorService executorService,
      List<Future<?>> futures) {
    return pruneItems(fields, prunedItems.getRemovedFields(), executorService, futures);
  }

  private static Set<DexMember<?, ?>> pruneMembers(
      Set<DexMember<?, ?>> members,
      PrunedItems prunedItems,
      ExecutorService executorService,
      List<Future<?>> futures) {
    if (prunedItems.hasRemovedMembers()) {
      futures.add(
          ThreadUtils.processAsynchronously(
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
              },
              executorService));
    }
    return members;
  }

  private static Set<DexMethod> pruneMethods(
      Set<DexMethod> methods,
      PrunedItems prunedItems,
      ExecutorService executorService,
      List<Future<?>> futures) {
    return pruneItems(methods, prunedItems.getRemovedMethods(), executorService, futures);
  }

  private static <T> Set<T> pruneItems(
      Set<T> items, Set<T> removedItems, ExecutorService executorService, List<Future<?>> futures) {
    if (!removedItems.isEmpty()) {

      futures.add(
          ThreadUtils.processAsynchronously(
              () -> {
                if (items.size() <= removedItems.size()) {
                  items.removeAll(removedItems);
                } else {
                  removedItems.forEach(items::remove);
                }
              },
              executorService));
    }
    return items;
  }

  private static <V> Map<DexType, V> pruneMapFromClasses(
      Map<DexType, V> map,
      PrunedItems prunedItems,
      ExecutorService executorService,
      List<Future<?>> futures) {
    return pruneMap(map, prunedItems.getRemovedClasses(), executorService, futures);
  }

  private static <V> Map<DexMember<?, ?>, V> pruneMapFromMembers(
      Map<DexMember<?, ?>, V> map,
      PrunedItems prunedItems,
      ExecutorService executorService,
      List<Future<?>> futures) {
    if (prunedItems.hasRemovedMembers()) {
      futures.add(
          ThreadUtils.processAsynchronously(
              () -> {
                Set<DexField> removedFields = prunedItems.getRemovedFields();
                Set<DexMethod> removedMethods = prunedItems.getRemovedMethods();
                if (map.size() <= removedFields.size() + removedMethods.size()) {
                  map.keySet()
                      .removeIf(
                          member ->
                              member.isDexField()
                                  ? removedFields.contains(member.asDexField())
                                  : removedMethods.contains(member.asDexMethod()));
                } else {
                  removedFields.forEach(map::remove);
                  removedMethods.forEach(map::remove);
                }
              },
              executorService));
    }
    return map;
  }

  private static Object2BooleanMap<DexMember<?, ?>> pruneMapFromMembers(
      Object2BooleanMap<DexMember<?, ?>> map,
      PrunedItems prunedItems,
      ExecutorService executorService,
      List<Future<?>> futures) {
    if (prunedItems.hasRemovedMembers()) {
      futures.add(
          ThreadUtils.processAsynchronously(
              () -> {
                Set<DexField> removedFields = prunedItems.getRemovedFields();
                Set<DexMethod> removedMethods = prunedItems.getRemovedMethods();
                if (map.size() <= removedFields.size() + removedMethods.size()) {
                  map.keySet()
                      .removeIf(
                          member ->
                              member.isDexField()
                                  ? removedFields.contains(member.asDexField())
                                  : removedMethods.contains(member.asDexMethod()));
                } else {
                  removedFields.forEach(map::remove);
                  removedMethods.forEach(map::remove);
                }
              },
              executorService));
    }
    return map;
  }

  private static <K, V> Map<K, V> pruneMap(
      Map<K, V> map,
      Set<K> removedItems,
      ExecutorService executorService,
      List<Future<?>> futures) {
    if (!removedItems.isEmpty()) {
      futures.add(
          ThreadUtils.processAsynchronously(
              () -> {
                if (map.size() <= removedItems.size()) {
                  map.keySet().removeAll(removedItems);
                } else {
                  removedItems.forEach(map::remove);
                }
              },
              executorService));
    }
    return map;
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
        deadProtoTypes,
        getMissingClasses(),
        liveTypes,
        targetedMethods,
        failedMethodResolutionTargets,
        failedFieldResolutionTargets,
        bootstrapMethods,
        methodsTargetedByInvokeDynamic,
        virtualMethodsTargetedByInvokeDirect,
        liveMethods,
        fieldAccessInfoCollection,
        methodAccessInfoCollection,
        objectAllocationInfoCollection,
        callSites,
        keepInfo,
        mayHaveSideEffects,
        noSideEffects,
        assumedValues,
        alwaysInline,
        neverInlineDueToSingleCaller,
        whyAreYouNotInlining,
        keepConstantArguments,
        keepUnusedArguments,
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
    this.failedMethodResolutionTargets = previous.failedMethodResolutionTargets;
    this.failedFieldResolutionTargets = previous.failedFieldResolutionTargets;
    this.bootstrapMethods = previous.bootstrapMethods;
    this.methodsTargetedByInvokeDynamic = previous.methodsTargetedByInvokeDynamic;
    this.virtualMethodsTargetedByInvokeDirect = previous.virtualMethodsTargetedByInvokeDirect;
    this.liveMethods = previous.liveMethods;
    this.fieldAccessInfoCollection = previous.fieldAccessInfoCollection;
    this.methodAccessInfoCollection = previous.methodAccessInfoCollection;
    this.objectAllocationInfoCollection = previous.objectAllocationInfoCollection;
    this.keepInfo = previous.keepInfo;
    this.mayHaveSideEffects = previous.mayHaveSideEffects;
    this.noSideEffects = previous.noSideEffects;
    this.assumedValues = previous.assumedValues;
    this.callSites = previous.callSites;
    this.alwaysInline = previous.alwaysInline;
    this.neverInlineDueToSingleCaller = previous.neverInlineDueToSingleCaller;
    this.whyAreYouNotInlining = previous.whyAreYouNotInlining;
    this.keepConstantArguments = previous.keepConstantArguments;
    this.keepUnusedArguments = previous.keepUnusedArguments;
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
            || InterfaceDesugaringSyntheticHelper.isEmulatedLibraryClassType(type)
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

  public boolean isFailedResolutionTarget(DexMethod method) {
    return failedMethodResolutionTargets.contains(method);
  }

  public Set<DexMethod> getFailedMethodResolutionTargets() {
    return failedMethodResolutionTargets;
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

  public boolean isMethodTargetedByInvokeDynamic(DexMethod method) {
    return methodsTargetedByInvokeDynamic.contains(method);
  }

  public boolean isMethodTargetedByInvokeDynamic(ProgramMethod method) {
    return isMethodTargetedByInvokeDynamic(method.getReference());
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

  public boolean isAssumeMethod(DexClassAndMethod method) {
    return isAssumeNoSideEffectsMethod(method) || isAssumeValuesMethod(method);
  }

  public boolean isAssumeNoSideEffectsMethod(DexMethod method) {
    return noSideEffects.containsKey(method);
  }

  public boolean isAssumeNoSideEffectsMethod(DexClassAndMethod method) {
    return isAssumeNoSideEffectsMethod(method.getReference());
  }

  public boolean isAssumeValuesMethod(DexMethod method) {
    return assumedValues.containsKey(method);
  }

  public boolean isAssumeValuesMethod(DexClassAndMethod method) {
    return isAssumeValuesMethod(method.getReference());
  }

  public boolean isWhyAreYouNotInliningMethod(DexMethod method) {
    return whyAreYouNotInlining.contains(method);
  }

  public boolean hasNoWhyAreYouNotInliningMethods() {
    return whyAreYouNotInlining.isEmpty();
  }

  public boolean isKeepConstantArgumentsMethod(ProgramMethod method) {
    return isKeepConstantArgumentsMethod(method.getReference());
  }

  public boolean isKeepConstantArgumentsMethod(DexMethod method) {
    return keepConstantArguments.contains(method);
  }

  public boolean isKeepUnusedArgumentsMethod(ProgramMethod method) {
    return isKeepUnusedArgumentsMethod(method.getReference());
  }

  public boolean isKeepUnusedArgumentsMethod(DexMethod method) {
    return keepUnusedArguments.contains(method);
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
  public Set<DexEncodedMethod> lookupLambdaImplementedMethods(DexCallSite callSite) {
    assert checkIfObsolete();
    List<DexType> callSiteInterfaces = LambdaDescriptor.getInterfaces(callSite, this);
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
    // TODO(b/192924387): When we enqueue a field as a root item, we should maybe create a
    //  FieldAccessInfo that describes the field is read and written using reflection.
    return !getKeepInfo().getFieldInfo(reference, this).isShrinkingAllowed(options());
  }

  public boolean isFieldRead(DexEncodedField encodedField) {
    assert checkIfObsolete();
    DexField field = encodedField.getReference();
    FieldAccessInfo info = getFieldAccessInfoCollection().get(field);
    if (info != null && info.isRead()) {
      return true;
    }
    if (isPinned(field)) {
      return true;
    }
    // For library classes we don't know whether a field is read.
    return isLibraryOrClasspathField(encodedField);
  }

  public boolean isFieldWritten(DexEncodedField encodedField) {
    assert checkIfObsolete();
    return isFieldWrittenByFieldPutInstruction(encodedField)
        || isPinned(encodedField.getReference());
  }

  public boolean isFieldWrittenByFieldPutInstruction(DexEncodedField encodedField) {
    assert checkIfObsolete();
    DexField field = encodedField.getReference();
    FieldAccessInfo info = getFieldAccessInfoCollection().get(field);
    if (info != null && info.isWritten()) {
      // The field is written directly by the program itself.
      return true;
    }
    // For library classes we don't know whether a field is rewritten.
    return isLibraryOrClasspathField(encodedField);
  }

  public boolean isFieldOnlyWrittenInMethod(DexEncodedField field, DexEncodedMethod method) {
    assert checkIfObsolete();
    assert isFieldWritten(field) : "Expected field `" + field.toSourceString() + "` to be written";
    if (isPinned(field.getReference())) {
      return false;
    }
    return isFieldOnlyWrittenInMethodIgnoringPinning(field, method);
  }

  public boolean isFieldOnlyWrittenInMethodIgnoringPinning(
      DexEncodedField field, DexEncodedMethod method) {
    assert checkIfObsolete();
    assert isFieldWritten(field) : "Expected field `" + field.toSourceString() + "` to be written";
    FieldAccessInfo fieldAccessInfo = getFieldAccessInfoCollection().get(field.getReference());
    return fieldAccessInfo != null
        && fieldAccessInfo.isWritten()
        && !fieldAccessInfo.isWrittenOutside(method);
  }

  public boolean isInstanceFieldWrittenOnlyInInstanceInitializers(DexClassAndField field) {
    return isInstanceFieldWrittenOnlyInInstanceInitializers(field.getDefinition());
  }

  public boolean isInstanceFieldWrittenOnlyInInstanceInitializers(DexEncodedField field) {
    assert checkIfObsolete();
    assert isFieldWritten(field) : "Expected field `" + field.toSourceString() + "` to be written";
    if (isPinned(field.getReference())) {
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

  public boolean isStaticFieldWrittenOnlyInEnclosingStaticInitializer(DexEncodedField field) {
    assert checkIfObsolete();
    assert isFieldWritten(field) : "Expected field `" + field.toSourceString() + "` to be written";
    DexEncodedMethod staticInitializer =
        definitionFor(field.getHolderType()).asProgramClass().getClassInitializer();
    return staticInitializer != null && isFieldOnlyWrittenInMethod(field, staticInitializer);
  }

  public boolean mayPropagateArgumentsTo(ProgramMethod method) {
    DexMethod reference = method.getReference();
    return method.getDefinition().hasCode()
        && !method.getDefinition().isLibraryMethodOverride().isPossiblyTrue()
        && !neverReprocess.contains(reference)
        && !keepInfo.getMethodInfo(method).isPinned(options());
  }

  public boolean mayPropagateValueFor(DexClassAndMember<?, ?> member) {
    assert checkIfObsolete();
    return member.getReference().apply(this::mayPropagateValueFor, this::mayPropagateValueFor);
  }

  public boolean mayPropagateValueFor(DexField field) {
    assert checkIfObsolete();
    if (neverPropagateValue.contains(field)) {
      return false;
    }
    if (isPinned(field) && !field.getType().isAlwaysNull(this)) {
      return false;
    }
    return true;
  }

  public boolean mayPropagateValueFor(DexMethod method) {
    assert checkIfObsolete();
    if (neverPropagateValue.contains(method)) {
      return false;
    }
    if (!method.getReturnType().isAlwaysNull(this)
        && !getKeepInfo().getMethodInfo(method, this).isOptimizationAllowed(options())) {
      return false;
    }
    return true;
  }

  private boolean isLibraryOrClasspathField(DexEncodedField field) {
    DexClass holder = definitionFor(field.getHolderType());
    return holder == null || holder.isLibraryClass() || holder.isClasspathClass();
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

  public boolean isMinificationAllowed(DexReference reference) {
    return options().isMinificationEnabled()
        && keepInfo.getInfo(reference, this).isMinificationAllowed(options());
  }

  public boolean isAccessModificationAllowed(ProgramDefinition definition) {
    return isAccessModificationAllowed(definition.getReference());
  }

  public boolean isAccessModificationAllowed(DexReference reference) {
    assert options().getProguardConfiguration().isAccessModificationAllowed();
    return keepInfo.getInfo(reference, this).isAccessModificationAllowed(options());
  }

  public boolean isRepackagingAllowed(DexProgramClass clazz, AppView<?> appView) {
    if (!options().isRepackagingEnabled()) {
      return false;
    }
    if (!keepInfo.getInfo(clazz).isRepackagingAllowed(clazz, options())) {
      return false;
    }
    SeedMapper applyMappingSeedMapper = appView.getApplyMappingSeedMapper();
    if (applyMappingSeedMapper != null && applyMappingSeedMapper.hasMapping(clazz.type)) {
      return false;
    }
    return clazz
        .traverseProgramMembers(
            member -> {
              if (keepInfo.getInfo(member).isRepackagingAllowed(member, options())) {
                return TraversalContinuation.CONTINUE;
              }
              return TraversalContinuation.BREAK;
            })
        .shouldContinue();
  }

  public boolean isPinned(DexReference reference) {
    assert checkIfObsolete();
    return keepInfo.isPinned(reference, this, options());
  }

  public boolean isPinned(DexDefinition definition) {
    assert definition != null;
    return isPinned(definition.getReference());
  }

  public boolean isPinned(DexClassAndMember<?, ?> member) {
    assert member != null;
    return isPinned(member.getReference());
  }

  public boolean hasPinnedInstanceInitializer(DexType type) {
    assert type.isClassType();
    DexProgramClass clazz = asProgramClassOrNull(definitionFor(type));
    if (clazz != null) {
      for (DexEncodedMethod method : clazz.directMethods()) {
        if (method.isInstanceInitializer() && isPinned(method.getReference())) {
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
      PrunedItems prunedItems, ExecutorService executorService) throws ExecutionException {
    assert getClass() == AppInfoWithLiveness.class;
    assert checkIfObsolete();
    if (prunedItems.isEmpty()) {
      assert app() == prunedItems.getPrunedApp();
      return this;
    }
    if (prunedItems.hasRemovedClasses()) {
      // Rebuild the hierarchy.
      objectAllocationInfoCollection.mutate(
          mutator -> mutator.removeAllocationsForPrunedItems(prunedItems), this);
      keepInfo.mutate(keepInfo -> keepInfo.removeKeepInfoForPrunedItems(prunedItems));
    } else if (prunedItems.hasRemovedMembers()) {
      keepInfo.mutate(keepInfo -> keepInfo.removeKeepInfoForPrunedItems(prunedItems));
    }
    List<Future<?>> futures = new ArrayList<>();
    AppInfoWithLiveness appInfoWithLiveness =
        new AppInfoWithLiveness(this, prunedItems, executorService, futures);
    ThreadUtils.awaitFutures(futures);
    return appInfoWithLiveness;
  }

  public AppInfoWithLiveness rebuildWithLiveness(CommittedItems committedItems) {
    return new AppInfoWithLiveness(this, committedItems);
  }

  public AppInfoWithLiveness rewrittenWithLens(
      DirectMappedDexApplication application, NonIdentityGraphLens lens) {
    assert checkIfObsolete();

    // Switchmap classes should never be affected by renaming.
    assert lens.assertDefinitionsNotModified(
        switchMaps.keySet().stream()
            .map(this::resolveField)
            .filter(FieldResolutionResult::isSuccessfulResolution)
            .map(FieldResolutionResult::getResolvedField)
            .collect(Collectors.toList()));

    CommittedItems committedItems = getSyntheticItems().commitRewrittenWithLens(application, lens);
    DexDefinitionSupplier definitionSupplier =
        committedItems.getApplication().getDefinitionsSupplier(committedItems);
    return new AppInfoWithLiveness(
        committedItems,
        getClassToFeatureSplitMap().rewrittenWithLens(lens),
        getMainDexInfo().rewrittenWithLens(getSyntheticItems(), lens),
        deadProtoTypes,
        getMissingClasses(),
        lens.rewriteReferences(liveTypes),
        lens.rewriteReferences(targetedMethods),
        lens.rewriteReferences(failedMethodResolutionTargets),
        lens.rewriteReferences(failedFieldResolutionTargets),
        lens.rewriteReferences(bootstrapMethods),
        lens.rewriteReferences(methodsTargetedByInvokeDynamic),
        lens.rewriteReferences(virtualMethodsTargetedByInvokeDirect),
        lens.rewriteReferences(liveMethods),
        fieldAccessInfoCollection.rewrittenWithLens(definitionSupplier, lens),
        methodAccessInfoCollection.rewrittenWithLens(definitionSupplier, lens),
        objectAllocationInfoCollection.rewrittenWithLens(definitionSupplier, lens),
        lens.rewriteCallSites(callSites, definitionSupplier),
        keepInfo.rewrite(definitionSupplier, lens, application.options),
        // Take any rule in case of collisions.
        lens.rewriteReferenceKeys(mayHaveSideEffects, (reference, rules) -> ListUtils.first(rules)),
        // Take the assume rule from the representative in case of collisions.
        lens.rewriteReferenceKeys(
            noSideEffects,
            (reference, rules) -> noSideEffects.get(lens.getOriginalMemberSignature(reference))),
        lens.rewriteReferenceKeys(
            assumedValues,
            (reference, rules) -> assumedValues.get(lens.getOriginalMemberSignature(reference))),
        lens.rewriteReferences(alwaysInline),
        lens.rewriteReferences(neverInlineDueToSingleCaller),
        lens.rewriteReferences(whyAreYouNotInlining),
        lens.rewriteReferences(keepConstantArguments),
        lens.rewriteReferences(keepUnusedArguments),
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

  public DexEncodedMethod lookupSingleTarget(
      Type type,
      DexMethod target,
      ProgramMethod context,
      LibraryModeledPredicate modeledPredicate) {
    assert checkIfObsolete();
    DexType holder = target.holder;
    if (!holder.isClassType()) {
      return null;
    }
    switch (type) {
      case VIRTUAL:
        return lookupSingleVirtualTarget(target, context, false, modeledPredicate);
      case INTERFACE:
        return lookupSingleVirtualTarget(target, context, true, modeledPredicate);
      case DIRECT:
        return lookupDirectTarget(target, context);
      case STATIC:
        return lookupStaticTarget(target, context);
      case SUPER:
        return toMethodDefinitionOrNull(lookupSuperTarget(target, context));
      default:
        return null;
    }
  }

  public ProgramMethod lookupSingleProgramTarget(
      Type type,
      DexMethod target,
      ProgramMethod context,
      LibraryModeledPredicate modeledPredicate) {
    return asProgramMethodOrNull(lookupSingleTarget(type, target, context, modeledPredicate), this);
  }

  /** For mapping invoke virtual instruction to single target method. */
  public DexEncodedMethod lookupSingleVirtualTarget(
      DexMethod method, ProgramMethod context, boolean isInterface) {
    assert checkIfObsolete();
    return lookupSingleVirtualTarget(
        method, context, isInterface, type -> false, method.holder, null);
  }

  /** For mapping invoke virtual instruction to single target method. */
  public DexEncodedMethod lookupSingleVirtualTarget(
      DexMethod method,
      ProgramMethod context,
      boolean isInterface,
      LibraryModeledPredicate modeledPredicate) {
    assert checkIfObsolete();
    return lookupSingleVirtualTarget(
        method, context, isInterface, modeledPredicate, method.holder, null);
  }

  public DexEncodedMethod lookupSingleVirtualTarget(
      DexMethod method,
      ProgramMethod context,
      boolean isInterface,
      LibraryModeledPredicate modeledPredicate,
      DexType refinedReceiverType,
      ClassTypeElement receiverLowerBoundType) {
    assert checkIfObsolete();
    assert refinedReceiverType != null;
    if (!refinedReceiverType.isClassType()) {
      // The refined receiver is not of class type and we will not be able to find a single target
      // (it is either primitive or array).
      return null;
    }
    DexClass initialResolutionHolder = definitionFor(method.holder);
    if (initialResolutionHolder == null || initialResolutionHolder.isInterface() != isInterface) {
      return null;
    }
    DexClass refinedReceiverClass = definitionFor(refinedReceiverType);
    if (refinedReceiverClass == null) {
      // The refined receiver is not defined in the program and we cannot determine the target.
      return null;
    }
    if (receiverLowerBoundType == null
        && singleTargetLookupCache.hasCachedItem(refinedReceiverType, method)) {
      DexEncodedMethod cachedItem =
          singleTargetLookupCache.getCachedItem(refinedReceiverType, method);
      return cachedItem;
    }
    SingleResolutionResult resolution =
        resolveMethodOn(initialResolutionHolder, method).asSingleResolution();
    if (resolution == null
        || resolution.isAccessibleForVirtualDispatchFrom(context.getHolder(), this).isFalse()) {
      return null;
    }
    // If the method is modeled, return the resolution.
    DexEncodedMethod resolvedMethod = resolution.getResolvedMethod();
    if (modeledPredicate.isModeled(resolution.getResolvedHolder().type)) {
      if (resolution.getResolvedHolder().isFinal()
          || (resolvedMethod.isFinal() && resolvedMethod.accessFlags.isPublic())) {
        singleTargetLookupCache.addToCache(refinedReceiverType, method, resolvedMethod);
        return resolvedMethod;
      }
    }
    DexEncodedMethod exactTarget =
        getMethodTargetFromExactRuntimeInformation(
            refinedReceiverType, receiverLowerBoundType, resolution, refinedReceiverClass);
    if (exactTarget != null) {
      // We are not caching single targets here because the cache does not include the
      // lower bound dimension.
      return exactTarget == DexEncodedMethod.SENTINEL ? null : exactTarget;
    }
    if (refinedReceiverClass.isNotProgramClass()) {
      // The refined receiver is not defined in the program and we cannot determine the target.
      singleTargetLookupCache.addToCache(refinedReceiverType, method, null);
      return null;
    }
    DexClass resolvedHolder = resolution.getResolvedHolder();
    // TODO(b/148769279): Disable lookup single target on lambda's for now.
    if (resolvedHolder.isInterface()
        && resolvedHolder.isProgramClass()
        && objectAllocationInfoCollection.isImmediateInterfaceOfInstantiatedLambda(
            resolvedHolder.asProgramClass())) {
      singleTargetLookupCache.addToCache(refinedReceiverType, method, null);
      return null;
    }
    DexEncodedMethod singleMethodTarget = null;
    DexProgramClass refinedLowerBound = null;
    if (receiverLowerBoundType != null) {
      DexClass refinedLowerBoundClass = definitionFor(receiverLowerBoundType.getClassType());
      if (refinedLowerBoundClass != null) {
        refinedLowerBound = refinedLowerBoundClass.asProgramClass();
        // TODO(b/154822960): Check if the lower bound is a subtype of the upper bound.
        if (refinedLowerBound != null && !isSubtype(refinedLowerBound.type, refinedReceiverType)) {
          refinedLowerBound = null;
        }
      }
    }
    LookupResultSuccess lookupResult =
        resolution
            .lookupVirtualDispatchTargets(
                context.getHolder(), this, refinedReceiverClass.asProgramClass(), refinedLowerBound)
            .asLookupResultSuccess();
    if (lookupResult != null && !lookupResult.isIncomplete()) {
      LookupTarget singleTarget = lookupResult.getSingleLookupTarget();
      if (singleTarget != null && singleTarget.isMethodTarget()) {
        singleMethodTarget = singleTarget.asMethodTarget().getDefinition();
      }
    }
    if (receiverLowerBoundType == null) {
      singleTargetLookupCache.addToCache(refinedReceiverType, method, singleMethodTarget);
    }
    return singleMethodTarget;
  }

  private DexEncodedMethod getMethodTargetFromExactRuntimeInformation(
      DexType refinedReceiverType,
      ClassTypeElement receiverLowerBoundType,
      SingleResolutionResult resolution,
      DexClass refinedReceiverClass) {
    // If the lower-bound on the receiver type is the same as the upper-bound, then we have exact
    // runtime type information. In this case, the invoke will dispatch to the resolution result
    // from the runtime type of the receiver.
    if (receiverLowerBoundType != null
        && receiverLowerBoundType.getClassType() == refinedReceiverType) {
      if (refinedReceiverClass.isProgramClass()) {
        DexClassAndMethod clazzAndMethod =
            resolution.lookupVirtualDispatchTarget(refinedReceiverClass.asProgramClass(), this);
        if (clazzAndMethod == null
            || (clazzAndMethod.isProgramMethod()
                && !getKeepInfo()
                    .getMethodInfo(clazzAndMethod.asProgramMethod())
                    .isOptimizationAllowed(options()))) {
          // TODO(b/150640456): We should maybe only consider program methods.
          return DexEncodedMethod.SENTINEL;
        }
        return clazzAndMethod.getDefinition();
      } else {
        // TODO(b/150640456): We should maybe only consider program methods.
        // If we resolved to a method on the refined receiver in the library, then we report the
        // method as a single target as well. This is a bit iffy since the library could change
        // implementation, but we use this for library modelling.
        DexEncodedMethod resolvedMethod = resolution.getResolvedMethod();
        DexEncodedMethod targetOnReceiver =
            refinedReceiverClass.lookupVirtualMethod(resolvedMethod.getReference());
        if (targetOnReceiver != null && isOverriding(resolvedMethod, targetOnReceiver)) {
          return targetOnReceiver;
        }
        return DexEncodedMethod.SENTINEL;
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

  /**
   * Visits all class definitions that are a live program type or a type above it in the hierarchy.
   *
   * <p>Any given definition will be visited at most once. No guarantees are places on the order.
   */
  public void forEachTypeInHierarchyOfLiveProgramClasses(Consumer<DexClass> fn) {
    forEachTypeInHierarchyOfLiveProgramClasses(
        fn,
        ListUtils.map(liveTypes, t -> definitionFor(t).asProgramClass()),
        objectAllocationInfoCollection.getInstantiatedLambdaInterfaces(),
        this);
  }

  // Split in a static method so it can be used during construction.
  static void forEachTypeInHierarchyOfLiveProgramClasses(
      Consumer<DexClass> fn,
      Collection<DexProgramClass> liveProgramClasses,
      Set<DexType> lambdaInterfaces,
      AppInfoWithClassHierarchy appInfo) {
    Set<DexType> seen = Sets.newIdentityHashSet();
    liveProgramClasses.forEach(c -> seen.add(c.type));
    Deque<DexType> worklist = new ArrayDeque<>(lambdaInterfaces);
    for (DexProgramClass liveProgramClass : liveProgramClasses) {
      fn.accept(liveProgramClass);
      DexType superType = liveProgramClass.superType;
      if (superType != null && seen.add(superType)) {
        worklist.add(superType);
      }
      for (DexType iface : liveProgramClass.interfaces.values) {
        if (seen.add(iface)) {
          worklist.add(iface);
        }
      }
    }
    while (!worklist.isEmpty()) {
      DexType type = worklist.pop();
      DexClass clazz = appInfo.definitionFor(type);
      if (clazz != null) {
        fn.accept(clazz);
        if (clazz.superType != null && seen.add(clazz.superType)) {
          worklist.add(clazz.superType);
        }
        for (DexType iface : clazz.interfaces.values) {
          if (seen.add(iface)) {
            worklist.add(iface);
          }
        }
      }
    }
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
    return isInstantiatedDirectly(clazz) || isPinned(clazz.type) || isInstantiatedInterface(clazz);
  }

  public boolean isPinnedNotProgramOrLibraryOverride(DexDefinition definition) {
    if (isPinned(definition.getReference())) {
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
    return new SubtypingInfo(this);
  }

  public boolean mayHaveFinalizeMethodDirectlyOrIndirectly(ClassTypeElement type) {
    // Special case for java.lang.Object.
    if (type.getClassType() == dexItemFactory().objectType) {
      if (type.getInterfaces().isEmpty()) {
        // The type java.lang.Object could be any instantiated type. Assume a finalizer exists.
        return true;
      }
      return type.getInterfaces().anyMatch((iface, isKnown) -> mayHaveFinalizer(iface));
    }
    return mayHaveFinalizer(type.getClassType());
  }

  private boolean mayHaveFinalizer(DexType type) {
    // A type may have an active finalizer if any derived instance has a finalizer.
    return objectAllocationInfoCollection
        .traverseInstantiatedSubtypes(
            type,
            clazz -> {
              if (objectAllocationInfoCollection.isInterfaceWithUnknownSubtypeHierarchy(clazz)) {
                return TraversalContinuation.BREAK;
              } else {
                SingleResolutionResult resolution =
                    resolveMethodOn(clazz, dexItemFactory().objectMembers.finalize)
                        .asSingleResolution();
                if (resolution != null && resolution.getResolvedHolder().isProgramClass()) {
                  return TraversalContinuation.BREAK;
                }
              }
              return TraversalContinuation.CONTINUE;
            },
            lambda -> {
              // Lambda classes do not have finalizers.
              return TraversalContinuation.CONTINUE;
            },
            this)
        .shouldBreak();
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
