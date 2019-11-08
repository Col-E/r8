// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.graph.GraphLense.rewriteReferenceKeys;
import static com.google.common.base.Predicates.not;

import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.FieldAccessInfoCollectionImpl;
import com.android.tools.r8.graph.FieldAccessInfoImpl;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.PresortedComparable;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.graph.ResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.analysis.type.ClassTypeLatticeElement;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.optimize.NestUtils;
import com.android.tools.r8.utils.CollectionUtils;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Encapsulates liveness and reachability information for an application. */
public class AppInfoWithLiveness extends AppInfoWithSubtyping {

  /**
   * Set of types that are mentioned in the program. We at least need an empty abstract classitem
   * for these.
   */
  private final Set<DexType> liveTypes;
  /** Set of annotation types that are instantiated. */
  private final Set<DexType> instantiatedAnnotationTypes;
  /**
   * Set of service types (from META-INF/services/) that may have been instantiated reflectively via
   * ServiceLoader.load() or ServiceLoader.loadInstalled().
   */
  public final Set<DexType> instantiatedAppServices;
  /** Set of types that are actually instantiated. These cannot be abstract. */
  final Set<DexType> instantiatedTypes;
  /** Cache for {@link #isInstantiatedDirectlyOrIndirectly(DexType)}. */
  private final IdentityHashMap<DexType, Boolean> indirectlyInstantiatedTypes =
      new IdentityHashMap<>();
  /**
   * Set of methods that are the immediate target of an invoke. They might not actually be live but
   * are required so that invokes can find the method. If such a method is not live (i.e. not
   * contained in {@link #liveMethods}, it may be marked as abstract and its implementation may be
   * removed.
   */
  final SortedSet<DexMethod> targetedMethods;
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
  /**
   * Set of all instance fields that are only written inside the <init>() methods of their enclosing
   * class.
   */
  private Set<DexField> instanceFieldsWrittenOnlyInEnclosingInstanceInitializers;
  /**
   * Set of all static fields that are only written inside the <clinit>() method of their enclosing
   * class.
   */
  private Set<DexField> staticFieldsWrittenOnlyInEnclosingStaticInitializer;
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
  /**
   * Set of method signatures used in invoke-super instructions that either cannot be resolved or
   * resolve to a private method (leading to an IllegalAccessError).
   */
  public final SortedSet<DexMethod> brokenSuperInvokes;
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
  final Map<DexType, Map<DexField, EnumValueInfo>> enumValueInfoMaps;

  public static final class EnumValueInfo {
    /** The anonymous subtype of this specific value or the enum type. */
    public final DexType type;
    public final int ordinal;

    public EnumValueInfo(DexType type, int ordinal) {
      this.type = type;
      this.ordinal = ordinal;
    }
  }

  final Set<DexType> instantiatedLambdas;

  // TODO(zerny): Clean up the constructors so we have just one.
  private AppInfoWithLiveness(
      DexApplication application,
      Set<DexType> liveTypes,
      Set<DexType> instantiatedAnnotationTypes,
      Set<DexType> instantiatedAppServices,
      Set<DexType> instantiatedTypes,
      SortedSet<DexMethod> targetedMethods,
      SortedSet<DexMethod> bootstrapMethods,
      SortedSet<DexMethod> methodsTargetedByInvokeDynamic,
      SortedSet<DexMethod> virtualMethodsTargetedByInvokeDirect,
      SortedSet<DexMethod> liveMethods,
      FieldAccessInfoCollectionImpl fieldAccessInfoCollection,
      Set<DexField> instanceFieldsWrittenOnlyInEnclosingInstanceInitializers,
      Set<DexField> staticFieldsWrittenOnlyInEnclosingStaticInitializer,
      SortedMap<DexMethod, Set<DexEncodedMethod>> virtualInvokes,
      SortedMap<DexMethod, Set<DexEncodedMethod>> interfaceInvokes,
      SortedMap<DexMethod, Set<DexEncodedMethod>> superInvokes,
      SortedMap<DexMethod, Set<DexEncodedMethod>> directInvokes,
      SortedMap<DexMethod, Set<DexEncodedMethod>> staticInvokes,
      Set<DexCallSite> callSites,
      SortedSet<DexMethod> brokenSuperInvokes,
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
      Set<DexType> neverClassInline,
      Set<DexType> neverMerge,
      Set<DexReference> neverPropagateValue,
      Object2BooleanMap<DexReference> identifierNameStrings,
      Set<DexType> prunedTypes,
      Map<DexField, Int2ReferenceMap<DexField>> switchMaps,
      Map<DexType, Map<DexField, EnumValueInfo>> enumValueInfoMaps,
      Set<DexType> instantiatedLambdas,
      Set<DexType> constClassReferences) {
    super(application);
    this.liveTypes = liveTypes;
    this.instantiatedAnnotationTypes = instantiatedAnnotationTypes;
    this.instantiatedAppServices = instantiatedAppServices;
    this.instantiatedTypes = instantiatedTypes;
    this.targetedMethods = targetedMethods;
    this.bootstrapMethods = bootstrapMethods;
    this.methodsTargetedByInvokeDynamic = methodsTargetedByInvokeDynamic;
    this.virtualMethodsTargetedByInvokeDirect = virtualMethodsTargetedByInvokeDirect;
    this.liveMethods = liveMethods;
    this.fieldAccessInfoCollection = fieldAccessInfoCollection;
    this.instanceFieldsWrittenOnlyInEnclosingInstanceInitializers =
        instanceFieldsWrittenOnlyInEnclosingInstanceInitializers;
    this.staticFieldsWrittenOnlyInEnclosingStaticInitializer =
        staticFieldsWrittenOnlyInEnclosingStaticInitializer;
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
    this.brokenSuperInvokes = brokenSuperInvokes;
    this.alwaysInline = alwaysInline;
    this.forceInline = forceInline;
    this.neverInline = neverInline;
    this.whyAreYouNotInlining = whyAreYouNotInlining;
    this.keepConstantArguments = keepConstantArguments;
    this.keepUnusedArguments = keepUnusedArguments;
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
      Set<DexType> liveTypes,
      Set<DexType> instantiatedAnnotationTypes,
      Set<DexType> instantiatedAppServices,
      Set<DexType> instantiatedTypes,
      SortedSet<DexMethod> targetedMethods,
      SortedSet<DexMethod> bootstrapMethods,
      SortedSet<DexMethod> methodsTargetedByInvokeDynamic,
      SortedSet<DexMethod> virtualMethodsTargetedByInvokeDirect,
      SortedSet<DexMethod> liveMethods,
      FieldAccessInfoCollectionImpl fieldAccessInfoCollection,
      Set<DexField> instanceFieldsWrittenOnlyInEnclosingInstanceInitializers,
      Set<DexField> staticFieldsWrittenOnlyInEnclosingStaticInitializer,
      SortedMap<DexMethod, Set<DexEncodedMethod>> virtualInvokes,
      SortedMap<DexMethod, Set<DexEncodedMethod>> interfaceInvokes,
      SortedMap<DexMethod, Set<DexEncodedMethod>> superInvokes,
      SortedMap<DexMethod, Set<DexEncodedMethod>> directInvokes,
      SortedMap<DexMethod, Set<DexEncodedMethod>> staticInvokes,
      Set<DexCallSite> callSites,
      SortedSet<DexMethod> brokenSuperInvokes,
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
      Set<DexType> neverClassInline,
      Set<DexType> neverMerge,
      Set<DexReference> neverPropagateValue,
      Object2BooleanMap<DexReference> identifierNameStrings,
      Set<DexType> prunedTypes,
      Map<DexField, Int2ReferenceMap<DexField>> switchMaps,
      Map<DexType, Map<DexField, EnumValueInfo>> enumValueInfoMaps,
      Set<DexType> instantiatedLambdas,
      Set<DexType> constClassReferences) {
    super(appInfoWithSubtyping);
    this.liveTypes = liveTypes;
    this.instantiatedAnnotationTypes = instantiatedAnnotationTypes;
    this.instantiatedAppServices = instantiatedAppServices;
    this.instantiatedTypes = instantiatedTypes;
    this.targetedMethods = targetedMethods;
    this.bootstrapMethods = bootstrapMethods;
    this.methodsTargetedByInvokeDynamic = methodsTargetedByInvokeDynamic;
    this.virtualMethodsTargetedByInvokeDirect = virtualMethodsTargetedByInvokeDirect;
    this.liveMethods = liveMethods;
    this.fieldAccessInfoCollection = fieldAccessInfoCollection;
    this.instanceFieldsWrittenOnlyInEnclosingInstanceInitializers =
        instanceFieldsWrittenOnlyInEnclosingInstanceInitializers;
    this.staticFieldsWrittenOnlyInEnclosingStaticInitializer =
        staticFieldsWrittenOnlyInEnclosingStaticInitializer;
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
    this.brokenSuperInvokes = brokenSuperInvokes;
    this.alwaysInline = alwaysInline;
    this.forceInline = forceInline;
    this.neverInline = neverInline;
    this.whyAreYouNotInlining = whyAreYouNotInlining;
    this.keepConstantArguments = keepConstantArguments;
    this.keepUnusedArguments = keepUnusedArguments;
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
        previous.liveTypes,
        previous.instantiatedAnnotationTypes,
        previous.instantiatedAppServices,
        previous.instantiatedTypes,
        previous.targetedMethods,
        previous.bootstrapMethods,
        previous.methodsTargetedByInvokeDynamic,
        previous.virtualMethodsTargetedByInvokeDirect,
        previous.liveMethods,
        previous.fieldAccessInfoCollection,
        previous.instanceFieldsWrittenOnlyInEnclosingInstanceInitializers,
        previous.staticFieldsWrittenOnlyInEnclosingStaticInitializer,
        previous.virtualInvokes,
        previous.interfaceInvokes,
        previous.superInvokes,
        previous.directInvokes,
        previous.staticInvokes,
        previous.callSites,
        previous.brokenSuperInvokes,
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
      DexApplication application,
      Collection<DexType> removedClasses,
      Collection<DexReference> additionalPinnedItems) {
    this(
        application,
        previous.liveTypes,
        previous.instantiatedAnnotationTypes,
        previous.instantiatedAppServices,
        previous.instantiatedTypes,
        previous.targetedMethods,
        previous.bootstrapMethods,
        previous.methodsTargetedByInvokeDynamic,
        previous.virtualMethodsTargetedByInvokeDirect,
        previous.liveMethods,
        previous.fieldAccessInfoCollection,
        previous.instanceFieldsWrittenOnlyInEnclosingInstanceInitializers,
        previous.staticFieldsWrittenOnlyInEnclosingStaticInitializer,
        previous.virtualInvokes,
        previous.interfaceInvokes,
        previous.superInvokes,
        previous.directInvokes,
        previous.staticInvokes,
        previous.callSites,
        previous.brokenSuperInvokes,
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

  private AppInfoWithLiveness(
      AppInfoWithLiveness previous, DirectMappedDexApplication application, GraphLense lense) {
    super(application);
    this.liveTypes = rewriteItems(previous.liveTypes, lense::lookupType);
    this.instantiatedAnnotationTypes =
        rewriteItems(previous.instantiatedAnnotationTypes, lense::lookupType);
    this.instantiatedAppServices =
        rewriteItems(previous.instantiatedAppServices, lense::lookupType);
    this.instantiatedTypes = rewriteItems(previous.instantiatedTypes, lense::lookupType);
    this.instantiatedLambdas = rewriteItems(previous.instantiatedLambdas, lense::lookupType);
    this.targetedMethods = lense.rewriteMethodsConservatively(previous.targetedMethods);
    this.bootstrapMethods = lense.rewriteMethodsConservatively(previous.bootstrapMethods);
    this.methodsTargetedByInvokeDynamic =
        lense.rewriteMethodsConservatively(previous.methodsTargetedByInvokeDynamic);
    this.virtualMethodsTargetedByInvokeDirect =
        lense.rewriteMethodsConservatively(previous.virtualMethodsTargetedByInvokeDirect);
    this.liveMethods = lense.rewriteMethodsConservatively(previous.liveMethods);
    this.fieldAccessInfoCollection =
        previous.fieldAccessInfoCollection.rewrittenWithLens(application, lense);
    this.instanceFieldsWrittenOnlyInEnclosingInstanceInitializers =
        rewriteItems(
            previous.instanceFieldsWrittenOnlyInEnclosingInstanceInitializers, lense::lookupField);
    this.staticFieldsWrittenOnlyInEnclosingStaticInitializer =
        rewriteItems(
            previous.staticFieldsWrittenOnlyInEnclosingStaticInitializer, lense::lookupField);
    this.pinnedItems = lense.rewriteReferencesConservatively(previous.pinnedItems);
    this.virtualInvokes =
        rewriteKeysConservativelyWhileMergingValues(
            previous.virtualInvokes, lense::lookupMethodInAllContexts);
    this.interfaceInvokes =
        rewriteKeysConservativelyWhileMergingValues(
            previous.interfaceInvokes, lense::lookupMethodInAllContexts);
    this.superInvokes =
        rewriteKeysConservativelyWhileMergingValues(
            previous.superInvokes, lense::lookupMethodInAllContexts);
    this.directInvokes =
        rewriteKeysConservativelyWhileMergingValues(
            previous.directInvokes, lense::lookupMethodInAllContexts);
    this.staticInvokes =
        rewriteKeysConservativelyWhileMergingValues(
            previous.staticInvokes, lense::lookupMethodInAllContexts);
    // TODO(sgjesse): Rewrite call sites as well? Right now they are only used by minification
    // after second tree shaking.
    this.callSites = previous.callSites;
    this.brokenSuperInvokes = lense.rewriteMethodsConservatively(previous.brokenSuperInvokes);
    // Don't rewrite pruned types - the removed types are identified by their original name.
    this.prunedTypes = previous.prunedTypes;
    this.mayHaveSideEffects =
        rewriteReferenceKeys(previous.mayHaveSideEffects, lense::lookupReference);
    this.noSideEffects = rewriteReferenceKeys(previous.noSideEffects, lense::lookupReference);
    this.assumedValues = rewriteReferenceKeys(previous.assumedValues, lense::lookupReference);
    assert lense.assertDefinitionsNotModified(
        previous.alwaysInline.stream()
            .map(this::definitionFor)
            .filter(Objects::nonNull)
            .collect(Collectors.toList()));
    this.alwaysInline = lense.rewriteMethodsWithRenamedSignature(previous.alwaysInline);
    this.forceInline = lense.rewriteMethodsWithRenamedSignature(previous.forceInline);
    this.neverInline = lense.rewriteMethodsWithRenamedSignature(previous.neverInline);
    this.whyAreYouNotInlining =
        lense.rewriteMethodsWithRenamedSignature(previous.whyAreYouNotInlining);
    this.keepConstantArguments =
        lense.rewriteMethodsWithRenamedSignature(previous.keepConstantArguments);
    this.keepUnusedArguments =
        lense.rewriteMethodsWithRenamedSignature(previous.keepUnusedArguments);
    assert lense.assertDefinitionsNotModified(
        previous.neverMerge.stream()
            .map(this::definitionFor)
            .filter(Objects::nonNull)
            .collect(Collectors.toList()));
    this.neverClassInline = rewriteItems(previous.neverClassInline, lense::lookupType);
    this.neverMerge = rewriteItems(previous.neverMerge, lense::lookupType);
    this.neverPropagateValue = lense.rewriteReferencesConservatively(previous.neverPropagateValue);
    this.identifierNameStrings =
        lense.rewriteReferencesConservatively(previous.identifierNameStrings);
    // Switchmap classes should never be affected by renaming.
    assert lense.assertDefinitionsNotModified(
        previous.switchMaps.keySet().stream()
            .map(this::definitionFor)
            .filter(Objects::nonNull)
            .collect(Collectors.toList()));
    this.switchMaps = rewriteReferenceKeys(previous.switchMaps, lense::lookupField);
    this.enumValueInfoMaps = rewriteReferenceKeys(previous.enumValueInfoMaps, lense::lookupType);
    this.constClassReferences = previous.constClassReferences;
  }

  public AppInfoWithLiveness(
      AppInfoWithLiveness previous,
      Map<DexField, Int2ReferenceMap<DexField>> switchMaps,
      Map<DexType, Map<DexField, EnumValueInfo>> enumValueInfoMaps) {
    super(previous);
    this.liveTypes = previous.liveTypes;
    this.instantiatedAnnotationTypes = previous.instantiatedAnnotationTypes;
    this.instantiatedAppServices = previous.instantiatedAppServices;
    this.instantiatedTypes = previous.instantiatedTypes;
    this.instantiatedLambdas = previous.instantiatedLambdas;
    this.targetedMethods = previous.targetedMethods;
    this.bootstrapMethods = previous.bootstrapMethods;
    this.methodsTargetedByInvokeDynamic = previous.methodsTargetedByInvokeDynamic;
    this.virtualMethodsTargetedByInvokeDirect = previous.virtualMethodsTargetedByInvokeDirect;
    this.liveMethods = previous.liveMethods;
    this.fieldAccessInfoCollection = previous.fieldAccessInfoCollection;
    this.instanceFieldsWrittenOnlyInEnclosingInstanceInitializers =
        previous.instanceFieldsWrittenOnlyInEnclosingInstanceInitializers;
    this.staticFieldsWrittenOnlyInEnclosingStaticInitializer =
        previous.staticFieldsWrittenOnlyInEnclosingStaticInitializer;
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
    this.brokenSuperInvokes = previous.brokenSuperInvokes;
    this.alwaysInline = previous.alwaysInline;
    this.forceInline = previous.forceInline;
    this.neverInline = previous.neverInline;
    this.whyAreYouNotInlining = previous.whyAreYouNotInlining;
    this.keepConstantArguments = previous.keepConstantArguments;
    this.keepUnusedArguments = previous.keepUnusedArguments;
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

  public boolean isLiveProgramClass(DexProgramClass clazz) {
    return liveTypes.contains(clazz.type);
  }

  public boolean isLiveProgramType(DexType type) {
    DexClass clazz = definitionFor(type);
    return clazz.isProgramClass() && isLiveProgramClass(clazz.asProgramClass());
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

  public Collection<DexClass> computeReachableInterfaces(Set<DexCallSite> desugaredCallSites) {
    Set<DexClass> interfaces = Sets.newIdentityHashSet();
    Set<DexType> seen = Sets.newIdentityHashSet();
    Deque<DexType> worklist = new ArrayDeque<>();
    for (DexProgramClass clazz : classes()) {
      worklist.add(clazz.type);
    }
    // TODO(b/129458850): Remove this once desugared classes are made part of the program classes.
    for (DexCallSite callSite : desugaredCallSites) {
      for (DexEncodedMethod method : lookupLambdaImplementedMethods(callSite)) {
        worklist.add(method.method.holder);
      }
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
    result.staticFieldsWrittenOnlyInEnclosingStaticInitializer =
        filter(
            staticFieldsWrittenOnlyInEnclosingStaticInitializer,
            not(noLongerWrittenFields::contains));
    return result;
  }

  private <T extends PresortedComparable<T>> SortedSet<T> filter(
      Set<T> items, Predicate<T> predicate) {
    return ImmutableSortedSet.copyOf(
        PresortedComparable::slowCompareTo,
        items.stream().filter(predicate).collect(Collectors.toList()));
  }

  public Map<DexField, EnumValueInfo> getEnumValueInfoMapFor(DexType enumClass) {
    assert checkIfObsolete();
    return enumValueInfoMaps.get(enumClass);
  }

  public Int2ReferenceMap<DexField> getSwitchMapFor(DexField field) {
    assert checkIfObsolete();
    return switchMaps.get(field);
  }

  /** This method provides immutable access to `fieldAccessInfoCollection`. */
  public FieldAccessInfoCollection<? extends FieldAccessInfo> getFieldAccessInfoCollection() {
    return fieldAccessInfoCollection;
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

  public boolean isInstantiatedDirectly(DexType type) {
    assert checkIfObsolete();
    assert type.isClassType();
    return type.isD8R8SynthesizedClassType()
        || instantiatedTypes.contains(type)
        || instantiatedLambdas.contains(type)
        || instantiatedAnnotationTypes.contains(type);
  }

  public boolean isInstantiatedIndirectly(DexType type) {
    assert checkIfObsolete();
    assert type.isClassType();
    synchronized (indirectlyInstantiatedTypes) {
      if (indirectlyInstantiatedTypes.containsKey(type)) {
        return indirectlyInstantiatedTypes.get(type).booleanValue();
      }
      for (DexType directSubtype : allImmediateSubtypes(type)) {
        if (isInstantiatedDirectlyOrIndirectly(directSubtype)) {
          indirectlyInstantiatedTypes.put(type, Boolean.TRUE);
          return true;
        }
      }
      indirectlyInstantiatedTypes.put(type, Boolean.FALSE);
      return false;
    }
  }

  public boolean isInstantiatedDirectlyOrIndirectly(DexType type) {
    assert checkIfObsolete();
    assert type.isClassType();
    return isInstantiatedDirectly(type) || isInstantiatedIndirectly(type);
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

  public boolean isInstanceFieldWrittenOnlyInEnclosingInstanceInitializers(DexEncodedField field) {
    assert checkIfObsolete();
    assert isFieldWritten(field) : "Expected field `" + field.toSourceString() + "` to be written";
    return instanceFieldsWrittenOnlyInEnclosingInstanceInitializers.contains(field.field);
  }

  public boolean isStaticFieldWrittenOnlyInEnclosingStaticInitializerNew(DexEncodedField field) {
    assert checkIfObsolete();
    assert isFieldWritten(field) : "Expected field `" + field.toSourceString() + "` to be written";
    if (!isPinned(field.field)) {
      DexEncodedMethod staticInitializer =
          definitionFor(field.field.holder).asProgramClass().getClassInitializer();
      if (staticInitializer != null) {
        FieldAccessInfo fieldAccessInfo = fieldAccessInfoCollection.get(field.field);
        return fieldAccessInfo != null
            && fieldAccessInfo.isWritten()
            && !fieldAccessInfo.isWrittenOutside(staticInitializer);
      }
    }
    return false;
  }

  public boolean isStaticFieldWrittenOnlyInEnclosingStaticInitializer(DexEncodedField field) {
    assert checkIfObsolete();
    assert isFieldWritten(field) : "Expected field `" + field.toSourceString() + "` to be written";
    boolean result = staticFieldsWrittenOnlyInEnclosingStaticInitializer.contains(field.field);
    assert result == isStaticFieldWrittenOnlyInEnclosingStaticInitializerNew(field);
    return result;
  }

  public boolean mayPropagateValueFor(DexReference reference) {
    assert checkIfObsolete();
    return !isPinned(reference) && !neverPropagateValue.contains(reference);
  }

  private boolean isLibraryOrClasspathField(DexEncodedField field) {
    DexClass holder = definitionFor(field.field.holder);
    return holder == null || holder.isLibraryClass() || holder.isClasspathClass();
  }

  private static <T extends PresortedComparable<T>> ImmutableSortedSet<T> rewriteItems(
      Set<T> original, Function<T, T> rewrite) {
    ImmutableSortedSet.Builder<T> builder =
        new ImmutableSortedSet.Builder<>(PresortedComparable::slowCompare);
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
  protected boolean hasAnyInstantiatedLambdas(DexType type) {
    assert checkIfObsolete();
    return instantiatedLambdas.contains(type);
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

  public boolean isMethodPinnedDirectlyOrInAncestor(DexMethod method) {
    // Look in all ancestor types.
    DexClass currentClass = definitionFor(method.holder);
    if (currentClass == null || !currentClass.isProgramClass()) {
      return false;
    }
    Set<DexType> visited = SetUtils.newIdentityHashSet(currentClass.allImmediateSupertypes());
    Deque<DexType> worklist = new ArrayDeque<>(visited);
    while (!worklist.isEmpty()) {
      DexType type = worklist.removeFirst();
      assert visited.contains(type);
      DexClass clazz = definitionFor(type);
      if (clazz == null || !clazz.isProgramClass()) {
        continue;
      }
      DexEncodedMethod methodInClass = clazz.lookupVirtualMethod(method);
      if (methodInClass != null && isPinned(methodInClass.method)) {
        return true;
      }
      for (DexType superType : clazz.allImmediateSupertypes()) {
        if (visited.add(superType)) {
          worklist.addLast(superType);
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
      DexApplication application,
      Collection<DexType> removedClasses,
      Collection<DexReference> additionalPinnedItems) {
    assert checkIfObsolete();
    return new AppInfoWithLiveness(this, application, removedClasses, additionalPinnedItems);
  }

  public AppInfoWithLiveness rewrittenWithLense(
      DirectMappedDexApplication application, GraphLense lense) {
    assert checkIfObsolete();
    return new AppInfoWithLiveness(this, application, lense);
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
        return lookupSingleVirtualTarget(target, invocationContext);
      case INTERFACE:
        return lookupSingleInterfaceTarget(target, invocationContext);
      case DIRECT:
        return lookupDirectTarget(target);
      case STATIC:
        return lookupStaticTarget(target);
      case SUPER:
        return lookupSuperTarget(target, invocationContext);
      default:
        return null;
    }
  }

  private DexEncodedMethod validateSingleVirtualTarget(
      DexEncodedMethod singleTarget, DexEncodedMethod resolutionResult) {
    assert SingleResolutionResult.isValidVirtualTarget(options(), resolutionResult);

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
    assert SingleResolutionResult.isValidVirtualTarget(options(), resolutionResult);
    // Art978_virtual_interfaceTest correctly expects an IncompatibleClassChangeError exception
    // at runtime.
    return !singleTarget.accessFlags.isAtLeastAsVisibleAs(resolutionResult.accessFlags);
  }

  /** For mapping invoke virtual instruction to single target method. */
  public DexEncodedMethod lookupSingleVirtualTarget(DexMethod method, DexType invocationContext) {
    assert checkIfObsolete();
    return lookupSingleVirtualTarget(method, invocationContext, method.holder, null);
  }

  public DexEncodedMethod lookupSingleVirtualTarget(
      DexMethod method,
      DexType invocationContext,
      DexType refinedReceiverType,
      ClassTypeLatticeElement receiverLowerBoundType) {
    assert checkIfObsolete();
    DexEncodedMethod directResult = nestAccessLookup(method, invocationContext);
    if (directResult != null) {
      return directResult;
    }

    // If the lower-bound on the receiver type is the same as the upper-bound, then we have exact
    // runtime type information. In this case, the invoke will dispatch to the resolution result
    // from the runtime type of the receiver.
    if (receiverLowerBoundType != null) {
      if (receiverLowerBoundType.getClassType() == refinedReceiverType) {
        ResolutionResult resolutionResult = resolveMethod(method.holder, method, false);
        if (resolutionResult.hasSingleTarget()
            && resolutionResult.isValidVirtualTargetForDynamicDispatch()) {
          ResolutionResult refinedResolutionResult = resolveMethod(refinedReceiverType, method);
          if (refinedResolutionResult.hasSingleTarget()
              && refinedResolutionResult.isValidVirtualTargetForDynamicDispatch()) {
            return validateSingleVirtualTarget(
                refinedResolutionResult.getSingleTarget(), resolutionResult.getSingleTarget());
          }
        }
        return null;
      } else {
        // We should never hit the case at the moment, but if we start tracking more precise lower-
        // bound type information, we should handle this case as well.
      }
    }

    // This implements the logic from
    // https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-6.html#jvms-6.5.invokevirtual
    assert method != null;
    assert isSubtype(refinedReceiverType, method.holder);
    if (method.holder.isArrayType()) {
      // For javac output this will only be clone(), but in general the methods from Object can
      // be invoked with an array type holder.
      return null;
    }
    DexClass holder = definitionFor(method.holder);
    if (holder == null || holder.isNotProgramClass() || holder.isInterface()) {
      return null;
    }
    boolean refinedReceiverIsStrictSubType = refinedReceiverType != method.holder;
    DexClass refinedHolder =
        refinedReceiverIsStrictSubType ? definitionFor(refinedReceiverType) : holder;
    assert refinedHolder != null;
    assert refinedHolder.isProgramClass();
    assert !refinedHolder.isInterface();
    if (method.isSingleVirtualMethodCached(refinedReceiverType)) {
      return method.getSingleVirtualMethodCache(refinedReceiverType);
    }
    // First get the target for the holder type.
    ResolutionResult topMethod = resolveMethodOnClass(holder, method);
    // We might hit none or multiple targets. Both make this fail at runtime.
    if (!topMethod.hasSingleTarget() || !topMethod.isValidVirtualTarget(options())) {
      method.setSingleVirtualMethodCache(refinedReceiverType, null);
      return null;
    }
    // Now, resolve the target with the refined receiver type.
    ResolutionResult refinedResolutionResult =
        refinedReceiverIsStrictSubType ? resolveMethodOnClass(refinedHolder, method) : topMethod;
    DexEncodedMethod topSingleTarget = refinedResolutionResult.getSingleTarget();
    DexClass topHolder = definitionFor(topSingleTarget.method.holder);
    // We need to know whether the top method is from an interface, as that would allow it to be
    // shadowed by a default method from an interface further down.
    boolean topIsFromInterface = topHolder.isInterface();
    // Now look at all subtypes and search for overrides.
    DexEncodedMethod result =
        validateSingleVirtualTarget(
            findSingleTargetFromSubtypes(
                refinedReceiverType,
                method,
                topSingleTarget,
                !refinedHolder.accessFlags.isAbstract(),
                topIsFromInterface),
            topMethod.getSingleTarget());
    assert result != DexEncodedMethod.SENTINEL;
    method.setSingleVirtualMethodCache(refinedReceiverType, result);
    return result;
  }

  private DexEncodedMethod nestAccessLookup(DexMethod method, DexType invocationContext) {
    if (method.holder == invocationContext || !definitionFor(invocationContext).isInANest()) {
      return null;
    }
    DexEncodedMethod directTarget = lookupDirectTarget(method);
    assert directTarget == null || directTarget.method.holder == method.holder;
    if (directTarget != null
        && directTarget.isPrivateMethod()
        && NestUtils.sameNest(method.holder, invocationContext, this)) {
      return directTarget;
    }

    return null;
  }

  /**
   * Computes which methods overriding <code>method</code> are visible for the subtypes of type.
   *
   * <p><code>candidate</code> is the definition further up the hierarchy that is visible from the
   * subtypes. If <code>candidateIsReachable</code> is true, the provided candidate is already a
   * target for a type further up the chain, so anything found in subtypes is a conflict. If it is
   * false, the target exists but is not reachable from a live type.
   *
   * <p>Returns <code>null</code> if the given type has no subtypes or all subtypes are abstract.
   * Returns {@link DexEncodedMethod#SENTINEL} if multiple live overrides were found. Returns the
   * single virtual target otherwise.
   */
  private DexEncodedMethod findSingleTargetFromSubtypes(
      DexType type,
      DexMethod method,
      DexEncodedMethod candidate,
      boolean candidateIsReachable,
      boolean checkForInterfaceConflicts) {
    // For kept types we do not know all subtypes, so abort if the method is also kept.
    if (isPinned(type) && isMethodPinnedDirectlyOrInAncestor(candidate.method)) {
      return DexEncodedMethod.SENTINEL;
    }
    // If the candidate is reachable, we already have a previous result.
    DexEncodedMethod result = candidateIsReachable ? candidate : null;
    for (DexType subtype : allImmediateExtendsSubtypes(type)) {
      DexClass clazz = definitionFor(subtype);
      DexEncodedMethod target = clazz.lookupVirtualMethod(method);
      if (target != null && !target.isPrivateMethod()) {
        // We found a method on this class. If this class is not abstract it is a runtime
        // reachable override and hence a conflict.
        if (!clazz.accessFlags.isAbstract()) {
          if (result != null && result != target) {
            // We found a new target on this subtype that does not match the previous one. Fail.
            return DexEncodedMethod.SENTINEL;
          }
          // Add the first or matching target.
          result = target;
        }
      }
      if (checkForInterfaceConflicts) {
        // We have to check whether there are any default methods in implemented interfaces.
        if (interfacesMayHaveDefaultFor(clazz.interfaces, method)) {
          return DexEncodedMethod.SENTINEL;
        }
      }
      DexEncodedMethod newCandidate = target == null ? candidate : target;
      // If we have a new target and did not fail, it is not an override of a reachable method.
      // Whether the target is actually reachable depends on whether this class is abstract.
      // If we did not find a new target, the candidate is reachable if it was before, or if this
      // class is not abstract.
      boolean newCandidateIsReachable =
          !clazz.accessFlags.isAbstract() || ((target == null) && candidateIsReachable);
      DexEncodedMethod subtypeTarget =
          findSingleTargetFromSubtypes(
              subtype, method, newCandidate, newCandidateIsReachable, checkForInterfaceConflicts);
      if (subtypeTarget != null) {
        // We found a target in the subclasses. If we already have a different result, fail.
        if (result != null && result != subtypeTarget) {
          return DexEncodedMethod.SENTINEL;
        }
        // Remember this new result.
        result = subtypeTarget;
      }
    }
    return result;
  }

  /**
   * Checks whether any interface in the given list or their super interfaces implement a default
   * method.
   *
   * <p>This method is conservative for unknown interfaces and interfaces from the library.
   */
  private boolean interfacesMayHaveDefaultFor(DexTypeList ifaces, DexMethod method) {
    for (DexType iface : ifaces.values) {
      DexClass clazz = definitionFor(iface);
      if (clazz == null || clazz.isNotProgramClass()) {
        return true;
      }
      DexEncodedMethod candidate = clazz.lookupMethod(method);
      if (candidate != null && !candidate.accessFlags.isAbstract()) {
        return true;
      }
      if (interfacesMayHaveDefaultFor(clazz.interfaces, method)) {
        return true;
      }
    }
    return false;
  }

  public DexEncodedMethod lookupSingleInterfaceTarget(DexMethod method, DexType invocationContext) {
    assert checkIfObsolete();
    return lookupSingleInterfaceTarget(method, invocationContext, method.holder, null);
  }

  public DexEncodedMethod lookupSingleInterfaceTarget(
      DexMethod method,
      DexType invocationContext,
      DexType refinedReceiverType,
      ClassTypeLatticeElement receiverLowerBoundType) {
    assert checkIfObsolete();
    DexEncodedMethod directResult = nestAccessLookup(method, invocationContext);
    if (directResult != null) {
      return directResult;
    }
    if (instantiatedLambdas.contains(method.holder)) {
      return null;
    }

    // If the lower-bound on the receiver type is the same as the upper-bound, then we have exact
    // runtime type information. In this case, the invoke will dispatch to the resolution result
    // from the runtime type of the receiver.
    if (receiverLowerBoundType != null) {
      if (receiverLowerBoundType.getClassType() == refinedReceiverType) {
        ResolutionResult resolutionResult = resolveMethod(method.holder, method, true);
        if (resolutionResult.hasSingleTarget()
            && resolutionResult.isValidVirtualTargetForDynamicDispatch()) {
          ResolutionResult refinedResolutionResult = resolveMethod(refinedReceiverType, method);
          if (refinedResolutionResult.hasSingleTarget()
              && refinedResolutionResult.isValidVirtualTargetForDynamicDispatch()) {
            return validateSingleVirtualTarget(
                refinedResolutionResult.getSingleTarget(), resolutionResult.getSingleTarget());
          }
        }
        return null;
      } else {
        // We should never hit the case at the moment, but if we start tracking more precise lower-
        // bound type information, we should handle this case as well.
      }
    }

    DexClass holder = definitionFor(method.holder);
    if ((holder == null) || holder.isNotProgramClass() || !holder.accessFlags.isInterface()) {
      return null;
    }
    // First check that there is a target for this invoke-interface to hit. If there is none,
    // this will fail at runtime.
    DexEncodedMethod topTarget = resolveMethodOnInterface(holder, method).getSingleTarget();
    if (topTarget == null || !SingleResolutionResult.isValidVirtualTarget(options(), topTarget)) {
      return null;
    }
    // For kept types we cannot ensure a single target.
    if (pinnedItems.contains(method.holder)) {
      return null;
    }
    DexEncodedMethod result = null;
    // The loop will ignore abstract classes that are not kept as they should not be a target
    // at runtime.
    Iterable<DexType> subTypesToExplore =
        refinedReceiverType == method.holder
            ? subtypes(method.holder)
            : Iterables.concat(
                ImmutableList.of(refinedReceiverType), subtypes(refinedReceiverType));
    for (DexType type : subTypesToExplore) {
      if (instantiatedLambdas.contains(type)) {
        return null;
      }
      if (pinnedItems.contains(type)) {
        // For kept classes we cannot ensure a single target.
        return null;
      }
      DexClass clazz = definitionFor(type);
      if (clazz.isInterface()) {
        // Default methods are looked up when looking at a specific subtype that does not
        // override them, so we ignore interface methods here. Otherwise, we would look up
        // default methods that are factually never used.
      } else if (!clazz.accessFlags.isAbstract()) {
        DexEncodedMethod resolutionResult = resolveMethodOnClass(clazz, method).getSingleTarget();
        if (resolutionResult == null || isInvalidSingleVirtualTarget(resolutionResult, topTarget)) {
          // This will fail at runtime.
          return null;
        }
        if (result != null && result != resolutionResult) {
          return null;
        }
        result = resolutionResult;
      }
    }
    assert result == null || !isInvalidSingleVirtualTarget(result, topTarget);
    return result == null || !result.isVirtualMethod() ? null : result;
  }

  public AppInfoWithLiveness addSwitchMaps(Map<DexField, Int2ReferenceMap<DexField>> switchMaps) {
    assert checkIfObsolete();
    assert this.switchMaps.isEmpty();
    return new AppInfoWithLiveness(this, switchMaps, enumValueInfoMaps);
  }

  public AppInfoWithLiveness addEnumValueInfoMaps(
      Map<DexType, Map<DexField, EnumValueInfo>> enumValueInfoMaps) {
    assert checkIfObsolete();
    assert this.enumValueInfoMaps.isEmpty();
    return new AppInfoWithLiveness(this, switchMaps, enumValueInfoMaps);
  }
}
