// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.contexts.CompilationContext.UniqueContext;
import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClasspathOrLibraryClass;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.synthesis.SyntheticFinalization.Result;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.ListUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
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
    private final Map<DexType, LegacySyntheticDefinition> legacyClasses = new ConcurrentHashMap<>();

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
      for (LegacySyntheticDefinition legacy : legacyClasses.values()) {
        allPending.add(legacy.getDefinition());
      }
      return Collections.unmodifiableList(allPending);
    }
  }

  private final CommittedSyntheticsCollection committed;

  private final PendingSynthetics pending = new PendingSynthetics();

  // Empty collection for use only in tests and utilities.
  public static SyntheticItems empty() {
    return new SyntheticItems(-1, CommittedSyntheticsCollection.empty());
  }

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

  public static void collectSyntheticInputs(AppView<?> appView) {
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
      SyntheticMarker marker = SyntheticMarker.stripMarkerFromClass(clazz, appView);
      if (marker.isSyntheticMethods()) {
        clazz.forEachProgramMethod(
            // TODO(b/158159959): Support having multiple methods per class.
            method ->
                builder.addNonLegacyMethod(
                    new SyntheticMethodDefinition(marker.getKind(), marker.getContext(), method)));
      } else if (marker.isSyntheticClass()) {
        builder.addNonLegacyClass(
            new SyntheticProgramClassDefinition(marker.getKind(), marker.getContext(), clazz));
      }
    }
    CommittedSyntheticsCollection committed = builder.collectSyntheticInputs().build();
    if (committed.isEmpty()) {
      return;
    }
    CommittedItems commit =
        new CommittedItems(
            synthetics.nextSyntheticId, appView.appInfo().app(), committed, ImmutableList.of());
    if (appView.appInfo().hasClassHierarchy()) {
      appView
          .withClassHierarchy()
          .setAppInfo(appView.appInfo().withClassHierarchy().rebuildWithClassHierarchy(commit));
    } else {
      appView
          .withoutClassHierarchy()
          .setAppInfo(new AppInfo(commit, appView.appInfo().getMainDexInfo()));
    }
  }

  // Predicates and accessors.

  @Override
  public DexClass definitionFor(DexType type, Function<DexType, DexClass> baseDefinitionFor) {
    DexClass clazz = null;
    SyntheticKind kind = null;
    LegacySyntheticDefinition legacyItem = pending.legacyClasses.get(type);
    if (legacyItem != null) {
      clazz = legacyItem.getDefinition();
    } else {
      SyntheticDefinition<?, ?, ?> item = pending.nonLegacyDefinitions.get(type);
      if (item != null) {
        clazz = item.getHolder();
        kind = item.getKind();
        assert clazz.isProgramClass() == item.isProgramDefinition();
        assert clazz.isClasspathClass() == item.isClasspathDefinition();
      }
    }
    if (clazz != null) {
      assert legacyItem != null || kind != null;
      assert baseDefinitionFor.apply(type) == null
              || (kind != null && kind.mayOverridesNonProgramType)
          : "Pending synthetic definition also present in the active program: " + type;
      return clazz;
    }
    return baseDefinitionFor.apply(type);
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

  public boolean isEligibleForClassMerging(DexProgramClass clazz) {
    assert isSyntheticClass(clazz);
    return isSyntheticLambda(clazz);
  }

  // TODO(b/186211926): Allow merging of legacy synthetics.
  private boolean isSyntheticLambda(DexProgramClass clazz) {
    if (!isNonLegacySynthetic(clazz)) {
      return false;
    }
    Iterable<SyntheticReference<?, ?, ?>> references = committed.getNonLegacyItems(clazz.getType());
    if (!Iterables.isEmpty(references)) {
      assert Iterables.size(references) == 1;
      return references.iterator().next().getKind() == SyntheticKind.LAMBDA;
    }
    SyntheticDefinition<?, ?, ?> definition = pending.nonLegacyDefinitions.get(clazz.getType());
    if (definition != null) {
      return definition.getKind() == SyntheticKind.LAMBDA;
    }
    assert false;
    return false;
  }

  public boolean isSubjectToKeepRules(DexProgramClass clazz) {
    assert isSyntheticClass(clazz);
    return isSyntheticInput(clazz);
  }

  public boolean isSyntheticClass(DexType type) {
    return isLegacySyntheticClass(type) || isNonLegacySynthetic(type);
  }

  public boolean isSyntheticClass(DexProgramClass clazz) {
    return isSyntheticClass(clazz.type);
  }

  boolean isSyntheticInput(DexProgramClass clazz) {
    return committed.containsSyntheticInput(clazz.getType());
  }

  public FeatureSplit getContextualFeatureSplit(DexType type) {
    if (pending.legacyClasses.containsKey(type)) {
      LegacySyntheticDefinition definition = pending.legacyClasses.get(type);
      return definition.getFeatureSplit();
    }
    if (committed.containsLegacyType(type)) {
      List<LegacySyntheticReference> types = committed.getLegacyTypes(type);
      if (types.isEmpty()) {
        return null;
      }
      assert verifyAllHaveSameFeature(types, LegacySyntheticReference::getFeatureSplit);
      return types.get(0).getFeatureSplit();
    }
    List<SynthesizingContext> contexts = getSynthesizingContexts(type);
    if (contexts.isEmpty()) {
      return null;
    }
    assert verifyAllHaveSameFeature(contexts, SynthesizingContext::getFeatureSplit);
    return contexts.get(0).getFeatureSplit();
  }

  private static <T> boolean verifyAllHaveSameFeature(
      List<T> items, Function<T, FeatureSplit> getter) {
    assert !items.isEmpty();
    FeatureSplit featureSplit = getter.apply(items.get(0));
    for (int i = 1; i < items.size(); i++) {
      assert featureSplit == getter.apply(items.get(i));
    }
    return true;
  }

  private void forEachSynthesizingContext(DexType type, Consumer<SynthesizingContext> consumer) {
    for (SyntheticReference<?, ?, ?> reference : committed.getNonLegacyItems(type)) {
      consumer.accept(reference.getContext());
    }
    SyntheticDefinition<?, ?, ?> definition = pending.nonLegacyDefinitions.get(type);
    if (definition != null) {
      consumer.accept(definition.getContext());
    }
  }

  private List<SynthesizingContext> getSynthesizingContexts(DexType type) {
    return ListUtils.newImmutableList(builder -> forEachSynthesizingContext(type, builder));
  }

  public Collection<DexType> getSynthesizingContextTypes(DexType type) {
    ImmutableList.Builder<DexType> builder = ImmutableList.builder();
    forEachSynthesizingContext(
        type, synthesizingContext -> builder.add(synthesizingContext.getSynthesizingContextType()));
    for (LegacySyntheticReference legacyReference : committed.getLegacyTypes(type)) {
      builder.addAll(legacyReference.getContexts());
    }
    LegacySyntheticDefinition legacyDefinition = pending.legacyClasses.get(type);
    if (legacyDefinition != null) {
      builder.addAll(legacyDefinition.getContexts());
    }
    return builder.build();
  }

  // TODO(b/180091213): Implement this and remove client provided the oracle.
  public Set<DexReference> getSynthesizingContextReferences(
      DexProgramClass clazz, SynthesizingContextOracle oracle) {
    assert isSyntheticClass(clazz);
    return oracle.getSynthesizingContexts(clazz);
  }

  public interface SynthesizingContextOracle {

    Set<DexReference> getSynthesizingContexts(DexProgramClass clazz);
  }

  // The compiler should not inspect the kind of a synthetic, so this provided only as a assertion
  // utility.
  public boolean verifySyntheticLambdaProperty(
      DexProgramClass clazz,
      Predicate<DexProgramClass> ifIsLambda,
      Predicate<DexProgramClass> ifNotLambda) {
    Iterable<SyntheticReference<?, ?, ?>> references = committed.getNonLegacyItems(clazz.getType());
    SyntheticDefinition<?, ?, ?> definition = pending.nonLegacyDefinitions.get(clazz.getType());
    if (definition != null) {
      references = Iterables.concat(references, IterableUtils.singleton(definition.toReference()));
    }
    if (Iterables.any(references, reference -> reference.getKind() == SyntheticKind.LAMBDA)) {
      assert ifIsLambda.test(clazz);
    } else {
      assert ifNotLambda.test(clazz);
    }
    return true;
  }

  public Collection<DexProgramClass> getLegacyPendingClasses() {
    return ListUtils.map(pending.legacyClasses.values(), LegacySyntheticDefinition::getDefinition);
  }

  private SynthesizingContext getSynthesizingContext(
      ProgramDefinition context, AppView<?> appView) {
    return getSynthesizingContext(
        context, appView.appInfoForDesugaring().getClassToFeatureSplitMap());
  }

  /** Used to find the synthesizing context for a new synthetic that is about to be created. */
  private SynthesizingContext getSynthesizingContext(
      ProgramDefinition context, ClassToFeatureSplitMap featureSplits) {
    DexType contextType = context.getContextType();
    SyntheticDefinition<?, ?, ?> existingDefinition = pending.nonLegacyDefinitions.get(contextType);
    if (existingDefinition != null) {
      return existingDefinition.getContext();
    }
    Iterable<SyntheticReference<?, ?, ?>> existingReferences =
        committed.getNonLegacyItems(contextType);
    if (!Iterables.isEmpty(existingReferences)) {
      // Use a deterministic synthesizing context from the set of contexts.
      return IterableUtils.min(
              existingReferences,
              (existingReference, other) ->
                  existingReference.getReference().compareTo(other.getReference()))
          .getContext();
    }
    // This context is not nested in an existing synthetic context so create a new "leaf" context.
    FeatureSplit featureSplit = featureSplits.getFeatureSplit(context, this);
    return SynthesizingContext.fromNonSyntheticInputContext(context, featureSplit);
  }

  // Addition and creation of synthetic items.

  public void addLegacySyntheticClassForLibraryDesugaring(DexProgramClass clazz) {
    internalAddLegacySyntheticClass(clazz);
    // No context information is added for library context.
    // This is intended only to support desugared-library compilation.
  }

  // TODO(b/158159959): Remove the usage of this direct class addition.
  public void addLegacySyntheticClass(
      DexProgramClass clazz, ProgramDefinition context, FeatureSplit featureSplit) {
    LegacySyntheticDefinition legacyItem = internalAddLegacySyntheticClass(clazz);
    legacyItem.addContext(context, featureSplit);
  }

  private LegacySyntheticDefinition internalAddLegacySyntheticClass(DexProgramClass clazz) {
    assert !isCommittedSynthetic(clazz.type);
    assert !pending.nonLegacyDefinitions.containsKey(clazz.type);
    LegacySyntheticDefinition legacyItem =
        pending.legacyClasses.computeIfAbsent(
            clazz.getType(), type -> new LegacySyntheticDefinition(clazz));
    assert legacyItem.getDefinition() == clazz;
    return legacyItem;
  }

  public DexProgramClass createClass(
      SyntheticKind kind,
      UniqueContext context,
      AppView<?> appView,
      Consumer<SyntheticProgramClassBuilder> fn) {
    // Obtain the outer synthesizing context in the case the context itself is synthetic.
    // This is to ensure a flat input-type -> synthetic-item mapping.
    SynthesizingContext outerContext = getSynthesizingContext(context.getClassContext(), appView);
    DexType type =
        SyntheticNaming.createInternalType(
            kind, outerContext, context.getSyntheticSuffix(), appView.dexItemFactory());
    SyntheticProgramClassBuilder classBuilder =
        new SyntheticProgramClassBuilder(type, outerContext, appView.dexItemFactory());
    fn.accept(classBuilder);
    DexProgramClass clazz = classBuilder.build();
    addPendingDefinition(new SyntheticProgramClassDefinition(kind, outerContext, clazz));
    return clazz;
  }

  // TODO(b/172194101): Make this take a unique context.
  public DexProgramClass createFixedClass(
      SyntheticKind kind,
      DexProgramClass context,
      AppView<?> appView,
      Consumer<SyntheticProgramClassBuilder> fn) {
    // Obtain the outer synthesizing context in the case the context itself is synthetic.
    // This is to ensure a flat input-type -> synthetic-item mapping.
    SynthesizingContext outerContext = getSynthesizingContext(context, appView);
    DexType type = SyntheticNaming.createFixedType(kind, outerContext, appView.dexItemFactory());
    SyntheticProgramClassBuilder classBuilder =
        new SyntheticProgramClassBuilder(type, outerContext, appView.dexItemFactory());
    fn.accept(classBuilder);
    DexProgramClass clazz = classBuilder.build();
    addPendingDefinition(new SyntheticProgramClassDefinition(kind, outerContext, clazz));
    return clazz;
  }

  // This is a temporary API for migration to the hygienic synthetic, the classes created behave
  // like a hygienic synthetic, but use the legacyType passed as parameter instead of the
  // hygienic type.
  public DexProgramClass ensureFixedClassWhileMigrating(
      SyntheticKind kind,
      DexType legacyType,
      DexProgramClass context,
      AppView<?> appView,
      Consumer<SyntheticProgramClassBuilder> fn) {
    synchronized (context) {
      DexClass dexClass = appView.definitionFor(legacyType);
      if (dexClass != null) {
        assert dexClass.isProgramClass();
        return dexClass.asProgramClass();
      }
      // Obtain the outer synthesizing context in the case the context itself is synthetic.
      // This is to ensure a flat input-type -> synthetic-item mapping.
      SynthesizingContext outerContext = getSynthesizingContext(context, appView);
      SyntheticProgramClassBuilder classBuilder =
          new SyntheticProgramClassBuilder(legacyType, outerContext, appView.dexItemFactory());
      fn.accept(classBuilder);
      DexProgramClass clazz = classBuilder.build();
      addPendingDefinition(new SyntheticProgramClassDefinition(kind, outerContext, clazz));
      return clazz;
    }
  }

  public DexClasspathClass createFixedClasspathClass(
      SyntheticKind kind, DexClasspathClass context, DexItemFactory factory) {
    // Obtain the outer synthesizing context in the case the context itself is synthetic.
    // This is to ensure a flat input-type -> synthetic-item mapping.
    SynthesizingContext outerContext = SynthesizingContext.fromNonSyntheticInputContext(context);
    DexType type = SyntheticNaming.createFixedType(kind, outerContext, factory);
    SyntheticClasspathClassBuilder classBuilder =
        new SyntheticClasspathClassBuilder(type, outerContext, factory);
    DexClasspathClass clazz = classBuilder.build();
    addPendingDefinition(new SyntheticClasspathClassDefinition(kind, outerContext, clazz));
    return clazz;
  }

  // This is a temporary API for migration to the hygienic synthetic, the classes created behave
  // like a hygienic synthetic, but use the legacyType passed as parameter instead of the
  // hygienic type.
  private DexClasspathClass ensureFixedClasspathClassWhileMigrating(
      SyntheticKind kind, DexType legacyType, ClasspathOrLibraryClass context, AppView<?> appView) {
    synchronized (context) {
      DexClass dexClass = appView.definitionFor(legacyType);
      if (dexClass != null) {
        assert dexClass.isClasspathClass();
        return dexClass.asClasspathClass();
      }
      // Obtain the outer synthesizing context in the case the context itself is synthetic.
      // This is to ensure a flat input-type -> synthetic-item mapping.
      SynthesizingContext outerContext = SynthesizingContext.fromNonSyntheticInputContext(context);
      SyntheticClasspathClassBuilder classBuilder =
          new SyntheticClasspathClassBuilder(legacyType, outerContext, appView.dexItemFactory());
      DexClasspathClass clazz = classBuilder.build();
      addPendingDefinition(new SyntheticClasspathClassDefinition(kind, outerContext, clazz));
      return clazz;
    }
  }

  // This is a temporary API for migration to the hygienic synthetic, the classes created behave
  // like a hygienic synthetic, but use the legacyType passed as parameter instead of the
  // hygienic type.
  public void ensureDirectMethodOnSyntheticClasspathClassWhileMigrating(
      SyntheticKind kind,
      DexType legacyType,
      ClasspathOrLibraryClass context,
      AppView<?> appView,
      DexMethod method,
      Consumer<SyntheticMethodBuilder> builderConsumer) {
    DexClasspathClass syntheticClass =
        ensureFixedClasspathClassWhileMigrating(kind, legacyType, context, appView);
    synchronized (syntheticClass) {
      if (syntheticClass.lookupMethod(method) != null) {
        return;
      }
      SyntheticMethodBuilder syntheticMethodBuilder =
          new SyntheticMethodBuilder(appView.dexItemFactory(), syntheticClass.type);
      builderConsumer.accept(syntheticMethodBuilder);
      syntheticClass.addDirectMethod(syntheticMethodBuilder.build());
    }
  }

  public DexProgramClass createFixedClassFromType(
      SyntheticKind kind,
      DexType contextType,
      DexItemFactory factory,
      Consumer<SyntheticProgramClassBuilder> fn) {
    // Obtain the outer synthesizing context in the case the context itself is synthetic.
    // This is to ensure a flat input-type -> synthetic-item mapping.
    SynthesizingContext outerContext = SynthesizingContext.fromType(contextType);
    DexType type = SyntheticNaming.createFixedType(kind, outerContext, factory);
    SyntheticProgramClassBuilder classBuilder =
        new SyntheticProgramClassBuilder(type, outerContext, factory);
    fn.accept(classBuilder);
    DexProgramClass clazz = classBuilder.build();
    addPendingDefinition(new SyntheticProgramClassDefinition(kind, outerContext, clazz));
    return clazz;
  }

  /** Create a single synthetic method item. */
  public ProgramMethod createMethod(
      SyntheticKind kind,
      UniqueContext context,
      AppView<?> appView,
      Consumer<SyntheticMethodBuilder> fn) {
    return createMethod(kind, context.getClassContext(), appView, fn, context::getSyntheticSuffix);
  }

  private ProgramMethod createMethod(
      SyntheticKind kind,
      ProgramDefinition context,
      AppView<?> appView,
      Consumer<SyntheticMethodBuilder> fn,
      Supplier<String> syntheticIdSupplier) {
    assert nextSyntheticId != INVALID_ID_AFTER_SYNTHETIC_FINALIZATION;
    // Obtain the outer synthesizing context in the case the context itself is synthetic.
    // This is to ensure a flat input-type -> synthetic-item mapping.
    SynthesizingContext outerContext = getSynthesizingContext(context, appView);
    DexType type =
        SyntheticNaming.createInternalType(
            kind, outerContext, syntheticIdSupplier.get(), appView.dexItemFactory());
    SyntheticProgramClassBuilder classBuilder =
        new SyntheticProgramClassBuilder(type, outerContext, appView.dexItemFactory());
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
    builder.addLegacyClasses(pending.legacyClasses);
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
            if (definition.getKind().mayOverridesNonProgramType) {
              appBuilder.addProgramClassPotentiallyOverridingNonProgramClass(
                  definition.asProgramDefinition().getHolder());
            } else {
              appBuilder.addProgramClass(definition.asProgramDefinition().getHolder());
            }
          } else if (appBuilder.isDirect()) {
            assert definition.isClasspathDefinition();
            appBuilder.asDirect().addClasspathClass(definition.asClasspathDefinition().getHolder());
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
      DexApplication app, Collection<LegacySyntheticDefinition> classes) {
    for (LegacySyntheticDefinition item : classes) {
      DexProgramClass clazz = item.getDefinition();
      assert app.programDefinitionFor(clazz.type) != null : "Missing synthetic: " + clazz.type;
    }
    return true;
  }

  // Finalization of synthetic items.

  Result computeFinalSynthetics(AppView<?> appView) {
    assert !hasPendingSyntheticClasses();
    return new SyntheticFinalization(appView.options(), this, committed)
        .computeFinalSynthetics(appView);
  }
}
