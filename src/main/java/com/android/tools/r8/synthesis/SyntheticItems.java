// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.MethodProcessingId;
import com.android.tools.r8.synthesis.SyntheticFinalization.Result;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SyntheticItems implements SyntheticDefinitionsProvider {

  static final int INVALID_ID_AFTER_SYNTHETIC_FINALIZATION = -1;

  /**
   * The internal synthetic class separator is only used for representing synthetic items during
   * compilation. In particular, this separator must never be used to write synthetic classes to the
   * final compilation result.
   */
  public static final String INTERNAL_SYNTHETIC_CLASS_SEPARATOR = "-$$InternalSynthetic";

  /**
   * The external synthetic class separator is used when writing classes. It may appear in types
   * during compilation as the output of a compilation may be the input to another.
   */
  public static final String EXTERNAL_SYNTHETIC_CLASS_SEPARATOR = "-$$ExternalSynthetic";

  /** Method prefix when generating synthetic methods in a class. */
  public static final String INTERNAL_SYNTHETIC_METHOD_PREFIX = "m";

  public static boolean verifyNotInternalSynthetic(DexType type) {
    assert !type.toDescriptorString().contains(SyntheticItems.INTERNAL_SYNTHETIC_CLASS_SEPARATOR);
    return true;
  }

  /** Globally incremented id for the next internal synthetic class. */
  private int nextSyntheticId;

  /**
   * Thread safe collection of synthesized classes that are not yet committed to the application.
   *
   * <p>TODO(b/158159959): Remove legacy support.
   */
  private final Map<DexType, DexProgramClass> legacyPendingClasses = new ConcurrentHashMap<>();

  /**
   * Immutable set of synthetic types in the application (eg, committed).
   *
   * <p>TODO(b/158159959): Remove legacy support.
   */
  private final ImmutableSet<DexType> legacySyntheticTypes;

  /** Thread safe collection of synthetic items not yet committed to the application. */
  private final ConcurrentHashMap<DexType, SyntheticDefinition> pendingDefinitions =
      new ConcurrentHashMap<>();

  /** Mapping from synthetic type to its synthetic description. */
  private final ImmutableMap<DexType, SyntheticReference> nonLecacySyntheticItems;

  // Only for use from initial AppInfo/AppInfoWithClassHierarchy create functions. */
  public static CommittedItems createInitialSyntheticItems(DexApplication application) {
    return new CommittedItems(
        0, application, ImmutableSet.of(), ImmutableMap.of(), ImmutableList.of());
  }

  // Only for conversion to a mutable synthetic items collection.
  SyntheticItems(CommittedItems commit) {
    this(commit.nextSyntheticId, commit.legacySyntheticTypes, commit.syntheticItems);
  }

  private SyntheticItems(
      int nextSyntheticId,
      ImmutableSet<DexType> legacySyntheticTypes,
      ImmutableMap<DexType, SyntheticReference> nonLecacySyntheticItems) {
    this.nextSyntheticId = nextSyntheticId;
    this.legacySyntheticTypes = legacySyntheticTypes;
    this.nonLecacySyntheticItems = nonLecacySyntheticItems;
    assert Sets.intersection(nonLecacySyntheticItems.keySet(), legacySyntheticTypes).isEmpty();
  }

  public static void collectSyntheticInputs(AppView<AppInfo> appView) {
    // Collecting synthetic items must be the very first task after application build.
    SyntheticItems synthetics = appView.getSyntheticItems();
    assert synthetics.nextSyntheticId == 0;
    assert synthetics.nonLecacySyntheticItems.isEmpty();
    assert !synthetics.hasPendingSyntheticClasses();
    if (appView.options().intermediate) {
      // If the compilation is in intermediate mode the synthetics should just be passed through.
      return;
    }
    ImmutableMap.Builder<DexType, SyntheticReference> pending = ImmutableMap.builder();
    // TODO(b/158159959): Consider identifying synthetics in the input reader to speed this up.
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      DexType annotatedContextType = isSynthesizedMethodsContainer(clazz, appView.dexItemFactory());
      if (annotatedContextType == null) {
        continue;
      }
      clazz.setAnnotations(DexAnnotationSet.empty());
      SynthesizingContext context =
          SynthesizingContext.fromSyntheticInputClass(clazz, annotatedContextType);
      clazz.forEachProgramMethod(
          // TODO(b/158159959): Support having multiple methods per class.
          method -> {
            method.getDefinition().setAnnotations(DexAnnotationSet.empty());
            pending.put(clazz.type, new SyntheticMethodDefinition(context, method).toReference());
          });
    }
    pending.putAll(synthetics.nonLecacySyntheticItems);
    ImmutableMap<DexType, SyntheticReference> nonLegacySyntheticItems = pending.build();
    if (nonLegacySyntheticItems.isEmpty()) {
      return;
    }
    CommittedItems commit =
        new CommittedItems(
            synthetics.nextSyntheticId,
            appView.appInfo().app(),
            synthetics.legacySyntheticTypes,
            nonLegacySyntheticItems,
            ImmutableList.of());
    appView.setAppInfo(new AppInfo(commit, appView.appInfo().getMainDexClasses()));
  }

  private static DexType isSynthesizedMethodsContainer(
      DexProgramClass clazz, DexItemFactory factory) {
    ClassAccessFlags flags = clazz.accessFlags;
    if (!flags.isSynthetic() || flags.isAbstract() || flags.isEnum()) {
      return null;
    }
    DexType contextType =
        DexAnnotation.getSynthesizedClassAnnotationContextType(clazz.annotations(), factory);
    if (contextType == null) {
      return null;
    }
    if (clazz.superType != factory.objectType) {
      return null;
    }
    if (!clazz.interfaces.isEmpty()) {
      return null;
    }
    if (clazz.annotations().size() != 1) {
      return null;
    }
    for (DexEncodedMethod method : clazz.methods()) {
      if (!SyntheticMethodBuilder.isValidSyntheticMethod(method)) {
        return null;
      }
    }
    return contextType;
  }

  // Internal synthetic id creation helpers.

  private synchronized String getNextSyntheticId() {
    if (nextSyntheticId == INVALID_ID_AFTER_SYNTHETIC_FINALIZATION) {
      throw new InternalCompilerError(
          "Unexpected attempt to synthesize classes after synthetic finalization.");
    }
    return Integer.toString(nextSyntheticId++);
  }

  // Predicates and accessors.

  @Override
  public DexClass definitionFor(DexType type, Function<DexType, DexClass> baseDefinitionFor) {
    DexProgramClass pending = legacyPendingClasses.get(type);
    if (pending == null) {
      SyntheticDefinition item = pendingDefinitions.get(type);
      if (item != null) {
        pending = item.getHolder();
      }
    }
    if (pending != null) {
      assert baseDefinitionFor.apply(type) == null
          : "Pending synthetic definition also present in the active program: " + type;
      return pending;
    }
    return baseDefinitionFor.apply(type);
  }

  public boolean hasPendingSyntheticClasses() {
    return !legacyPendingClasses.isEmpty() || !pendingDefinitions.isEmpty();
  }

  public Collection<DexProgramClass> getPendingSyntheticClasses() {
    List<DexProgramClass> pending =
        new ArrayList<>(pendingDefinitions.size() + legacyPendingClasses.size());
    for (SyntheticDefinition item : pendingDefinitions.values()) {
      pending.add(item.getHolder());
    }
    pending.addAll(legacyPendingClasses.values());
    return Collections.unmodifiableList(pending);
  }

  private boolean isCommittedSynthetic(DexType type) {
    return nonLecacySyntheticItems.containsKey(type) || legacySyntheticTypes.contains(type);
  }

  public boolean isPendingSynthetic(DexType type) {
    return pendingDefinitions.containsKey(type) || legacyPendingClasses.containsKey(type);
  }

  public boolean isSyntheticClass(DexType type) {
    return isCommittedSynthetic(type)
        || isPendingSynthetic(type)
        // TODO(b/158159959): Remove usage of name-based identification.
        || type.isD8R8SynthesizedClassType();
  }

  public boolean isSyntheticClass(DexProgramClass clazz) {
    return isSyntheticClass(clazz.type);
  }

  public Collection<DexProgramClass> getLegacyPendingClasses() {
    return Collections.unmodifiableCollection(legacyPendingClasses.values());
  }

  private SynthesizingContext getSynthesizingContext(ProgramDefinition context) {
    SyntheticDefinition pendingItemContext = pendingDefinitions.get(context.getContextType());
    if (pendingItemContext != null) {
      return pendingItemContext.getContext();
    }
    SyntheticReference committedItemContext = nonLecacySyntheticItems.get(context.getContextType());
    return committedItemContext != null
        ? committedItemContext.getContext()
        : SynthesizingContext.fromNonSyntheticInputContext(context);
  }

  // Addition and creation of synthetic items.

  // TODO(b/158159959): Remove the usage of this direct class addition (and name-based id).
  public void addLegacySyntheticClass(DexProgramClass clazz) {
    assert clazz.type.isD8R8SynthesizedClassType();
    assert !isCommittedSynthetic(clazz.type);
    DexProgramClass previous = legacyPendingClasses.put(clazz.type, clazz);
    assert previous == null || previous == clazz;
  }

  /** Create a single synthetic method item. */
  public ProgramMethod createMethod(
      ProgramDefinition context, DexItemFactory factory, Consumer<SyntheticMethodBuilder> fn) {
    return createMethod(context, factory, fn, this::getNextSyntheticId);
  }

  public ProgramMethod createMethod(
      ProgramDefinition context,
      DexItemFactory factory,
      Consumer<SyntheticMethodBuilder> fn,
      MethodProcessingId methodProcessingId) {
    return createMethod(context, factory, fn, methodProcessingId::getFullyQualifiedIdAndIncrement);
  }

  private ProgramMethod createMethod(
      ProgramDefinition context,
      DexItemFactory factory,
      Consumer<SyntheticMethodBuilder> fn,
      Supplier<String> syntheticIdSupplier) {
    assert nextSyntheticId != INVALID_ID_AFTER_SYNTHETIC_FINALIZATION;
    // Obtain the outer synthesizing context in the case the context itself is synthetic.
    // This is to ensure a flat input-type -> synthetic-item mapping.
    SynthesizingContext outerContext = getSynthesizingContext(context);
    DexType type = outerContext.createHygienicType(syntheticIdSupplier.get(), factory);
    SyntheticClassBuilder classBuilder = new SyntheticClassBuilder(type, outerContext, factory);
    DexProgramClass clazz = classBuilder.addMethod(fn).build();
    ProgramMethod method = new ProgramMethod(clazz, clazz.methods().iterator().next());
    addPendingDefinition(new SyntheticMethodDefinition(outerContext, method));
    return method;
  }

  private void addPendingDefinition(SyntheticDefinition definition) {
    pendingDefinitions.put(definition.getHolder().getType(), definition);
  }

  // Commit of the synthetic items to a new fully populated application.

  public CommittedItems commit(DexApplication application) {
    return commitPrunedClasses(application, Collections.emptySet());
  }

  public CommittedItems commitPrunedClasses(
      DexApplication application, Set<DexType> removedClasses) {
    return commit(
        application,
        removedClasses,
        legacyPendingClasses,
        legacySyntheticTypes,
        pendingDefinitions,
        nonLecacySyntheticItems,
        nextSyntheticId);
  }

  public CommittedItems commitRewrittenWithLens(
      DexApplication application, NonIdentityGraphLens lens) {
    // Rewrite the previously committed synthetic types.
    ImmutableSet<DexType> rewrittenLegacyTypes = lens.rewriteTypes(this.legacySyntheticTypes);
    ImmutableMap.Builder<DexType, SyntheticReference> rewrittenItems = ImmutableMap.builder();
    for (SyntheticReference reference : nonLecacySyntheticItems.values()) {
      SyntheticReference rewritten = reference.rewrite(lens);
      if (rewritten != null) {
        rewrittenItems.put(rewritten.getHolder(), rewritten);
      }
    }
    // No pending item should need rewriting.
    assert legacyPendingClasses.keySet().equals(lens.rewriteTypes(legacyPendingClasses.keySet()));
    assert pendingDefinitions.keySet().equals(lens.rewriteTypes(pendingDefinitions.keySet()));
    return commit(
        application,
        Collections.emptySet(),
        legacyPendingClasses,
        rewrittenLegacyTypes,
        pendingDefinitions,
        rewrittenItems.build(),
        nextSyntheticId);
  }

  private static CommittedItems commit(
      DexApplication application,
      Set<DexType> removedClasses,
      Map<DexType, DexProgramClass> legacyPendingClasses,
      ImmutableSet<DexType> legacySyntheticTypes,
      ConcurrentHashMap<DexType, SyntheticDefinition> pendingDefinitions,
      ImmutableMap<DexType, SyntheticReference> syntheticItems,
      int nextSyntheticId) {
    // Legacy synthetics must already have been committed.
    assert verifyClassesAreInApp(application, legacyPendingClasses.values());
    // Add the set of legacy definitions to the synthetic types.
    ImmutableSet<DexType> mergedLegacyTypes = legacySyntheticTypes;
    if (!legacyPendingClasses.isEmpty() || !removedClasses.isEmpty()) {
      ImmutableSet.Builder<DexType> legacyBuilder = ImmutableSet.builder();
      filteredAdd(legacySyntheticTypes, removedClasses, legacyBuilder);
      filteredAdd(legacyPendingClasses.keySet(), removedClasses, legacyBuilder);
      mergedLegacyTypes = legacyBuilder.build();
    }
    // The set of synthetic items is the union of the previous types plus the pending additions.
    ImmutableMap<DexType, SyntheticReference> mergedItems;
    ImmutableList<DexType> additions;
    DexApplication amendedApplication;
    if (pendingDefinitions.isEmpty()) {
      mergedItems = filteredCopy(syntheticItems, removedClasses);
      additions = ImmutableList.of();
      amendedApplication = application;
    } else {
      DexApplication.Builder<?> appBuilder = application.builder();
      ImmutableMap.Builder<DexType, SyntheticReference> itemsBuilder = ImmutableMap.builder();
      ImmutableList.Builder<DexType> additionsBuilder = ImmutableList.builder();
      for (SyntheticDefinition definition : pendingDefinitions.values()) {
        if (removedClasses.contains(definition.getHolder().getType())) {
          continue;
        }
        SyntheticReference reference = definition.toReference();
        itemsBuilder.put(reference.getHolder(), reference);
        additionsBuilder.add(definition.getHolder().getType());
        appBuilder.addProgramClass(definition.getHolder());
      }
      filteredAdd(syntheticItems, removedClasses, itemsBuilder);
      mergedItems = itemsBuilder.build();
      additions = additionsBuilder.build();
      amendedApplication = appBuilder.build();
    }
    return new CommittedItems(
        nextSyntheticId, amendedApplication, mergedLegacyTypes, mergedItems, additions);
  }

  private static void filteredAdd(
      Set<DexType> input, Set<DexType> excludeSet, Builder<DexType> result) {
    if (excludeSet.isEmpty()) {
      result.addAll(input);
    } else {
      for (DexType type : input) {
        if (!excludeSet.contains(type)) {
          result.add(type);
        }
      }
    }
  }

  private static ImmutableMap<DexType, SyntheticReference> filteredCopy(
      ImmutableMap<DexType, SyntheticReference> syntheticItems, Set<DexType> removedClasses) {
    if (removedClasses.isEmpty()) {
      return syntheticItems;
    }
    ImmutableMap.Builder<DexType, SyntheticReference> builder = ImmutableMap.builder();
    filteredAdd(syntheticItems, removedClasses, builder);
    return builder.build();
  }

  private static void filteredAdd(
      ImmutableMap<DexType, SyntheticReference> syntheticItems,
      Set<DexType> removedClasses,
      ImmutableMap.Builder<DexType, SyntheticReference> builder) {
    if (removedClasses.isEmpty()) {
      builder.putAll(syntheticItems);
    } else {
      syntheticItems.forEach(
          (t, r) -> {
            if (!removedClasses.contains(t)) {
              builder.put(t, r);
            }
          });
    }
  }

  private static boolean verifyClassesAreInApp(
      DexApplication app, Collection<DexProgramClass> classes) {
    for (DexProgramClass clazz : classes) {
      assert app.programDefinitionFor(clazz.type) != null : "Missing synthetic: " + clazz.type;
    }
    return true;
  }

  // Finalization of synthetic items.

  public Result computeFinalSynthetics(AppView<?> appView) {
    assert !hasPendingSyntheticClasses();
    return new SyntheticFinalization(
            appView.options(), legacySyntheticTypes, nonLecacySyntheticItems)
        .computeFinalSynthetics(appView);
  }
}
