// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.ir.conversion.MethodProcessingId;
import com.android.tools.r8.synthesis.SyntheticFinalization.Result;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class SyntheticItems implements SyntheticDefinitionsProvider {

  static final int INVALID_ID_AFTER_SYNTHETIC_FINALIZATION = -1;

  /** Globally incremented id for the next internal synthetic class. */
  private int nextSyntheticId;

  /** Collection of pending items. */
  private static class PendingSynthetics {
    /**
     * Thread safe collection of synthesized classes that are not yet committed to the application.
     *
     * <p>TODO(b/158159959): Remove legacy support.
     */
    private final Map<DexType, DexProgramClass> legacyClasses = new ConcurrentHashMap<>();

    /** Thread safe collection of synthetic items not yet committed to the application. */
    private final ConcurrentHashMap<DexType, SyntheticDefinition<?, ?, ?>> nonLegacyDefinitions =
        new ConcurrentHashMap<>();

    boolean isEmpty() {
      return legacyClasses.isEmpty() && nonLegacyDefinitions.isEmpty();
    }

    boolean containsType(DexType type) {
      return legacyClasses.containsKey(type) || nonLegacyDefinitions.containsKey(type);
    }

    boolean verifyNotRewritten(NonIdentityGraphLens lens) {
      assert legacyClasses.keySet().equals(lens.rewriteTypes(legacyClasses.keySet()));
      assert nonLegacyDefinitions.keySet().equals(lens.rewriteTypes(nonLegacyDefinitions.keySet()));
      return true;
    }

    Collection<DexProgramClass> getAllProgramClasses() {
      List<DexProgramClass> allPending =
          new ArrayList<>(nonLegacyDefinitions.size() + legacyClasses.size());
      for (SyntheticDefinition<?, ?, ?> item : nonLegacyDefinitions.values()) {
        if (item.isProgramDefinition()) {
          allPending.add(item.asProgramDefinition().getHolder());
        }
      }
      allPending.addAll(legacyClasses.values());
      return Collections.unmodifiableList(allPending);
    }
  }

  private final CommittedSyntheticsCollection committed;

  private final PendingSynthetics pending = new PendingSynthetics();

  // Only for use from initial AppInfo/AppInfoWithClassHierarchy create functions. */
  public static CommittedItems createInitialSyntheticItems(DexApplication application) {
    return new CommittedItems(
        0, application, CommittedSyntheticsCollection.empty(), ImmutableList.of());
  }

  // Only for conversion to a mutable synthetic items collection.
  SyntheticItems(CommittedItems commit) {
    this(commit.nextSyntheticId, commit.committed);
  }

  private SyntheticItems(int nextSyntheticId, CommittedSyntheticsCollection committed) {
    this.nextSyntheticId = nextSyntheticId;
    this.committed = committed;
  }

  public static void collectSyntheticInputs(AppView<AppInfo> appView) {
    // Collecting synthetic items must be the very first task after application build.
    SyntheticItems synthetics = appView.getSyntheticItems();
    assert synthetics.nextSyntheticId == 0;
    assert synthetics.committed.isEmpty();
    assert synthetics.pending.isEmpty();
    if (appView.options().intermediate) {
      // If the compilation is in intermediate mode the synthetics should just be passed through.
      return;
    }
    CommittedSyntheticsCollection.Builder builder = synthetics.committed.builder();
    // TODO(b/158159959): Consider identifying synthetics in the input reader to speed this up.
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      SyntheticMarker marker =
          SyntheticMarker.stripMarkerFromClass(clazz, appView.dexItemFactory());
      if (marker.isSyntheticMethods()) {
        clazz.forEachProgramMethod(
            // TODO(b/158159959): Support having multiple methods per class.
            method -> {
              builder.addNonLegacyMethod(
                  new SyntheticMethodDefinition(marker.getKind(), marker.getContext(), method));
            });
      } else if (marker.isSyntheticClass()) {
        builder.addNonLegacyClass(
            new SyntheticProgramClassDefinition(marker.getKind(), marker.getContext(), clazz));
      }
    }
    CommittedSyntheticsCollection committed = builder.build();
    if (committed.isEmpty()) {
      return;
    }
    CommittedItems commit =
        new CommittedItems(
            synthetics.nextSyntheticId, appView.appInfo().app(), committed, ImmutableList.of());
    appView.setAppInfo(new AppInfo(commit, appView.appInfo().getMainDexClasses()));
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
    DexClass clazz = pending.legacyClasses.get(type);
    if (clazz == null) {
      SyntheticDefinition<?, ?, ?> item = pending.nonLegacyDefinitions.get(type);
      if (item != null) {
        clazz = item.getHolder();
        assert clazz.isProgramClass() == item.isProgramDefinition();
        assert clazz.isClasspathClass() == item.isClasspathDefinition();
      }
    }
    if (clazz != null) {
      assert baseDefinitionFor.apply(type) == null
          : "Pending synthetic definition also present in the active program: " + type;
      return clazz;
    }
    return baseDefinitionFor.apply(type);
  }

  public boolean verifyNonLegacySyntheticsAreCommitted() {
    assert pending.nonLegacyDefinitions.isEmpty()
        : "Uncommitted synthetics: "
            + pending.nonLegacyDefinitions.keySet().stream()
                .map(DexType::getName)
                .collect(Collectors.joining(", "));
    return true;
  }

  public boolean hasPendingSyntheticClasses() {
    return !pending.isEmpty();
  }

  public Collection<DexProgramClass> getPendingSyntheticClasses() {
    return pending.getAllProgramClasses();
  }

  private boolean isCommittedSynthetic(DexType type) {
    return committed.containsType(type);
  }

  private boolean isLegacyCommittedSynthetic(DexType type) {
    return committed.containsLegacyType(type);
  }

  public boolean isPendingSynthetic(DexType type) {
    return pending.containsType(type);
  }

  private boolean isLegacyPendingSynthetic(DexType type) {
    return pending.legacyClasses.containsKey(type);
  }

  public boolean isLegacySyntheticClass(DexType type) {
    return isLegacyCommittedSynthetic(type) || isLegacyPendingSynthetic(type);
  }

  public boolean isLegacySyntheticClass(DexProgramClass clazz) {
    return isLegacySyntheticClass(clazz.getType());
  }

  public boolean isNonLegacySynthetic(DexProgramClass clazz) {
    return isNonLegacySynthetic(clazz.type);
  }

  public boolean isNonLegacySynthetic(DexType type) {
    return isCommittedSynthetic(type) || isPendingSynthetic(type);
  }

  public boolean isSyntheticClass(DexType type) {
    return isLegacySyntheticClass(type) || isNonLegacySynthetic(type);
  }

  public boolean isSyntheticClass(DexProgramClass clazz) {
    return isSyntheticClass(clazz.type);
  }

  // The compiler should not inspect the kind of a synthetic, so this provided only as a assertion
  // utility.
  public boolean verifySyntheticLambdaProperty(
      DexProgramClass clazz,
      Predicate<DexProgramClass> ifIsLambda,
      Predicate<DexProgramClass> ifNotLambda) {
    SyntheticReference<?, ?, ?> reference = committed.getNonLegacyItem(clazz.getType());
    if (reference == null) {
      SyntheticDefinition<?, ?, ?> definition = pending.nonLegacyDefinitions.get(clazz.getType());
      if (definition != null) {
        reference = definition.toReference();
      }
    }
    if (reference != null && reference.getKind() == SyntheticKind.LAMBDA) {
      assert ifIsLambda.test(clazz);
    } else {
      assert ifNotLambda.test(clazz);
    }
    return true;
  }

  public Collection<DexProgramClass> getLegacyPendingClasses() {
    return Collections.unmodifiableCollection(pending.legacyClasses.values());
  }

  private SynthesizingContext getSynthesizingContext(ProgramDefinition context) {
    DexType contextType = context.getContextType();
    SyntheticDefinition<?, ?, ?> existingDefinition = pending.nonLegacyDefinitions.get(contextType);
    if (existingDefinition != null) {
      return existingDefinition.getContext();
    }
    SyntheticReference<?, ?, ?> existingReference = committed.getNonLegacyItem(contextType);
    if (existingReference != null) {
      return existingReference.getContext();
    }
    // This context is not nested in an existing synthetic context so create a new "leaf" context.
    return SynthesizingContext.fromNonSyntheticInputContext(context);
  }

  // Addition and creation of synthetic items.

  // TODO(b/158159959): Remove the usage of this direct class addition.
  public void addLegacySyntheticClass(DexProgramClass clazz) {
    assert !isCommittedSynthetic(clazz.type);
    assert !pending.nonLegacyDefinitions.containsKey(clazz.type);
    DexProgramClass previous = pending.legacyClasses.put(clazz.type, clazz);
    assert previous == null || previous == clazz;
  }

  public DexProgramClass createClass(
      SyntheticKind kind,
      DexProgramClass context,
      DexItemFactory factory,
      Supplier<String> syntheticIdSupplier,
      Consumer<SyntheticProgramClassBuilder> fn) {
    // Obtain the outer synthesizing context in the case the context itself is synthetic.
    // This is to ensure a flat input-type -> synthetic-item mapping.
    SynthesizingContext outerContext = getSynthesizingContext(context);
    DexType type =
        SyntheticNaming.createInternalType(kind, outerContext, syntheticIdSupplier.get(), factory);
    SyntheticProgramClassBuilder classBuilder =
        new SyntheticProgramClassBuilder(type, outerContext, factory);
    fn.accept(classBuilder);
    DexProgramClass clazz = classBuilder.build();
    addPendingDefinition(new SyntheticProgramClassDefinition(kind, outerContext, clazz));
    return clazz;
  }

  public DexClasspathClass createClasspathClass(
      SyntheticKind kind, DexType type, DexClass context, DexItemFactory factory) {
    // Obtain the outer synthesizing context in the case the context itself is synthetic.
    // This is to ensure a flat input-type -> synthetic-item mapping.
    SynthesizingContext outerContext = SynthesizingContext.fromNonSyntheticInputContext(context);
    SyntheticClasspathClassBuilder classBuilder =
        new SyntheticClasspathClassBuilder(type, outerContext, factory);
    DexClasspathClass clazz = classBuilder.build();
    addPendingDefinition(new SyntheticClasspathClassDefinition(kind, outerContext, clazz));
    return clazz;
  }

  /** Create a single synthetic method item. */
  public ProgramMethod createMethod(
      SyntheticKind kind,
      ProgramDefinition context,
      DexItemFactory factory,
      Consumer<SyntheticMethodBuilder> fn) {
    return createMethod(kind, context, factory, fn, this::getNextSyntheticId);
  }

  // TODO(b/172194101): Remove this once the uniqueness is a property of the context.
  public ProgramMethod createMethod(
      SyntheticKind kind,
      ProgramDefinition context,
      DexItemFactory factory,
      Consumer<SyntheticMethodBuilder> fn,
      MethodProcessingId methodProcessingId) {
    return createMethod(
        kind,
        context,
        factory,
        fn,
        methodProcessingId != null
            ? methodProcessingId::getFullyQualifiedIdAndIncrement
            : this::getNextSyntheticId);
  }

  // TODO(b/172194101): Remove/private this once the uniqueness is a property of the context.
  public ProgramMethod createMethod(
      SyntheticKind kind,
      ProgramDefinition context,
      DexItemFactory factory,
      Consumer<SyntheticMethodBuilder> fn,
      Supplier<String> syntheticIdSupplier) {
    assert nextSyntheticId != INVALID_ID_AFTER_SYNTHETIC_FINALIZATION;
    // Obtain the outer synthesizing context in the case the context itself is synthetic.
    // This is to ensure a flat input-type -> synthetic-item mapping.
    SynthesizingContext outerContext = getSynthesizingContext(context);
    DexType type =
        SyntheticNaming.createInternalType(kind, outerContext, syntheticIdSupplier.get(), factory);
    SyntheticProgramClassBuilder classBuilder =
        new SyntheticProgramClassBuilder(type, outerContext, factory);
    DexProgramClass clazz =
        classBuilder
            .addMethod(fn.andThen(m -> m.setName(SyntheticNaming.INTERNAL_SYNTHETIC_METHOD_PREFIX)))
            .build();
    ProgramMethod method = new ProgramMethod(clazz, clazz.methods().iterator().next());
    addPendingDefinition(new SyntheticMethodDefinition(kind, outerContext, method));
    return method;
  }

  private void addPendingDefinition(SyntheticDefinition<?, ?, ?> definition) {
    pending.nonLegacyDefinitions.put(definition.getHolder().getType(), definition);
  }

  // Commit of the synthetic items to a new fully populated application.

  public CommittedItems commit(DexApplication application) {
    return commitPrunedItems(PrunedItems.empty(application));
  }

  public CommittedItems commitPrunedItems(PrunedItems prunedItems) {
    return commit(prunedItems, pending, committed, nextSyntheticId);
  }

  public CommittedItems commitRewrittenWithLens(
      DexApplication application, NonIdentityGraphLens lens) {
    assert pending.verifyNotRewritten(lens);
    return commit(
        PrunedItems.empty(application), pending, committed.rewriteWithLens(lens), nextSyntheticId);
  }

  private static CommittedItems commit(
      PrunedItems prunedItems,
      PendingSynthetics pending,
      CommittedSyntheticsCollection committed,
      int nextSyntheticId) {
    DexApplication application = prunedItems.getPrunedApp();
    Set<DexType> removedClasses = prunedItems.getNoLongerSyntheticItems();
    CommittedSyntheticsCollection.Builder builder = committed.builder();
    // Legacy synthetics must already have been committed to the app.
    assert verifyClassesAreInApp(application, pending.legacyClasses.values());
    builder.addLegacyClasses(pending.legacyClasses.values());
    // Compute the synthetic additions and add them to the application.
    ImmutableList<DexType> committedProgramTypes;
    DexApplication amendedApplication;
    if (pending.nonLegacyDefinitions.isEmpty()) {
      committedProgramTypes = ImmutableList.of();
      amendedApplication = application;
    } else {
      DexApplication.Builder<?> appBuilder = application.builder();
      ImmutableList.Builder<DexType> committedProgramTypesBuilder = ImmutableList.builder();
      for (SyntheticDefinition<?, ?, ?> definition : pending.nonLegacyDefinitions.values()) {
        if (!removedClasses.contains(definition.getHolder().getType())) {
          if (definition.isProgramDefinition()) {
            committedProgramTypesBuilder.add(definition.getHolder().getType());
            appBuilder.addProgramClass(definition.asProgramDefinition().getHolder());
          } else {
            assert definition.isClasspathDefinition();
            appBuilder.addClasspathClass(definition.asClasspathDefinition().getHolder());
          }
          builder.addItem(definition);
        }
      }
      committedProgramTypes = committedProgramTypesBuilder.build();
      amendedApplication = appBuilder.build();
    }
    return new CommittedItems(
        nextSyntheticId,
        amendedApplication,
        builder.build().pruneItems(prunedItems),
        committedProgramTypes);
  }

  private static boolean verifyClassesAreInApp(
      DexApplication app, Collection<DexProgramClass> classes) {
    for (DexProgramClass clazz : classes) {
      assert app.programDefinitionFor(clazz.type) != null : "Missing synthetic: " + clazz.type;
    }
    return true;
  }

  // Finalization of synthetic items.

  Result computeFinalSynthetics(AppView<?> appView) {
    assert !hasPendingSyntheticClasses();
    return new SyntheticFinalization(appView.options(), committed).computeFinalSynthetics(appView);
  }
}
