// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.contexts.CompilationContext.UniqueContext;
import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassResolutionResult;
import com.android.tools.r8.graph.ClasspathMethod;
import com.android.tools.r8.graph.ClasspathOrLibraryClass;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexEncodedMethod;
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
import com.android.tools.r8.utils.ConsumerUtils;
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

    /** Thread safe collection of synthetic items not yet committed to the application. */
    private final ConcurrentHashMap<DexType, SyntheticDefinition<?, ?, ?>> nonLegacyDefinitions =
        new ConcurrentHashMap<>();

    boolean isEmpty() {
      return nonLegacyDefinitions.isEmpty();
    }

    boolean containsType(DexType type) {
      return nonLegacyDefinitions.containsKey(type);
    }

    boolean containsTypeOfKind(DexType type, SyntheticKind kind) {
      SyntheticDefinition<?, ?, ?> definition = nonLegacyDefinitions.get(type);
      return definition != null && definition.getKind() == kind;
    }

    boolean verifyNotRewritten(NonIdentityGraphLens lens) {
      assert nonLegacyDefinitions.keySet().equals(lens.rewriteTypes(nonLegacyDefinitions.keySet()));
      return true;
    }

    Collection<DexProgramClass> getAllProgramClasses() {
      List<DexProgramClass> allPending = new ArrayList<>(nonLegacyDefinitions.size());
      for (SyntheticDefinition<?, ?, ?> item : nonLegacyDefinitions.values()) {
        if (item.isProgramDefinition()) {
          allPending.add(item.asProgramDefinition().getHolder());
        }
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
  public ClassResolutionResult definitionFor(
      DexType type, Function<DexType, ClassResolutionResult> baseDefinitionFor) {
    DexClass clazz = null;
    SyntheticKind kind = null;
    SyntheticDefinition<?, ?, ?> item = pending.nonLegacyDefinitions.get(type);
    if (item != null) {
      clazz = item.getHolder();
      kind = item.getKind();
      assert clazz.isProgramClass() == item.isProgramDefinition();
      assert clazz.isClasspathClass() == item.isClasspathDefinition();
    }
    if (clazz != null) {
      assert kind != null;
      assert !baseDefinitionFor.apply(type).hasClassResolutionResult()
              || kind.mayOverridesNonProgramType
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

  private boolean isNonLegacyCommittedSynthetic(DexType type) {
    return committed.containsNonLegacyType(type);
  }

  public boolean isPendingSynthetic(DexType type) {
    return pending.containsType(type);
  }

  private boolean isNonLegacyPendingSynthetic(DexType type) {
    return pending.nonLegacyDefinitions.containsKey(type);
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
    return isNonLegacySynthetic(type);
  }

  public boolean isSyntheticClass(DexProgramClass clazz) {
    return isSyntheticClass(clazz.type);
  }

  public boolean isSyntheticOfKind(DexType type, SyntheticKind kind) {
    return pending.containsTypeOfKind(type, kind) || committed.containsTypeOfKind(type, kind);
  }

  boolean isSyntheticInput(DexProgramClass clazz) {
    return committed.containsSyntheticInput(clazz.getType());
  }

  public FeatureSplit getContextualFeatureSplit(
      DexType type, ClassToFeatureSplitMap classToFeatureSplitMap) {
    if (isSyntheticOfKind(type, SyntheticKind.ENUM_UNBOXING_SHARED_UTILITY_CLASS)) {
      // Use the startup base if there is one, such that we don't merge non-startup classes with the
      // shared utility class in case it is used during startup. The use of base startup allows for
      // merging startup classes with the shared utility class, however, which could be bad for
      // startup if the shared utility class is not used during startup.
      return classToFeatureSplitMap.getBaseStartup();
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

  private DexProgramClass internalLookupProgramClass(
      DexType type, SyntheticKind kind, AppView<?> appView) {
    DexClass clazz = appView.definitionFor(type);
    if (clazz == null) {
      return null;
    }
    if (clazz.isProgramClass()) {
      return clazz.asProgramClass();
    }
    if (clazz.isLibraryClass() && kind.isGlobal()) {
      return null;
    }
    errorOnInvalidSyntheticEnsure(clazz, "program", appView);
    return null;
  }

  private DexProgramClass internalEnsureFixedProgramClass(
      SyntheticKind kind,
      Consumer<SyntheticProgramClassBuilder> classConsumer,
      Consumer<DexProgramClass> onCreationConsumer,
      SynthesizingContext outerContext,
      AppView<?> appView) {
    Function<SynthesizingContext, DexType> contextToType =
        c -> SyntheticNaming.createFixedType(kind, c, appView.dexItemFactory());
    DexType type = contextToType.apply(outerContext);
    // Fast path is that the synthetic is already present. If so it must be a program class.
    DexProgramClass clazz = internalLookupProgramClass(type, kind, appView);
    if (clazz != null) {
      return clazz;
    }
    // Slow path creates the class using the context to make it thread safe.
    synchronized (type) {
      // Recheck if it is present now the lock is held.
      clazz = internalLookupProgramClass(type, kind, appView);
      if (clazz != null) {
        return clazz;
      }
      assert !isSyntheticClass(type);
      clazz =
          internalCreateProgramClass(
              kind,
              syntheticProgramClassBuilder -> {
                syntheticProgramClassBuilder.setUseSortedMethodBacking(true);
                classConsumer.accept(syntheticProgramClassBuilder);
              },
              outerContext,
              type,
              contextToType,
              appView);
      onCreationConsumer.accept(clazz);
      return clazz;
    }
  }

  private DexProgramClass internalCreateProgramClass(
      SyntheticKind kind,
      Consumer<SyntheticProgramClassBuilder> fn,
      SynthesizingContext outerContext,
      DexType type,
      Function<SynthesizingContext, DexType> contextToType,
      AppView<?> appView) {
    registerSyntheticTypeRewriting(outerContext, contextToType, appView, type);
    SyntheticProgramClassBuilder classBuilder =
        new SyntheticProgramClassBuilder(type, kind, outerContext, appView.dexItemFactory());
    fn.accept(classBuilder);
    DexProgramClass clazz = classBuilder.build();
    addPendingDefinition(new SyntheticProgramClassDefinition(kind, outerContext, clazz));
    return clazz;
  }

  private void registerSyntheticTypeRewriting(
      SynthesizingContext outerContext,
      Function<SynthesizingContext, DexType> contextToType,
      AppView<?> appView,
      DexType type) {
    DexType rewrittenContextType =
        appView.typeRewriter.rewrittenContextType(outerContext.getSynthesizingContextType());
    if (rewrittenContextType == null) {
      return;
    }
    SynthesizingContext synthesizingContext = SynthesizingContext.fromType(rewrittenContextType);
    DexType rewrittenType = contextToType.apply(synthesizingContext);
    appView.typeRewriter.rewriteType(type, rewrittenType);
  }

  public DexProgramClass createClass(
      SyntheticKind kind, UniqueContext context, AppView<?> appView) {
    return createClass(kind, context, appView, ConsumerUtils.emptyConsumer());
  }

  public DexProgramClass createClass(
      SyntheticKind kind,
      UniqueContext context,
      AppView<?> appView,
      Consumer<SyntheticProgramClassBuilder> fn) {
    // Obtain the outer synthesizing context in the case the context itself is synthetic.
    // This is to ensure a flat input-type -> synthetic-item mapping.
    SynthesizingContext outerContext = getSynthesizingContext(context.getClassContext(), appView);
    Function<SynthesizingContext, DexType> contextToType =
        c ->
            SyntheticNaming.createInternalType(
                kind, c, context.getSyntheticSuffix(), appView.dexItemFactory());
    return internalCreateProgramClass(
        kind, fn, outerContext, contextToType.apply(outerContext), contextToType, appView);
  }

  // TODO(b/172194101): Make this take a unique context.
  public DexProgramClass createFixedClass(
      SyntheticKind kind,
      DexProgramClass context,
      AppView<?> appView,
      Consumer<SyntheticProgramClassBuilder> fn) {
    SynthesizingContext outerContext = internalGetOuterContext(context, appView);
    Function<SynthesizingContext, DexType> contextToType =
        c -> SyntheticNaming.createFixedType(kind, c, appView.dexItemFactory());
    return internalCreateProgramClass(
        kind, fn, outerContext, contextToType.apply(outerContext), contextToType, appView);
  }

  public DexProgramClass getExistingFixedClass(
      SyntheticKind kind, DexClass context, AppView<?> appView) {
    assert kind.isFixedSuffixSynthetic;
    SynthesizingContext outerContext = internalGetOuterContext(context, appView);
    DexType type = SyntheticNaming.createFixedType(kind, outerContext, appView.dexItemFactory());
    DexClass clazz = appView.definitionFor(type);
    assert clazz != null;
    assert isSyntheticClass(type);
    assert clazz.isProgramClass();
    return clazz.asProgramClass();
  }

  // Obtain the outer synthesizing context in the case the context itself is synthetic.
  // This is to ensure a flat input-type -> synthetic-item mapping.
  private SynthesizingContext internalGetOuterContext(DexClass context, AppView<?> appView) {
    return context.isProgramClass()
        ? getSynthesizingContext(context.asProgramClass(), appView)
        : SynthesizingContext.fromNonSyntheticInputContext(context.asClasspathOrLibraryClass());
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
    SynthesizingContext outerContext = internalGetOuterContext(context, appView);
    return internalEnsureFixedProgramClass(kind, fn, onCreationConsumer, outerContext, appView);
  }

  public ProgramMethod ensureFixedClassMethod(
      DexString name,
      DexProto proto,
      SyntheticKind kind,
      ProgramDefinition context,
      AppView<?> appView,
      Consumer<SyntheticProgramClassBuilder> buildClassCallback,
      Consumer<SyntheticMethodBuilder> buildMethodCallback) {
    return ensureFixedClassMethod(
        name,
        proto,
        kind,
        context,
        appView,
        buildClassCallback,
        buildMethodCallback,
        emptyConsumer());
  }

  public ProgramMethod ensureFixedClassMethod(
      DexString name,
      DexProto proto,
      SyntheticKind kind,
      ProgramDefinition context,
      AppView<?> appView,
      Consumer<SyntheticProgramClassBuilder> buildClassCallback,
      Consumer<SyntheticMethodBuilder> buildMethodCallback,
      Consumer<ProgramMethod> newMethodCallback) {
    DexProgramClass clazz =
        ensureFixedClass(
            kind, context.getContextClass(), appView, buildClassCallback, emptyConsumer());
    DexMethod methodReference = appView.dexItemFactory().createMethod(clazz.getType(), proto, name);
    DexEncodedMethod methodDefinition =
        internalEnsureMethod(
            methodReference, clazz, kind, appView, buildMethodCallback, newMethodCallback);
    return new ProgramMethod(clazz, methodDefinition);
  }

  private void errorOnInvalidSyntheticEnsure(DexClass dexClass, String kind, AppView<?> appView) {
    String classKind =
        dexClass.isProgramClass()
            ? "program"
            : dexClass.isClasspathClass() ? "classpath" : "library";
    throw appView
        .reporter()
        .fatalError(
            "Cannot ensure "
                + dexClass.type
                + " as a synthetic "
                + kind
                + " class, because it is already a "
                + classKind
                + " class.");
  }

  private DexClasspathClass internalEnsureFixedClasspathClass(
      SyntheticKind kind,
      Consumer<SyntheticClasspathClassBuilder> classConsumer,
      Consumer<DexClasspathClass> onCreationConsumer,
      SynthesizingContext outerContext,
      AppView<?> appView) {
    Function<SynthesizingContext, DexType> contextToType =
        (c) -> SyntheticNaming.createFixedType(kind, c, appView.dexItemFactory());
    DexType type = contextToType.apply(outerContext);
    synchronized (type) {
      DexClass clazz = appView.definitionFor(type);
      if (clazz != null) {
        if (!clazz.isClasspathClass()) {
          errorOnInvalidSyntheticEnsure(clazz, "classpath", appView);
        }
        return clazz.asClasspathClass();
      }
      registerSyntheticTypeRewriting(outerContext, contextToType, appView, type);
      SyntheticClasspathClassBuilder classBuilder =
          new SyntheticClasspathClassBuilder(type, kind, outerContext, appView.dexItemFactory());
      classConsumer.accept(classBuilder);
      DexClasspathClass definition = classBuilder.build();
      addPendingDefinition(new SyntheticClasspathClassDefinition(kind, outerContext, definition));
      onCreationConsumer.accept(definition);
      return definition;
    }
  }

  public DexClasspathClass ensureFixedClasspathClassFromType(
      SyntheticKind kind,
      DexType contextType,
      AppView<?> appView,
      Consumer<SyntheticClasspathClassBuilder> classConsumer,
      Consumer<DexClasspathClass> onCreationConsumer) {
    SynthesizingContext outerContext = SynthesizingContext.fromType(contextType);
    return internalEnsureFixedClasspathClass(
        kind, classConsumer, onCreationConsumer, outerContext, appView);
  }

  public DexClasspathClass ensureFixedClasspathClass(
      SyntheticKind kind,
      ClasspathOrLibraryClass context,
      AppView<?> appView,
      Consumer<SyntheticClasspathClassBuilder> classConsumer,
      Consumer<DexClasspathClass> onCreationConsumer) {
    // Obtain the outer synthesizing context in the case the context itself is synthetic.
    // This is to ensure a flat input-type -> synthetic-item mapping.
    SynthesizingContext outerContext = SynthesizingContext.fromNonSyntheticInputContext(context);
    return internalEnsureFixedClasspathClass(
        kind, classConsumer, onCreationConsumer, outerContext, appView);
  }

  public ClasspathMethod ensureFixedClasspathMethodFromType(
      DexString methodName,
      DexProto methodProto,
      SyntheticKind kind,
      DexType contextType,
      AppView<?> appView,
      Consumer<SyntheticClasspathClassBuilder> classConsumer,
      Consumer<DexClasspathClass> onCreationConsumer,
      Consumer<SyntheticMethodBuilder> buildMethodCallback) {
    DexClasspathClass clazz =
        ensureFixedClasspathClassFromType(
            kind, contextType, appView, classConsumer, onCreationConsumer);
    return internalEnsureFixedClasspathMethod(
        methodName, methodProto, kind, appView, buildMethodCallback, clazz);
  }

  public ClasspathMethod ensureFixedClasspathClassMethod(
      DexString methodName,
      DexProto methodProto,
      SyntheticKind kind,
      ClasspathOrLibraryClass context,
      AppView<?> appView,
      Consumer<SyntheticClasspathClassBuilder> buildClassCallback,
      Consumer<DexClasspathClass> onClassCreationCallback,
      Consumer<SyntheticMethodBuilder> buildMethodCallback) {
    DexClasspathClass clazz =
        ensureFixedClasspathClass(
            kind, context, appView, buildClassCallback, onClassCreationCallback);
    return internalEnsureFixedClasspathMethod(
        methodName, methodProto, kind, appView, buildMethodCallback, clazz);
  }

  private ClasspathMethod internalEnsureFixedClasspathMethod(
      DexString methodName,
      DexProto methodProto,
      SyntheticKind kind,
      AppView<?> appView,
      Consumer<SyntheticMethodBuilder> buildMethodCallback,
      DexClasspathClass clazz) {
    DexMethod methodReference =
        appView.dexItemFactory().createMethod(clazz.getType(), methodProto, methodName);
    DexEncodedMethod methodDefinition =
        internalEnsureMethod(
            methodReference,
            clazz,
            kind,
            appView,
            methodBuilder -> {
              // For class path classes we always disable api level checks because we never trace
              // the code and it cannot be inlined.
              buildMethodCallback.accept(methodBuilder.disableAndroidApiLevelCheck());
            },
            emptyConsumer());
    return new ClasspathMethod(clazz, methodDefinition);
  }

  @SuppressWarnings("unchecked")
  private <T extends DexClassAndMethod> DexEncodedMethod internalEnsureMethod(
      DexMethod methodReference,
      DexClass clazz,
      SyntheticKind kind,
      AppView<?> appView,
      Consumer<SyntheticMethodBuilder> buildMethodCallback,
      Consumer<T> newMethodCallback) {
    MethodCollection methodCollection = clazz.getMethodCollection();
    synchronized (methodCollection) {
      DexEncodedMethod methodDefinition = methodCollection.getMethod(methodReference);
      if (methodDefinition != null) {
        return methodDefinition;
      }
      SyntheticMethodBuilder builder =
          new SyntheticMethodBuilder(appView.dexItemFactory(), clazz.getType(), kind);
      builder.setName(methodReference.getName());
      builder.setProto(methodReference.getProto());
      buildMethodCallback.accept(builder);
      methodDefinition = builder.build();
      methodCollection.addMethod(methodDefinition);
      newMethodCallback.accept((T) DexClassAndMethod.create(clazz, methodDefinition));
      return methodDefinition;
    }
  }

  public DexProgramClass ensureFixedClassFromType(
      SyntheticKind kind,
      DexType contextType,
      AppView<?> appView,
      Consumer<SyntheticProgramClassBuilder> fn,
      Consumer<DexProgramClass> onCreationConsumer) {
    SynthesizingContext outerContext = SynthesizingContext.fromType(contextType);
    return internalEnsureFixedProgramClass(kind, fn, onCreationConsumer, outerContext, appView);
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
            .addMethod(fn.andThen(m -> m.setName(SyntheticNaming.INTERNAL_SYNTHETIC_METHOD_NAME)))
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
