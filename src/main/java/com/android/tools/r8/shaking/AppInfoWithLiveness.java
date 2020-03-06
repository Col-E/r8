// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.graph.GraphLense.rewriteReferenceKeys;
import static com.android.tools.r8.graph.ResolutionResult.SingleResolutionResult.isOverriding;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.EnumValueInfoMapCollection;
import com.android.tools.r8.graph.EnumValueInfoMapCollection.EnumValueInfoMap;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.FieldAccessInfoCollectionImpl;
import com.android.tools.r8.graph.FieldAccessInfoImpl;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.GraphLense.NestedGraphLense;
import com.android.tools.r8.graph.InstantiatedSubTypeInfo;
import com.android.tools.r8.graph.LookupResult.LookupResultSuccess;
import com.android.tools.r8.graph.LookupTarget;
import com.android.tools.r8.graph.ObjectAllocationInfoCollection;
import com.android.tools.r8.graph.ObjectAllocationInfoCollectionImpl;
import com.android.tools.r8.graph.PresortedComparable;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.graph.ResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.analysis.type.ClassTypeLatticeElement;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.utils.CollectionUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.PredicateSet;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.ImmutableSortedSet.Builder;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Encapsulates liveness and reachability information for an application. */
public class AppInfoWithLiveness extends AppInfoWithSubtyping implements InstantiatedSubTypeInfo {

  /** Set of types that are mentioned in the program, but for which no definition exists. */
  private final Set<DexType> missingTypes;
  /**
   * Set of types that are mentioned in the program. We at least need an empty abstract classitem
   * for these.
   */
  private final Set<DexType> liveTypes;
  /**
   * Set of service types (from META-INF/services/) that may have been instantiated reflectively via
   * ServiceLoader.load() or ServiceLoader.loadInstalled().
   */
  public final Set<DexType> instantiatedAppServices;
  /** Cache for {@link #isInstantiatedDirectlyOrIndirectly(DexProgramClass)}. */
  private final IdentityHashMap<DexType, Boolean> indirectlyInstantiatedTypes =
      new IdentityHashMap<>();
  /**
   * Set of methods that are the immediate target of an invoke. They might not actually be live but
   * are required so that invokes can find the method. If such a method is not live (i.e. not
   * contained in {@link #liveMethods}, it may be marked as abstract and its implementation may be
   * removed.
   */
  final SortedSet<DexMethod> targetedMethods;

  /** Set of targets that lead to resolution errors, such as non-existing or invalid targets. */
  public final Set<DexMethod> failedResolutionTargets;

  /**
   * Set of program methods that are used as the bootstrap method for an invoke-dynamic instruction.
   */
  public final SortedSet<DexMethod> bootstrapMethods;
  /** Set of methods that are the immediate target of an invoke-dynamic. */
  public final SortedSet<DexMethod> methodsTargetedByInvokeDynamic;
  /** Set of virtual methods that are the immediate target of an invoke-direct. */
  final SortedSet<DexMethod> virtualMethodsTargetedByInvokeDirect;
  /**
   * Set of methods that belong to live classes and can be reached by invokes. These need to be
   * kept.
   */
  public final SortedSet<DexMethod> liveMethods;
  /**
   * Information about all fields that are accessed by the program. The information includes whether
   * a given field is read/written by the program, and it also includes all indirect accesses to
   * each field. The latter is used, for example, during member rebinding.
   */
  private final FieldAccessInfoCollectionImpl fieldAccessInfoCollection;
  /** Information about instantiated classes and their allocation sites. */
  private final ObjectAllocationInfoCollectionImpl objectAllocationInfoCollection;
  /** Set of all methods referenced in virtual invokes, along with calling context. */
  public final SortedMap<DexMethod, Set<DexEncodedMethod>> virtualInvokes;
  /** Set of all methods referenced in interface invokes, along with calling context. */
  public final SortedMap<DexMethod, Set<DexEncodedMethod>> interfaceInvokes;
  /** Set of all methods referenced in super invokes, along with calling context. */
  public final SortedMap<DexMethod, Set<DexEncodedMethod>> superInvokes;
  /** Set of all methods referenced in direct invokes, along with calling context. */
  public final SortedMap<DexMethod, Set<DexEncodedMethod>> directInvokes;
  /** Set of all methods referenced in static invokes, along with calling context. */
  public final SortedMap<DexMethod, Set<DexEncodedMethod>> staticInvokes;
  /**
   * Set of live call sites in the code. Note that if desugaring has taken place call site objects
   * will have been removed from the code.
   */
  public final Set<DexCallSite> callSites;
  /** Set of all items that have to be kept independent of whether they are used. */
  final Set<DexReference> pinnedItems;
  /** All items with assumemayhavesideeffects rule. */
  public final Map<DexReference, ProguardMemberRule> mayHaveSideEffects;
  /** All items with assumenosideeffects rule. */
  public final Map<DexReference, ProguardMemberRule> noSideEffects;
  /** All items with assumevalues rule. */
  public final Map<DexReference, ProguardMemberRule> assumedValues;
  /** All methods that should be inlined if possible due to a configuration directive. */
  public final Set<DexMethod> alwaysInline;
  /** All methods that *must* be inlined due to a configuration directive (testing only). */
  public final Set<DexMethod> forceInline;
  /** All methods that *must* never be inlined due to a configuration directive (testing only). */
  public final Set<DexMethod> neverInline;
  /** Items for which to print inlining decisions for (testing only). */
  public final Set<DexMethod> whyAreYouNotInlining;
  /** All methods that may not have any parameters with a constant value removed. */
  public final Set<DexMethod> keepConstantArguments;
  /** All methods that may not have any unused arguments removed. */
  public final Set<DexMethod> keepUnusedArguments;
  /** All methods that must be reprocessed (testing only). */
  public final Set<DexMethod> reprocess;
  /** All methods that must not be reprocessed (testing only). */
  public final Set<DexMethod> neverReprocess;
  /** All types that should be inlined if possible due to a configuration directive. */
  public final PredicateSet<DexType> alwaysClassInline;
  /** All types that *must* never be inlined due to a configuration directive (testing only). */
  public final Set<DexType> neverClassInline;
  /** All types that *must* never be merged due to a configuration directive (testing only). */
  public final Set<DexType> neverMerge;
  /** Set of const-class references. */
  public final Set<DexType> constClassReferences;
  /**
   * All methods and fields whose value *must* never be propagated due to a configuration directive.
   * (testing only).
   */
  private final Set<DexReference> neverPropagateValue;
  /**
   * All items with -identifiernamestring rule. Bound boolean value indicates the rule is explicitly
   * specified by users (<code>true</code>) or not, i.e., implicitly added by R8 (<code>false</code>
   * ).
   */
  public final Object2BooleanMap<DexReference> identifierNameStrings;
  /** A set of types that have been removed by the {@link TreePruner}. */
  final Set<DexType> prunedTypes;
  /** A map from switchmap class types to their corresponding switchmaps. */
  final Map<DexField, Int2ReferenceMap<DexField>> switchMaps;
  /** A map from enum types to their value types and ordinals. */
  final EnumValueInfoMapCollection enumValueInfoMaps;

  final Set<DexType> instantiatedLambdas;

  // TODO(zerny): Clean up the constructors so we have just one.
  AppInfoWithLiveness(
      DirectMappedDexApplication application,
      Set<DexType> missingTypes,
      Set<DexType> liveTypes,
      Set<DexType> instantiatedAppServices,
      SortedSet<DexMethod> targetedMethods,
      Set<DexMethod> failedResolutionTargets,
      SortedSet<DexMethod> bootstrapMethods,
      SortedSet<DexMethod> methodsTargetedByInvokeDynamic,
      SortedSet<DexMethod> virtualMethodsTargetedByInvokeDirect,
      SortedSet<DexMethod> liveMethods,
      FieldAccessInfoCollectionImpl fieldAccessInfoCollection,
      ObjectAllocationInfoCollectionImpl objectAllocationInfoCollection,
      SortedMap<DexMethod, Set<DexEncodedMethod>> virtualInvokes,
      SortedMap<DexMethod, Set<DexEncodedMethod>> interfaceInvokes,
      SortedMap<DexMethod, Set<DexEncodedMethod>> superInvokes,
      SortedMap<DexMethod, Set<DexEncodedMethod>> directInvokes,
      SortedMap<DexMethod, Set<DexEncodedMethod>> staticInvokes,
      Set<DexCallSite> callSites,
      Set<DexReference> pinnedItems,
      Map<DexReference, ProguardMemberRule> mayHaveSideEffects,
      Map<DexReference, ProguardMemberRule> noSideEffects,
      Map<DexReference, ProguardMemberRule> assumedValues,
      Set<DexMethod> alwaysInline,
      Set<DexMethod> forceInline,
      Set<DexMethod> neverInline,
      Set<DexMethod> whyAreYouNotInlining,
      Set<DexMethod> keepConstantArguments,
      Set<DexMethod> keepUnusedArguments,
      Set<DexMethod> reprocess,
      Set<DexMethod> neverReprocess,
      PredicateSet<DexType> alwaysClassInline,
      Set<DexType> neverClassInline,
      Set<DexType> neverMerge,
      Set<DexReference> neverPropagateValue,
      Object2BooleanMap<DexReference> identifierNameStrings,
      Set<DexType> prunedTypes,
      Map<DexField, Int2ReferenceMap<DexField>> switchMaps,
      EnumValueInfoMapCollection enumValueInfoMaps,
      Set<DexType> instantiatedLambdas,
      Set<DexType> constClassReferences) {
    super(application);
    this.missingTypes = missingTypes;
    this.liveTypes = liveTypes;
    this.instantiatedAppServices = instantiatedAppServices;
    this.targetedMethods = targetedMethods;
    this.failedResolutionTargets = failedResolutionTargets;
    this.bootstrapMethods = bootstrapMethods;
    this.methodsTargetedByInvokeDynamic = methodsTargetedByInvokeDynamic;
    this.virtualMethodsTargetedByInvokeDirect = virtualMethodsTargetedByInvokeDirect;
    this.liveMethods = liveMethods;
    this.fieldAccessInfoCollection = fieldAccessInfoCollection;
    this.objectAllocationInfoCollection = objectAllocationInfoCollection;
    this.pinnedItems = pinnedItems;
    this.mayHaveSideEffects = mayHaveSideEffects;
    this.noSideEffects = noSideEffects;
    this.assumedValues = assumedValues;
    this.virtualInvokes = virtualInvokes;
    this.interfaceInvokes = interfaceInvokes;
    this.superInvokes = superInvokes;
    this.directInvokes = directInvokes;
    this.staticInvokes = staticInvokes;
    this.callSites = callSites;
    this.alwaysInline = alwaysInline;
    this.forceInline = forceInline;
    this.neverInline = neverInline;
    this.whyAreYouNotInlining = whyAreYouNotInlining;
    this.keepConstantArguments = keepConstantArguments;
    this.keepUnusedArguments = keepUnusedArguments;
    this.reprocess = reprocess;
    this.neverReprocess = neverReprocess;
    this.alwaysClassInline = alwaysClassInline;
    this.neverClassInline = neverClassInline;
    this.neverMerge = neverMerge;
    this.neverPropagateValue = neverPropagateValue;
    this.identifierNameStrings = identifierNameStrings;
    this.prunedTypes = prunedTypes;
    this.switchMaps = switchMaps;
    this.enumValueInfoMaps = enumValueInfoMaps;
    this.instantiatedLambdas = instantiatedLambdas;
    this.constClassReferences = constClassReferences;
  }

  public AppInfoWithLiveness(
      AppInfoWithSubtyping appInfoWithSubtyping,
      Set<DexType> missingTypes,
      Set<DexType> liveTypes,
      Set<DexType> instantiatedAppServices,
      SortedSet<DexMethod> targetedMethods,
      Set<DexMethod> failedResolutionTargets,
      SortedSet<DexMethod> bootstrapMethods,
      SortedSet<DexMethod> methodsTargetedByInvokeDynamic,
      SortedSet<DexMethod> virtualMethodsTargetedByInvokeDirect,
      SortedSet<DexMethod> liveMethods,
      FieldAccessInfoCollectionImpl fieldAccessInfoCollection,
      ObjectAllocationInfoCollectionImpl objectAllocationInfoCollection,
      SortedMap<DexMethod, Set<DexEncodedMethod>> virtualInvokes,
      SortedMap<DexMethod, Set<DexEncodedMethod>> interfaceInvokes,
      SortedMap<DexMethod, Set<DexEncodedMethod>> superInvokes,
      SortedMap<DexMethod, Set<DexEncodedMethod>> directInvokes,
      SortedMap<DexMethod, Set<DexEncodedMethod>> staticInvokes,
      Set<DexCallSite> callSites,
      Set<DexReference> pinnedItems,
      Map<DexReference, ProguardMemberRule> mayHaveSideEffects,
      Map<DexReference, ProguardMemberRule> noSideEffects,
      Map<DexReference, ProguardMemberRule> assumedValues,
      Set<DexMethod> alwaysInline,
      Set<DexMethod> forceInline,
      Set<DexMethod> neverInline,
      Set<DexMethod> whyAreYouNotInlining,
      Set<DexMethod> keepConstantArguments,
      Set<DexMethod> keepUnusedArguments,
      Set<DexMethod> reprocess,
      Set<DexMethod> neverReprocess,
      PredicateSet<DexType> alwaysClassInline,
      Set<DexType> neverClassInline,
      Set<DexType> neverMerge,
      Set<DexReference> neverPropagateValue,
      Object2BooleanMap<DexReference> identifierNameStrings,
      Set<DexType> prunedTypes,
      Map<DexField, Int2ReferenceMap<DexField>> switchMaps,
      EnumValueInfoMapCollection enumValueInfoMaps,
      Set<DexType> instantiatedLambdas,
      Set<DexType> constClassReferences) {
    super(appInfoWithSubtyping);
    this.missingTypes = missingTypes;
    this.liveTypes = liveTypes;
    this.instantiatedAppServices = instantiatedAppServices;
    this.targetedMethods = targetedMethods;
    this.failedResolutionTargets = failedResolutionTargets;
    this.bootstrapMethods = bootstrapMethods;
    this.methodsTargetedByInvokeDynamic = methodsTargetedByInvokeDynamic;
    this.virtualMethodsTargetedByInvokeDirect = virtualMethodsTargetedByInvokeDirect;
    this.liveMethods = liveMethods;
    this.fieldAccessInfoCollection = fieldAccessInfoCollection;
    this.objectAllocationInfoCollection = objectAllocationInfoCollection;
    this.pinnedItems = pinnedItems;
    this.mayHaveSideEffects = mayHaveSideEffects;
    this.noSideEffects = noSideEffects;
    this.assumedValues = assumedValues;
    this.virtualInvokes = virtualInvokes;
    this.interfaceInvokes = interfaceInvokes;
    this.superInvokes = superInvokes;
    this.directInvokes = directInvokes;
    this.staticInvokes = staticInvokes;
    this.callSites = callSites;
    this.alwaysInline = alwaysInline;
    this.forceInline = forceInline;
    this.neverInline = neverInline;
    this.whyAreYouNotInlining = whyAreYouNotInlining;
    this.keepConstantArguments = keepConstantArguments;
    this.keepUnusedArguments = keepUnusedArguments;
    this.reprocess = reprocess;
    this.neverReprocess = neverReprocess;
    this.alwaysClassInline = alwaysClassInline;
    this.neverClassInline = neverClassInline;
    this.neverMerge = neverMerge;
    this.neverPropagateValue = neverPropagateValue;
    this.identifierNameStrings = identifierNameStrings;
    this.prunedTypes = prunedTypes;
    this.switchMaps = switchMaps;
    this.enumValueInfoMaps = enumValueInfoMaps;
    this.instantiatedLambdas = instantiatedLambdas;
    this.constClassReferences = constClassReferences;
  }

  private AppInfoWithLiveness(AppInfoWithLiveness previous) {
    this(
        previous,
        previous.missingTypes,
        previous.liveTypes,
        previous.instantiatedAppServices,
        previous.targetedMethods,
        previous.failedResolutionTargets,
        previous.bootstrapMethods,
        previous.methodsTargetedByInvokeDynamic,
        previous.virtualMethodsTargetedByInvokeDirect,
        previous.liveMethods,
        previous.fieldAccessInfoCollection,
        previous.objectAllocationInfoCollection,
        previous.virtualInvokes,
        previous.interfaceInvokes,
        previous.superInvokes,
        previous.directInvokes,
        previous.staticInvokes,
        previous.callSites,
        previous.pinnedItems,
        previous.mayHaveSideEffects,
        previous.noSideEffects,
        previous.assumedValues,
        previous.alwaysInline,
        previous.forceInline,
        previous.neverInline,
        previous.whyAreYouNotInlining,
        previous.keepConstantArguments,
        previous.keepUnusedArguments,
        previous.reprocess,
        previous.neverReprocess,
        previous.alwaysClassInline,
        previous.neverClassInline,
        previous.neverMerge,
        previous.neverPropagateValue,
        previous.identifierNameStrings,
        previous.prunedTypes,
        previous.switchMaps,
        previous.enumValueInfoMaps,
        previous.instantiatedLambdas,
        previous.constClassReferences);
    copyMetadataFromPrevious(previous);
  }

  private AppInfoWithLiveness(
      AppInfoWithLiveness previous,
      DirectMappedDexApplication application,
      Collection<DexType> removedClasses,
      Collection<DexReference> additionalPinnedItems) {
    this(
        application,
        previous.missingTypes,
        previous.liveTypes,
        previous.instantiatedAppServices,
        previous.targetedMethods,
        previous.failedResolutionTargets,
        previous.bootstrapMethods,
        previous.methodsTargetedByInvokeDynamic,
        previous.virtualMethodsTargetedByInvokeDirect,
        previous.liveMethods,
        previous.fieldAccessInfoCollection,
        previous.objectAllocationInfoCollection,
        previous.virtualInvokes,
        previous.interfaceInvokes,
        previous.superInvokes,
        previous.directInvokes,
        previous.staticInvokes,
        previous.callSites,
        additionalPinnedItems == null
            ? previous.pinnedItems
            : CollectionUtils.mergeSets(previous.pinnedItems, additionalPinnedItems),
        previous.mayHaveSideEffects,
        previous.noSideEffects,
        previous.assumedValues,
        previous.alwaysInline,
        previous.forceInline,
        previous.neverInline,
        previous.whyAreYouNotInlining,
        previous.keepConstantArguments,
        previous.keepUnusedArguments,
        previous.reprocess,
        previous.neverReprocess,
        previous.alwaysClassInline,
        previous.neverClassInline,
        previous.neverMerge,
        previous.neverPropagateValue,
        previous.identifierNameStrings,
        removedClasses == null
            ? previous.prunedTypes
            : CollectionUtils.mergeSets(previous.prunedTypes, removedClasses),
        previous.switchMaps,
        previous.enumValueInfoMaps,
        previous.instantiatedLambdas,
        previous.constClassReferences);
    copyMetadataFromPrevious(previous);
    assert removedClasses == null || assertNoItemRemoved(previous.pinnedItems, removedClasses);
  }

  public AppInfoWithLiveness(
      AppInfoWithLiveness previous,
      Map<DexField, Int2ReferenceMap<DexField>> switchMaps,
      EnumValueInfoMapCollection enumValueInfoMaps) {
    super(previous);
    this.missingTypes = previous.missingTypes;
    this.liveTypes = previous.liveTypes;
    this.instantiatedAppServices = previous.instantiatedAppServices;
    this.instantiatedLambdas = previous.instantiatedLambdas;
    this.targetedMethods = previous.targetedMethods;
    this.failedResolutionTargets = previous.failedResolutionTargets;
    this.bootstrapMethods = previous.bootstrapMethods;
    this.methodsTargetedByInvokeDynamic = previous.methodsTargetedByInvokeDynamic;
    this.virtualMethodsTargetedByInvokeDirect = previous.virtualMethodsTargetedByInvokeDirect;
    this.liveMethods = previous.liveMethods;
    this.fieldAccessInfoCollection = previous.fieldAccessInfoCollection;
    this.objectAllocationInfoCollection = previous.objectAllocationInfoCollection;
    this.pinnedItems = previous.pinnedItems;
    this.mayHaveSideEffects = previous.mayHaveSideEffects;
    this.noSideEffects = previous.noSideEffects;
    this.assumedValues = previous.assumedValues;
    this.virtualInvokes = previous.virtualInvokes;
    this.interfaceInvokes = previous.interfaceInvokes;
    this.superInvokes = previous.superInvokes;
    this.directInvokes = previous.directInvokes;
    this.staticInvokes = previous.staticInvokes;
    this.callSites = previous.callSites;
    this.alwaysInline = previous.alwaysInline;
    this.forceInline = previous.forceInline;
    this.neverInline = previous.neverInline;
    this.whyAreYouNotInlining = previous.whyAreYouNotInlining;
    this.keepConstantArguments = previous.keepConstantArguments;
    this.keepUnusedArguments = previous.keepUnusedArguments;
    this.reprocess = previous.reprocess;
    this.neverReprocess = previous.neverReprocess;
    this.alwaysClassInline = previous.alwaysClassInline;
    this.neverClassInline = previous.neverClassInline;
    this.neverMerge = previous.neverMerge;
    this.neverPropagateValue = previous.neverPropagateValue;
    this.identifierNameStrings = previous.identifierNameStrings;
    this.prunedTypes = previous.prunedTypes;
    this.switchMaps = switchMaps;
    this.enumValueInfoMaps = enumValueInfoMaps;
    this.constClassReferences = previous.constClassReferences;
    previous.markObsolete();
  }

  private boolean dontAssertDefinitionFor = true;

  public static AppInfoWithLivenessModifier modifier() {
    return new AppInfoWithLivenessModifier();
  }

  @Override
  public void enableDefinitionForAssert() {
    dontAssertDefinitionFor = false;
  }

  @Override
  public void disableDefinitionForAssert() {
    dontAssertDefinitionFor = true;
  }

  @Override
  public DexClass definitionFor(DexType type) {
    DexClass definition = super.definitionFor(type);
    assert dontAssertDefinitionFor
        || definition != null
        || missingTypes.contains(type)
        // TODO(b/149363884): Remove this exception once fixed.
        || type.toDescriptorString().endsWith("$Builder;")
        : "Failed lookup of non-missing type: " + type;
    return definition;
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

  public Collection<DexClass> computeReachableInterfaces() {
    Set<DexClass> interfaces = Sets.newIdentityHashSet();
    Set<DexType> seen = Sets.newIdentityHashSet();
    Deque<DexType> worklist = new ArrayDeque<>();
    for (DexProgramClass clazz : classes()) {
      worklist.add(clazz.type);
    }
    for (DexCallSite callSite : callSites) {
      for (DexEncodedMethod method : lookupLambdaImplementedMethods(callSite)) {
        worklist.add(method.method.holder);
      }
    }
    while (!worklist.isEmpty()) {
      DexType type = worklist.pop();
      if (!seen.add(type)) {
        continue;
      }
      DexClass definition = definitionFor(type);
      if (definition == null) {
        continue;
      }
      if (definition.isInterface()) {
        interfaces.add(definition);
      }
      if (definition.superType != null) {
        worklist.add(definition.superType);
      }
      Collections.addAll(worklist, definition.interfaces.values);
    }
    return interfaces;
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
        if (method.method.name == callSite.methodName && method.accessFlags.isAbstract()) {
          result.add(method);
        }
      }
      Collections.addAll(worklist, clazz.interfaces.values);
    }
    return result;
  }

  // TODO(b/139464956): Reimplement using only reachable types.
  public DexProgramClass getSingleDirectSubtype(DexProgramClass clazz) {
    DexType subtype = super.getSingleSubtype_(clazz.type);
    return subtype == null ? null : asProgramClassOrNull(definitionFor(subtype));
  }

  /**
   * Apply the given function to all classes that directly extend this class.
   *
   * <p>If this class is an interface, then this method will visit all sub-interfaces. This deviates
   * from the dex-file encoding, where subinterfaces "implement" their super interfaces. However, it
   * is consistent with the source language.
   */
  // TODO(b/139464956): Reimplement using only reachable types.
  public void forAllImmediateExtendsSubtypes(DexType type, Consumer<DexType> f) {
    allImmediateExtendsSubtypes(type).forEach(f);
  }

  // TODO(b/139464956): Reimplement using only reachable types.
  public Iterable<DexType> allImmediateExtendsSubtypes(DexType type) {
    return super.allImmediateExtendsSubtypes_(type);
  }

  /**
   * Apply the given function to all classes that directly implement this interface.
   *
   * <p>The implementation does not consider how the hierarchy is encoded in the dex file, where
   * interfaces "implement" their super interfaces. Instead it takes the view of the source
   * language, where interfaces "extend" their superinterface.
   */
  // TODO(b/139464956): Reimplement using only reachable types.
  public void forAllImmediateImplementsSubtypes(DexType type, Consumer<DexType> f) {
    allImmediateImplementsSubtypes(type).forEach(f);
  }

  // TODO(b/139464956): Reimplement using only reachable types.
  public Iterable<DexType> allImmediateImplementsSubtypes(DexType type) {
    return super.allImmediateImplementsSubtypes_(type);
  }

  /**
   * Const-classes is a conservative set of types that may be lock-candidates and cannot be merged.
   * When using synchronized blocks, we cannot ensure that const-class locks will not flow in. This
   * can potentially cause incorrect behavior when merging classes. A conservative choice is to not
   * merge any const-class classes. More info at b/142438687.
   */
  public boolean isLockCandidate(DexType type) {
    return constClassReferences.contains(type);
  }

  public AppInfoWithLiveness withoutStaticFieldsWrites(Set<DexField> noLongerWrittenFields) {
    assert checkIfObsolete();
    if (noLongerWrittenFields.isEmpty()) {
      return this;
    }
    AppInfoWithLiveness result = new AppInfoWithLiveness(this);
    result.fieldAccessInfoCollection.forEach(
        info -> {
          if (noLongerWrittenFields.contains(info.getField())) {
            // Note that this implicitly mutates the current AppInfoWithLiveness, since the `info`
            // instance is shared between the old and the new AppInfoWithLiveness. This should not
            // lead to any problems, though, since the new AppInfo replaces the old AppInfo (we
            // never use an obsolete AppInfo).
            info.clearWrites();
          }
        });
    return result;
  }

  public EnumValueInfoMapCollection getEnumValueInfoMapCollection() {
    assert checkIfObsolete();
    return enumValueInfoMaps;
  }

  public EnumValueInfoMap getEnumValueInfoMap(DexType enumType) {
    assert checkIfObsolete();
    return enumValueInfoMaps.getEnumValueInfoMap(enumType);
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

  /** This method provides immutable access to `objectAllocationInfoCollection`. */
  public ObjectAllocationInfoCollection getObjectAllocationInfoCollection() {
    return objectAllocationInfoCollection;
  }

  ObjectAllocationInfoCollectionImpl getMutableObjectAllocationInfoCollection() {
    return objectAllocationInfoCollection;
  }

  private boolean assertNoItemRemoved(Collection<DexReference> items, Collection<DexType> types) {
    Set<DexType> typeSet = ImmutableSet.copyOf(types);
    for (DexReference item : items) {
      DexType typeToCheck;
      if (item.isDexType()) {
        typeToCheck = item.asDexType();
      } else if (item.isDexMethod()) {
        typeToCheck = item.asDexMethod().holder;
      } else {
        assert item.isDexField();
        typeToCheck = item.asDexField().holder;
      }
      assert !typeSet.contains(typeToCheck);
    }
    return true;
  }

  private boolean isInstantiatedDirectly(DexProgramClass clazz) {
    assert checkIfObsolete();
    DexType type = clazz.type;
    return type.isD8R8SynthesizedClassType()
        || objectAllocationInfoCollection.isInstantiatedDirectly(clazz)
        || (clazz.isAnnotation() && liveTypes.contains(type));
  }

  public boolean isInstantiatedIndirectly(DexProgramClass clazz) {
    assert checkIfObsolete();
    if (hasAnyInstantiatedLambdas(clazz)) {
      return true;
    }
    DexType type = clazz.type;
    synchronized (indirectlyInstantiatedTypes) {
      if (indirectlyInstantiatedTypes.containsKey(type)) {
        return indirectlyInstantiatedTypes.get(type).booleanValue();
      }
      for (DexType directSubtype : allImmediateSubtypes(type)) {
        DexProgramClass directSubClass = asProgramClassOrNull(definitionFor(directSubtype));
        if (directSubClass == null || isInstantiatedDirectlyOrIndirectly(directSubClass)) {
          indirectlyInstantiatedTypes.put(type, Boolean.TRUE);
          return true;
        }
      }
      indirectlyInstantiatedTypes.put(type, Boolean.FALSE);
      return false;
    }
  }

  public boolean isInstantiatedDirectlyOrIndirectly(DexProgramClass clazz) {
    assert checkIfObsolete();
    return isInstantiatedDirectly(clazz) || isInstantiatedIndirectly(clazz);
  }

  public boolean isFieldRead(DexEncodedField encodedField) {
    assert checkIfObsolete();
    DexField field = encodedField.field;
    FieldAccessInfoImpl info = fieldAccessInfoCollection.get(field);
    if (info != null && info.isRead()) {
      return true;
    }
    return isPinned(field)
        // Fields in the class that is synthesized by D8/R8 would be used soon.
        || field.holder.isD8R8SynthesizedClassType()
        // For library classes we don't know whether a field is read.
        || isLibraryOrClasspathField(encodedField);
  }

  public boolean isFieldWritten(DexEncodedField encodedField) {
    assert checkIfObsolete();
    return isFieldWrittenByFieldPutInstruction(encodedField) || isPinned(encodedField.field);
  }

  public boolean isFieldWrittenByFieldPutInstruction(DexEncodedField encodedField) {
    assert checkIfObsolete();
    DexField field = encodedField.field;
    FieldAccessInfoImpl info = fieldAccessInfoCollection.get(field);
    if (info != null && info.isWritten()) {
      // The field is written directly by the program itself.
      return true;
    }
    if (field.holder.isD8R8SynthesizedClassType()) {
      // Fields in the class that is synthesized by D8/R8 would be used soon.
      return true;
    }
    if (isLibraryOrClasspathField(encodedField)) {
      // For library classes we don't know whether a field is rewritten.
      return true;
    }
    return false;
  }

  public boolean isFieldOnlyWrittenInMethod(DexEncodedField field, DexEncodedMethod method) {
    assert checkIfObsolete();
    assert isFieldWritten(field) : "Expected field `" + field.toSourceString() + "` to be written";
    if (!isPinned(field.field)) {
      FieldAccessInfo fieldAccessInfo = fieldAccessInfoCollection.get(field.field);
      return fieldAccessInfo != null
          && fieldAccessInfo.isWritten()
          && !fieldAccessInfo.isWrittenOutside(method);
    }
    return false;
  }

  public boolean isInstanceFieldWrittenOnlyInInstanceInitializers(DexEncodedField field) {
    assert checkIfObsolete();
    assert isFieldWritten(field) : "Expected field `" + field.toSourceString() + "` to be written";
    if (isPinned(field.field)) {
      return false;
    }
    FieldAccessInfo fieldAccessInfo = fieldAccessInfoCollection.get(field.field);
    if (fieldAccessInfo == null || !fieldAccessInfo.isWritten()) {
      return false;
    }
    DexType holder = field.field.holder;
    return fieldAccessInfo.isWrittenOnlyInMethodSatisfying(
        method -> method.isInstanceInitializer() && method.method.holder == holder);
  }

  public boolean isStaticFieldWrittenOnlyInEnclosingStaticInitializer(DexEncodedField field) {
    assert checkIfObsolete();
    assert isFieldWritten(field) : "Expected field `" + field.toSourceString() + "` to be written";
    DexEncodedMethod staticInitializer =
        definitionFor(field.field.holder).asProgramClass().getClassInitializer();
    return staticInitializer != null && isFieldOnlyWrittenInMethod(field, staticInitializer);
  }

  public boolean mayPropagateValueFor(DexReference reference) {
    assert checkIfObsolete();
    return options().enableValuePropagation
        && !isPinned(reference)
        && !neverPropagateValue.contains(reference);
  }

  private boolean isLibraryOrClasspathField(DexEncodedField field) {
    DexClass holder = definitionFor(field.field.holder);
    return holder == null || holder.isLibraryClass() || holder.isClasspathClass();
  }

  private static <T extends PresortedComparable<T>> ImmutableSortedSet<T> rewriteItems(
      Set<T> original, Function<T, T> rewrite) {
    Builder<T> builder = new Builder<>(PresortedComparable::slowCompare);
    for (T item : original) {
      builder.add(rewrite.apply(item));
    }
    return builder.build();
  }

  private static <T extends PresortedComparable<T>, S>
      SortedMap<T, Set<S>> rewriteKeysConservativelyWhileMergingValues(
          Map<T, Set<S>> original, Function<T, Set<T>> rewrite) {
    SortedMap<T, Set<S>> result = new TreeMap<>(PresortedComparable::slowCompare);
    for (T item : original.keySet()) {
      Set<T> rewrittenKeys = rewrite.apply(item);
      for (T rewrittenKey : rewrittenKeys) {
        result
            .computeIfAbsent(rewrittenKey, k -> Sets.newIdentityHashSet())
            .addAll(original.get(item));
      }
    }
    return Collections.unmodifiableSortedMap(result);
  }

  @Override
  public boolean hasAnyInstantiatedLambdas(DexProgramClass clazz) {
    assert checkIfObsolete();
    return instantiatedLambdas.contains(clazz.type);
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

  public boolean isPinned(DexReference reference) {
    assert checkIfObsolete();
    return pinnedItems.contains(reference);
  }

  public boolean hasPinnedInstanceInitializer(DexType type) {
    assert type.isClassType();
    DexProgramClass clazz = asProgramClassOrNull(definitionFor(type));
    if (clazz != null) {
      for (DexEncodedMethod method : clazz.directMethods()) {
        if (method.isInstanceInitializer() && isPinned(method.method)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean canVirtualMethodBeImplementedInExtraSubclass(
      DexProgramClass clazz, DexMethod method) {
    // For functional interfaces that are instantiated by lambdas, we may not have synthesized all
    // the lambda classes yet, and therefore the set of subtypes for the holder may still be
    // incomplete.
    if (hasAnyInstantiatedLambdas(clazz)) {
      return true;
    }
    // If `clazz` is kept and `method` is a library method or a library method override, then it is
    // possible to create a class that inherits from `clazz` and overrides the library method.
    // Similarly, if `clazz` is kept and `method` is kept directly on `clazz` or indirectly on one
    // of its supertypes, then it is possible to create a class that inherits from `clazz` and
    // overrides the kept method.
    if (isPinned(clazz.type)) {
      ResolutionResult resolutionResult = resolveMethod(clazz, method);
      if (resolutionResult.isSingleResolution()) {
        DexEncodedMethod resolutionTarget = resolutionResult.getSingleTarget();
        return !resolutionTarget.isProgramMethod(this)
            || resolutionTarget.isLibraryMethodOverride().isPossiblyTrue()
            || isVirtualMethodPinnedDirectlyOrInAncestor(clazz, method);
      }
    }
    return false;
  }

  private boolean isVirtualMethodPinnedDirectlyOrInAncestor(
      DexProgramClass currentClass, DexMethod method) {
    // Look in all ancestor types, including `currentClass` itself.
    Set<DexProgramClass> visited = SetUtils.newIdentityHashSet(currentClass);
    Deque<DexProgramClass> worklist = new ArrayDeque<>(visited);
    while (!worklist.isEmpty()) {
      DexClass clazz = worklist.removeFirst();
      assert visited.contains(clazz);
      DexEncodedMethod methodInClass = clazz.lookupVirtualMethod(method);
      if (methodInClass != null && isPinned(methodInClass.method)) {
        return true;
      }
      for (DexType superType : clazz.allImmediateSupertypes()) {
        DexProgramClass superClass = asProgramClassOrNull(definitionFor(superType));
        if (superClass != null && visited.add(superClass)) {
          worklist.addLast(superClass);
        }
      }
    }
    return false;
  }

  public Set<DexReference> getPinnedItems() {
    assert checkIfObsolete();
    return pinnedItems;
  }

  /**
   * Returns a copy of this AppInfoWithLiveness where the set of classes is pruned using the given
   * DexApplication object.
   */
  public AppInfoWithLiveness prunedCopyFrom(
      DirectMappedDexApplication application,
      Collection<DexType> removedClasses,
      Collection<DexReference> additionalPinnedItems) {
    assert checkIfObsolete();
    return new AppInfoWithLiveness(this, application, removedClasses, additionalPinnedItems);
  }

  public AppInfoWithLiveness rewrittenWithLens(
      DirectMappedDexApplication application, NestedGraphLense lens) {
    assert checkIfObsolete();
    // The application has already been rewritten with all of lens' parent lenses. Therefore, we
    // temporarily replace lens' parent lens with an identity lens to avoid the overhead of
    // traversing the entire lens chain upon each lookup during the rewriting.
    return lens.withAlternativeParentLens(
        GraphLense.getIdentityLense(), () -> createRewrittenAppInfoWithLiveness(application, lens));
  }

  private AppInfoWithLiveness createRewrittenAppInfoWithLiveness(
      DirectMappedDexApplication application, NestedGraphLense lens) {
    // Switchmap classes should never be affected by renaming.
    assert lens.assertDefinitionsNotModified(
        switchMaps.keySet().stream()
            .map(this::definitionFor)
            .filter(Objects::nonNull)
            .collect(Collectors.toList()));

    assert lens.assertDefinitionsNotModified(
        neverMerge.stream()
            .map(this::definitionFor)
            .filter(Objects::nonNull)
            .collect(Collectors.toList()));

    assert lens.assertDefinitionsNotModified(
        alwaysInline.stream()
            .map(this::definitionFor)
            .filter(Objects::nonNull)
            .collect(Collectors.toList()));

    return new AppInfoWithLiveness(
        application,
        missingTypes,
        rewriteItems(liveTypes, lens::lookupType),
        rewriteItems(instantiatedAppServices, lens::lookupType),
        lens.rewriteMethodsConservatively(targetedMethods),
        lens.rewriteMethodsConservatively(failedResolutionTargets),
        lens.rewriteMethodsConservatively(bootstrapMethods),
        lens.rewriteMethodsConservatively(methodsTargetedByInvokeDynamic),
        lens.rewriteMethodsConservatively(virtualMethodsTargetedByInvokeDirect),
        lens.rewriteMethodsConservatively(liveMethods),
        fieldAccessInfoCollection.rewrittenWithLens(application, lens),
        objectAllocationInfoCollection.rewrittenWithLens(application, lens),
        rewriteKeysConservativelyWhileMergingValues(
            virtualInvokes, lens::lookupMethodInAllContexts),
        rewriteKeysConservativelyWhileMergingValues(
            interfaceInvokes, lens::lookupMethodInAllContexts),
        rewriteKeysConservativelyWhileMergingValues(superInvokes, lens::lookupMethodInAllContexts),
        rewriteKeysConservativelyWhileMergingValues(directInvokes, lens::lookupMethodInAllContexts),
        rewriteKeysConservativelyWhileMergingValues(staticInvokes, lens::lookupMethodInAllContexts),
        // TODO(sgjesse): Rewrite call sites as well? Right now they are only used by minification
        //   after second tree shaking.
        callSites,
        lens.rewriteReferencesConservatively(pinnedItems),
        rewriteReferenceKeys(mayHaveSideEffects, lens::lookupReference),
        rewriteReferenceKeys(noSideEffects, lens::lookupReference),
        rewriteReferenceKeys(assumedValues, lens::lookupReference),
        lens.rewriteMethodsWithRenamedSignature(alwaysInline),
        lens.rewriteMethodsWithRenamedSignature(forceInline),
        lens.rewriteMethodsWithRenamedSignature(neverInline),
        lens.rewriteMethodsWithRenamedSignature(whyAreYouNotInlining),
        lens.rewriteMethodsWithRenamedSignature(keepConstantArguments),
        lens.rewriteMethodsWithRenamedSignature(keepUnusedArguments),
        lens.rewriteMethodsWithRenamedSignature(reprocess),
        lens.rewriteMethodsWithRenamedSignature(neverReprocess),
        alwaysClassInline.rewriteItems(lens::lookupType),
        rewriteItems(neverClassInline, lens::lookupType),
        rewriteItems(neverMerge, lens::lookupType),
        lens.rewriteReferencesConservatively(neverPropagateValue),
        lens.rewriteReferencesConservatively(identifierNameStrings),
        // Don't rewrite pruned types - the removed types are identified by their original name.
        prunedTypes,
        rewriteReferenceKeys(switchMaps, lens::lookupField),
        enumValueInfoMaps.rewrittenWithLens(lens),
        rewriteItems(instantiatedLambdas, lens::lookupType),
        constClassReferences);
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
      Type type, DexMethod target, DexType invocationContext) {
    assert checkIfObsolete();
    DexType holder = target.holder;
    if (!holder.isClassType()) {
      return null;
    }
    switch (type) {
      case VIRTUAL:
        return lookupSingleVirtualTarget(target, invocationContext, false);
      case INTERFACE:
        return lookupSingleVirtualTarget(target, invocationContext, true);
      case DIRECT:
        return lookupDirectTarget(target, invocationContext);
      case STATIC:
        return lookupStaticTarget(target, invocationContext);
      case SUPER:
        return lookupSuperTarget(target, invocationContext);
      default:
        return null;
    }
  }

  private DexEncodedMethod validateSingleVirtualTarget(
      DexEncodedMethod singleTarget, DexEncodedMethod resolutionResult) {
    assert resolutionResult.isVirtualMethod();

    if (singleTarget == null || singleTarget == DexEncodedMethod.SENTINEL) {
      return null;
    }

    // Art978_virtual_interfaceTest correctly expects an IncompatibleClassChangeError exception
    // at runtime.
    if (isInvalidSingleVirtualTarget(singleTarget, resolutionResult)) {
      return null;
    }

    return singleTarget;
  }

  private boolean isInvalidSingleVirtualTarget(
      DexEncodedMethod singleTarget, DexEncodedMethod resolutionResult) {
    assert resolutionResult.isVirtualMethod();
    // Art978_virtual_interfaceTest correctly expects an IncompatibleClassChangeError exception
    // at runtime.
    return !singleTarget.accessFlags.isAtLeastAsVisibleAs(resolutionResult.accessFlags);
  }

  /** For mapping invoke virtual instruction to single target method. */
  public DexEncodedMethod lookupSingleVirtualTarget(
      DexMethod method, DexType invocationContext, boolean isInterface) {
    assert checkIfObsolete();
    return lookupSingleVirtualTarget(method, invocationContext, isInterface, method.holder, null);
  }

  public DexEncodedMethod lookupSingleVirtualTarget(
      DexMethod method,
      DexType invocationContext,
      boolean isInterface,
      DexType refinedReceiverType,
      ClassTypeLatticeElement receiverLowerBoundType) {
    assert checkIfObsolete();
    assert refinedReceiverType != null;

    DexProgramClass invocationClass = asProgramClassOrNull(definitionFor(invocationContext));
    assert invocationClass != null;

    if (!refinedReceiverType.isClassType()) {
      // The refined receiver is not of class type and we will not be able to find a single target
      // (it is either primitive or array).
      return null;
    }
    DexClass refinedReceiverClass = definitionFor(refinedReceiverType);
    if (refinedReceiverClass == null) {
      // The refined receiver is not defined in the program and we cannot determine the target.
      return null;
    }
    SingleResolutionResult resolution =
        resolveMethod(method.holder, method, isInterface).asSingleResolution();
    if (resolution == null
        || !resolution.isAccessibleForVirtualDispatchFrom(invocationClass, this)) {
      return null;
    }
    // If the lower-bound on the receiver type is the same as the upper-bound, then we have exact
    // runtime type information. In this case, the invoke will dispatch to the resolution result
    // from the runtime type of the receiver.
    if (receiverLowerBoundType != null
        && receiverLowerBoundType.getClassType() == refinedReceiverType) {
      if (refinedReceiverClass.isProgramClass()) {
        DexClassAndMethod clazzAndMethod =
            resolution.lookupVirtualDispatchTarget(refinedReceiverClass.asProgramClass(), this);
        if (clazzAndMethod == null || isPinned(clazzAndMethod.getMethod().method)) {
          // TODO(b/150640456): We should maybe only consider program methods.
          return null;
        }
        return clazzAndMethod.getMethod();
      } else {
        // TODO(b/150640456): We should maybe only consider program methods.
        // If we resolved to a method on the refined receiver in the library, then we report the
        // method as a single target as well. This is a bit iffy since the library could change
        // implementation, but we use this for library modelling.
        DexEncodedMethod targetOnReceiver = refinedReceiverClass.lookupVirtualMethod(method);
        if (targetOnReceiver != null
            && isOverriding(resolution.getResolvedMethod(), targetOnReceiver)) {
          return targetOnReceiver;
        }
        return null;
      }
    }
    if (refinedReceiverClass.isNotProgramClass()) {
      // The refined receiver is not defined in the program and we cannot determine the target.
      return null;
    }
    DexClass resolvedHolder = resolution.getResolvedHolder();
    // TODO(b/148769279): Disable lookup single target on lambda's for now.
    if (resolvedHolder.isInterface()
        && resolvedHolder.isProgramClass()
        && hasAnyInstantiatedLambdas(resolvedHolder.asProgramClass())) {
      return null;
    }

    if (method.isSingleVirtualMethodCached(refinedReceiverType)) {
      return method.getSingleVirtualMethodCache(refinedReceiverType);
    }

    DexProgramClass refinedLowerBound = null;
    if (receiverLowerBoundType != null) {
      assert receiverLowerBoundType.isClassType();
      DexClass refinedLowerBoundClass = definitionFor(receiverLowerBoundType.getClassType());
      if (refinedLowerBoundClass != null) {
        refinedLowerBound = refinedLowerBoundClass.asProgramClass();
      }
    }

    LookupResultSuccess lookupResult =
        resolution
            .lookupVirtualDispatchTargets(
                invocationClass, this, refinedReceiverClass.asProgramClass(), refinedLowerBound)
            .asLookupResultSuccess();

    if (lookupResult == null || lookupResult.isIncomplete()) {
      return null;
    }

    LookupTarget singleTarget = lookupResult.getSingleLookupTarget();
    DexEncodedMethod singleMethodTarget = null;
    if (singleTarget.isMethodTarget()) {
      singleMethodTarget = singleTarget.asMethodTarget().getMethod();
    }
    method.setSingleVirtualMethodCache(refinedReceiverType, singleMethodTarget);
    return singleMethodTarget;
  }

  public AppInfoWithLiveness withSwitchMaps(Map<DexField, Int2ReferenceMap<DexField>> switchMaps) {
    assert checkIfObsolete();
    assert this.switchMaps.isEmpty();
    return new AppInfoWithLiveness(this, switchMaps, enumValueInfoMaps);
  }

  public AppInfoWithLiveness withEnumValueInfoMaps(EnumValueInfoMapCollection enumValueInfoMaps) {
    assert checkIfObsolete();
    assert this.enumValueInfoMaps.isEmpty();
    return new AppInfoWithLiveness(this, switchMaps, enumValueInfoMaps);
  }

  public void forEachLiveProgramClass(Consumer<DexProgramClass> fn) {
    for (DexType type : liveTypes) {
      fn.accept(definitionFor(type).asProgramClass());
    }
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
        fn, ListUtils.map(liveTypes, t -> definitionFor(t).asProgramClass()), callSites, this);
  }

  // Split in a static method so it can be used during construction.
  static void forEachTypeInHierarchyOfLiveProgramClasses(
      Consumer<DexClass> fn,
      Collection<DexProgramClass> liveProgramClasses,
      Set<DexCallSite> callSites,
      AppInfoWithClassHierarchy appInfo) {
    Set<DexType> seen = Sets.newIdentityHashSet();
    Deque<DexType> worklist = new ArrayDeque<>();
    liveProgramClasses.forEach(c -> seen.add(c.type));
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
    for (DexCallSite callSite : callSites) {
      List<DexType> interfaces = LambdaDescriptor.getInterfaces(callSite, appInfo);
      if (interfaces != null) {
        for (DexType iface : interfaces) {
          if (seen.add(iface)) {
            worklist.add(iface);
          }
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
    WorkList<DexType> workList = WorkList.newIdentityWorkList();
    workList.addIfNotSeen(type);
    while (workList.hasNext()) {
      DexType subType = workList.next();
      DexProgramClass clazz = definitionForProgramType(subType);
      workList.addIfNotSeen(allImmediateSubtypes(subType));
      if (clazz == null) {
        continue;
      }
      if (isInstantiatedDirectly(clazz)
          || isPinned(clazz.type)
          || hasAnyInstantiatedLambdas(clazz)) {
        subTypeConsumer.accept(clazz);
      }
    }
  }

  public boolean isPinnedNotProgramOrLibraryOverride(DexReference reference) {
    if (isPinned(reference)) {
      return true;
    }
    if (reference.isDexMethod()) {
      DexEncodedMethod method = definitionFor(reference.asDexMethod());
      return method == null
          || !method.isProgramMethod(this)
          || method.isLibraryMethodOverride().isPossiblyTrue();
    } else {
      assert reference.isDexType();
      DexClass clazz = definitionFor(reference.asDexType());
      return clazz == null
          || clazz.isNotProgramClass()
          || hasAnyInstantiatedLambdas(clazz.asProgramClass());
    }
  }
}
