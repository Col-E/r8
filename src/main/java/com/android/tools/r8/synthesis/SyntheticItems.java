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
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.graph.MethodCollection;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger;
import com.android.tools.r8.synthesis.SyntheticFinalization.Result;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.objectweb.asm.ClassWriter;

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
    CommittedSyntheticsCollection.Builder builder = synthetics.committed.builder();
    // TODO(b/158159959): Consider populating the input synthetics when identified.
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      SyntheticMarker marker = SyntheticMarker.stripMarkerFromClass(clazz, appView);
      if (!appView.options().intermediate && marker.getContext() != null) {
        DexClass contextClass =
            appView
                .appInfo()
                .definitionForWithoutExistenceAssert(
                    marker.getContext().getSynthesizingContextType());
        if (contextClass == null || contextClass.isNotProgramClass()) {
          appView
              .reporter()
              .error(
                  new StringDiagnostic(
                      "Attempt at compiling intermediate artifact without its context",
                      clazz.getOrigin()));
        }
      }
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

  public boolean isFinalized() {
    return nextSyntheticId == INVALID_ID_AFTER_SYNTHETIC_FINALIZATION;
  }

  public boolean hasPendingSyntheticClasses() {
    return !pending.isEmpty();
  }

  public Collection<DexProgramClass> getPendingSyntheticClasses() {
    return pending.getAllProgramClasses();
  }

  public boolean isCommittedSynthetic(DexType type) {
    return committed.containsType(type);
  }

  private boolean isLegacyCommittedSynthetic(DexType type) {
    return committed.containsLegacyType(type);
  }

  private boolean isNonLegacyCommittedSynthetic(DexType type) {
    return committed.containsNonLegacyType(type);
  }

  public boolean isPendingSynthetic(DexType type) {
    return pending.containsType(type);
  }

  private boolean isLegacyPendingSynthetic(DexType type) {
    return pending.legacyClasses.containsKey(type);
  }

  private boolean isNonLegacyPendingSynthetic(DexType type) {
    return pending.nonLegacyDefinitions.containsKey(type);
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
    return isNonLegacyCommittedSynthetic(type) || isNonLegacyPendingSynthetic(type);
  }

  public boolean isEligibleForClassMerging(DexProgramClass clazz, HorizontalClassMerger.Mode mode) {
    assert isSyntheticClass(clazz);
    return mode.isFinal() || isSyntheticLambda(clazz);
  }

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

  public boolean isSyntheticMethodThatShouldNotBeDoubleProcessed(ProgramMethod method) {
    for (SyntheticMethodReference reference :
        committed
            .getNonLegacyMethods()
            .getOrDefault(method.getHolderType(), Collections.emptyList())) {
      if (reference.getKind() == SyntheticKind.STATIC_INTERFACE_CALL) {
        return true;
      }
    }
    SyntheticDefinition<?, ?, ?> definition =
        pending.nonLegacyDefinitions.get(method.getHolderType());
    if (definition != null) {
      return definition.getKind() == SyntheticKind.STATIC_INTERFACE_CALL;
    }
    return false;
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

  private DexProgramClass internalCreateClass(
      SyntheticKind kind,
      Consumer<SyntheticProgramClassBuilder> fn,
      SynthesizingContext outerContext,
      DexType type,
      DexItemFactory factory) {
    SyntheticProgramClassBuilder classBuilder =
        new SyntheticProgramClassBuilder(type, kind, outerContext, factory);
    fn.accept(classBuilder);
    DexProgramClass clazz = classBuilder.build();
    addPendingDefinition(new SyntheticProgramClassDefinition(kind, outerContext, clazz));
    return clazz;
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
    return internalCreateClass(kind, fn, outerContext, type, appView.dexItemFactory());
  }

  // TODO(b/172194101): Make this take a unique context.
  public DexProgramClass createFixedClass(
      SyntheticKind kind,
      DexProgramClass context,
      AppView<?> appView,
      Consumer<SyntheticProgramClassBuilder> fn) {
    // Obtain the outer synthesizing context in the case the context itself is synthetic.
    // This is to ensure a flat input-type -> synthetic-item mapping.
    SynthesizingContext outerContext =
        context.isProgramClass()
            ? getSynthesizingContext(context.asProgramClass(), appView)
            : SynthesizingContext.fromNonSyntheticInputContext(context.asClasspathOrLibraryClass());
    DexType type = SyntheticNaming.createFixedType(kind, outerContext, appView.dexItemFactory());
    return internalCreateClass(kind, fn, outerContext, type, appView.dexItemFactory());
  }

  /**
   * Ensure that a fixed synthetic class exists.
   *
   * <p>This method is thread safe and will synchronize based on the context of the fixed synthetic.
   */
  public DexProgramClass ensureFixedClass(
      SyntheticKind kind,
      DexClass context,
      AppView<?> appView,
      Consumer<SyntheticProgramClassBuilder> fn,
      Consumer<DexProgramClass> onCreationConsumer) {
    assert kind.isFixedSuffixSynthetic;
    // Obtain the outer synthesizing context in the case the context itself is synthetic.
    // This is to ensure a flat input-type -> synthetic-item mapping.
    SynthesizingContext outerContext =
        context.isProgramClass()
            ? getSynthesizingContext(context.asProgramClass(), appView)
            : SynthesizingContext.fromNonSyntheticInputContext(context.asClasspathOrLibraryClass());
    DexType type = SyntheticNaming.createFixedType(kind, outerContext, appView.dexItemFactory());
    // Fast path is that the synthetic is already present. If so it must be a program class.
    DexClass clazz = appView.definitionFor(type);
    if (clazz != null) {
      assert isSyntheticClass(type);
      assert clazz.isProgramClass();
      return clazz.asProgramClass();
    }
    // Slow path creates the class using the context to make it thread safe.
    synchronized (context) {
      // Recheck if it is present now the lock is held.
      clazz = appView.definitionFor(type);
      if (clazz != null) {
        assert isSyntheticClass(type);
        assert clazz.isProgramClass();
        return clazz.asProgramClass();
      }
      assert !isSyntheticClass(type);
      DexProgramClass dexProgramClass =
          internalCreateClass(
              kind,
              syntheticProgramClassBuilder -> {
                syntheticProgramClassBuilder.setUseSortedMethodBacking(true);
                fn.accept(syntheticProgramClassBuilder);
              },
              outerContext,
              type,
              appView.dexItemFactory());
      onCreationConsumer.accept(dexProgramClass);
      return dexProgramClass;
    }
  }

  public ProgramMethod ensureFixedClassMethod(
      DexString name,
      DexProto proto,
      SyntheticKind kind,
      DexProgramClass context,
      AppView<?> appView,
      Consumer<SyntheticProgramClassBuilder> buildClassCallback,
      Consumer<SyntheticMethodBuilder> buildMethodCallback) {
    DexProgramClass clazz =
        ensureFixedClass(kind, context, appView, buildClassCallback, ignored -> {});
    DexMethod methodReference = appView.dexItemFactory().createMethod(clazz.getType(), proto, name);
    DexEncodedMethod methodDefinition =
        internalEnsureMethod(methodReference, clazz, kind, appView, buildMethodCallback);
    return new ProgramMethod(clazz, methodDefinition);
  }

  public DexClasspathClass createFixedClasspathClass(
      SyntheticKind kind, DexClasspathClass context, DexItemFactory factory) {
    // Obtain the outer synthesizing context in the case the context itself is synthetic.
    // This is to ensure a flat input-type -> synthetic-item mapping.
    SynthesizingContext outerContext = SynthesizingContext.fromNonSyntheticInputContext(context);
    DexType type = SyntheticNaming.createFixedType(kind, outerContext, factory);
    SyntheticClasspathClassBuilder classBuilder =
        new SyntheticClasspathClassBuilder(type, kind, outerContext, factory);
    DexClasspathClass clazz = classBuilder.build();
    addPendingDefinition(new SyntheticClasspathClassDefinition(kind, outerContext, clazz));
    return clazz;
  }

  public DexClasspathClass ensureFixedClasspathClass(
      SyntheticKind kind,
      ClasspathOrLibraryClass context,
      AppView<?> appView,
      Consumer<SyntheticClasspathClassBuilder> classConsumer,
      Consumer<DexClasspathClass> onCreationConsumer) {
    SynthesizingContext outerContext = SynthesizingContext.fromNonSyntheticInputContext(context);
    DexType type = SyntheticNaming.createFixedType(kind, outerContext, appView.dexItemFactory());
    DexClass dexClass = appView.appInfo().definitionForWithoutExistenceAssert(type);
    if (dexClass != null) {
      assert dexClass.isClasspathClass();
      return dexClass.asClasspathClass();
    }
    synchronized (context) {
      dexClass = appView.appInfo().definitionForWithoutExistenceAssert(type);
      if (dexClass != null) {
        assert dexClass.isClasspathClass();
        return dexClass.asClasspathClass();
      }
      // Obtain the outer synthesizing context in the case the context itself is synthetic.
      // This is to ensure a flat input-type -> synthetic-item mapping.
      SyntheticClasspathClassBuilder classBuilder =
          new SyntheticClasspathClassBuilder(type, kind, outerContext, appView.dexItemFactory());
      classConsumer.accept(classBuilder);
      DexClasspathClass clazz = classBuilder.build();
      onCreationConsumer.accept(clazz);
      addPendingDefinition(new SyntheticClasspathClassDefinition(kind, outerContext, clazz));
      return clazz;
    }
  }

  public DexClassAndMethod ensureFixedClasspathClassMethod(
      DexString methodName,
      DexProto methodProto,
      SyntheticKind kind,
      ClasspathOrLibraryClass context,
      AppView<?> appView,
      Consumer<SyntheticClasspathClassBuilder> buildClassCallback,
      Consumer<SyntheticMethodBuilder> buildMethodCallback) {
    DexClasspathClass clazz =
        ensureFixedClasspathClass(kind, context, appView, buildClassCallback, ignored -> {});
    DexMethod methodReference =
        appView.dexItemFactory().createMethod(clazz.getType(), methodProto, methodName);
    DexEncodedMethod methodDefinition =
        internalEnsureMethod(methodReference, clazz, kind, appView, buildMethodCallback);
    return DexClassAndMethod.create(clazz, methodDefinition);
  }

  private DexEncodedMethod internalEnsureMethod(
      DexMethod methodReference,
      DexClass clazz,
      SyntheticKind kind,
      AppView<?> appView,
      Consumer<SyntheticMethodBuilder> buildMethodCallback) {
    MethodCollection methodCollection = clazz.getMethodCollection();
    DexEncodedMethod methodDefinition = methodCollection.getMethod(methodReference);
    if (methodDefinition != null) {
      return methodDefinition;
    }
    synchronized (methodCollection) {
      methodDefinition = methodCollection.getMethod(methodReference);
      if (methodDefinition != null) {
        return methodDefinition;
      }
      SyntheticMethodBuilder builder =
          new SyntheticMethodBuilder(appView.dexItemFactory(), clazz.getType(), kind);
      builder.setName(methodReference.getName());
      builder.setProto(methodReference.getProto());
      buildMethodCallback.accept(builder);
      // TODO(b/183998768): Make this safe for recursive definitions.
      //  For example, the builder should be split into the creation of the method structure
      //  and the creation of the method code. The code can then be constructed outside the lock.
      methodDefinition = builder.build();
      methodCollection.addMethod(methodDefinition);
      return methodDefinition;
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
    return internalCreateClass(kind, fn, outerContext, type, factory);
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
        new SyntheticProgramClassBuilder(type, kind, outerContext, appView.dexItemFactory());
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

  public void writeAttributeIfIntermediateSyntheticClass(
      ClassWriter writer, DexProgramClass clazz, AppView<?> appView) {
    if (!appView.options().intermediate || !appView.options().isGeneratingClassFiles()) {
      return;
    }
    Iterator<SyntheticReference<?, ?, ?>> it =
        committed.getNonLegacyItems(clazz.getType()).iterator();
    if (it.hasNext()) {
      SyntheticKind kind = it.next().getKind();
      // When compiling intermediates there should not be any mergings as they may invalidate the
      // single kind of a synthetic which is required for marking synthetics. This check could be
      // relaxed to ensure that all kinds are equivalent if merging is possible.
      assert !it.hasNext();
      SyntheticMarker.writeMarkerAttribute(writer, kind);
    }
  }

  // Finalization of synthetic items.

  Result computeFinalSynthetics(AppView<?> appView) {
    assert !hasPendingSyntheticClasses();
    return new SyntheticFinalization(appView.options(), this, committed)
        .computeFinalSynthetics(appView);
  }
}
