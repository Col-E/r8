// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.SyntheticInfoConsumer;
import com.android.tools.r8.SyntheticInfoConsumerData;
import com.android.tools.r8.contexts.CompilationContext.UniqueContext;
import com.android.tools.r8.errors.MissingGlobalSyntheticsConsumerDiagnostic;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassResolutionResult;
import com.android.tools.r8.graph.ClasspathMethod;
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
import com.android.tools.r8.graph.MethodCollection;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ProgramOrClasspathDefinition;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.profile.startup.profile.StartupProfile;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticFinalization.Result;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.objectweb.asm.ClassWriter;

public class SyntheticItems implements SyntheticDefinitionsProvider {

  public boolean isSyntheticClassEligibleForMerging(DexProgramClass clazz) {
    SyntheticDefinition<?, ?, ?> definition = pending.definitions.get(clazz.type);
    if (definition != null) {
      return definition.getKind().isShareable();
    }
    Iterable<SyntheticReference<?, ?, ?>> references = committed.getItems(clazz.type);
    Iterator<SyntheticReference<?, ?, ?>> iterator = references.iterator();
    if (iterator.hasNext()) {
      boolean sharable = iterator.next().getKind().isShareable();
      assert Iterables.all(references, r -> sharable == r.getKind().isShareable());
      return sharable;
    }
    return false;
  }

  public interface GlobalSyntheticsStrategy {
    ContextsForGlobalSynthetics getStrategy();

    static GlobalSyntheticsStrategy forNonSynthesizing() {
      ContextsForGlobalSyntheticsInSingleOutputMode instance =
          new ContextsForGlobalSyntheticsInSingleOutputMode() {
            @Override
            public void addGlobalContexts(
                DexType globalType, Collection<? extends ProgramDefinition> contexts) {
              throw new Unreachable("Unexpected attempt to add globals to non-desugaring build.");
            }
          };
      return () -> instance;
    }

    static GlobalSyntheticsStrategy forSingleOutputMode() {
      ContextsForGlobalSynthetics instance = new ContextsForGlobalSyntheticsInSingleOutputMode();
      return () -> instance;
    }

    static GlobalSyntheticsStrategy forPerFileMode() {
      // Allocate a new context set as the new pending set.
      return ContextsForGlobalSyntheticsInPerFileMode::new;
    }
  }

  interface ContextsForGlobalSynthetics {
    boolean isEmpty();

    void forEach(BiConsumer<DexType, Set<DexType>> fn);

    void addGlobalContexts(DexType globalType, Collection<? extends ProgramDefinition> contexts);
  }

  private static class ContextsForGlobalSyntheticsInSingleOutputMode
      implements ContextsForGlobalSynthetics {

    @Override
    public boolean isEmpty() {
      return true;
    }

    @Override
    public void forEach(BiConsumer<DexType, Set<DexType>> fn) {
      // nothing to do.
    }

    @Override
    public void addGlobalContexts(
        DexType globalType, Collection<? extends ProgramDefinition> contexts) {
      // contexts are ignored in single output modes.
    }
  }

  private static class ContextsForGlobalSyntheticsInPerFileMode
      implements ContextsForGlobalSynthetics {
    private final ConcurrentHashMap<DexType, Set<DexType>> globalContexts =
        new ConcurrentHashMap<>();

    @Override
    public boolean isEmpty() {
      return globalContexts.isEmpty();
    }

    @Override
    public void forEach(BiConsumer<DexType, Set<DexType>> fn) {
      globalContexts.forEach(fn);
    }

    @Override
    public void addGlobalContexts(
        DexType globalType, Collection<? extends ProgramDefinition> contexts) {
      Set<DexType> contextReferences =
          globalContexts.computeIfAbsent(globalType, k -> ConcurrentHashMap.newKeySet());
      contexts.forEach(definition -> contextReferences.add(definition.getContextType()));
    }
  }

  enum State {
    OPEN,
    FINALIZED
  }

  /** Collection of pending items. */
  private static class PendingSynthetics {

    /** Thread safe collection of synthetic items not yet committed to the application. */
    private final ConcurrentHashMap<DexType, SyntheticDefinition<?, ?, ?>> definitions =
        new ConcurrentHashMap<>();

    boolean isEmpty() {
      return definitions.isEmpty();
    }

    boolean containsType(DexType type) {
      return definitions.containsKey(type);
    }

    boolean containsTypeOfKind(DexType type, SyntheticKind kind) {
      SyntheticDefinition<?, ?, ?> definition = definitions.get(type);
      return definition != null && definition.getKind() == kind;
    }

    boolean verifyNotRewritten(NonIdentityGraphLens lens) {
      assert definitions.keySet().equals(lens.rewriteTypes(definitions.keySet()));
      return true;
    }

    Collection<DexProgramClass> getAllProgramClasses() {
      List<DexProgramClass> allPending = new ArrayList<>(definitions.size());
      for (SyntheticDefinition<?, ?, ?> item : definitions.values()) {
        if (item.isProgramDefinition()) {
          allPending.add(item.asProgramDefinition().getHolder());
        }
      }
      return Collections.unmodifiableList(allPending);
    }
  }

  private final State state;
  private final SyntheticNaming naming;
  private final CommittedSyntheticsCollection committed;
  private final PendingSynthetics pending = new PendingSynthetics();
  private final ContextsForGlobalSynthetics globalContexts;
  private final GlobalSyntheticsStrategy globalSyntheticsStrategy;

  public Set<DexType> collectSyntheticsFromContext(DexType context) {
    Set<DexType> result = Sets.newIdentityHashSet();
    committed
        .getMethods()
        .forEach(
            (synthetic, methodReferences) -> {
              methodReferences.forEach(
                  methodReference -> {
                    if (methodReference.getContext().getSynthesizingContextType() == context) {
                      result.add(synthetic);
                    }
                  });
            });
    committed
        .getClasses()
        .forEach(
            (synthetic, classReferences) -> {
              classReferences.forEach(
                  classReference -> {
                    if (classReference.getContext().getSynthesizingContextType() == context) {
                      result.add(synthetic);
                    }
                  });
            });
    return result;
  }

  public SyntheticNaming getNaming() {
    return naming;
  }

  public GlobalSyntheticsStrategy getGlobalSyntheticsStrategy() {
    return globalSyntheticsStrategy;
  }

  // Only for use from initial AppInfo/AppInfoWithClassHierarchy create functions. */
  public static CommittedItems createInitialSyntheticItems(
      DexApplication application, GlobalSyntheticsStrategy globalSyntheticsStrategy) {
    return new CommittedItems(
        State.OPEN,
        application,
        CommittedSyntheticsCollection.empty(application.dexItemFactory().getSyntheticNaming()),
        ImmutableList.of(),
        globalSyntheticsStrategy);
  }

  // Only for conversion to a mutable synthetic items collection.
  SyntheticItems(CommittedItems commit) {
    this(commit.state, commit.committed, commit.globalSyntheticsStrategy);
  }

  private SyntheticItems(
      State state,
      CommittedSyntheticsCollection committed,
      GlobalSyntheticsStrategy globalSyntheticsStrategy) {
    this.state = state;
    this.committed = committed;
    this.naming = committed.getNaming();
    this.globalContexts = globalSyntheticsStrategy.getStrategy();
    this.globalSyntheticsStrategy = globalSyntheticsStrategy;
  }

  public Map<DexType, Set<DexType>> getFinalGlobalSyntheticContexts(AppView appView) {
    assert isFinalized();
    DexItemFactory factory = appView.dexItemFactory();
    ImmutableMap<DexType, Set<DexType>> globalContexts = committed.getGlobalContexts();
    NamingLens namingLens = appView.getNamingLens();
    Map<DexType, Set<DexType>> rewritten = new IdentityHashMap<>(globalContexts.size());
    globalContexts.forEach(
        (global, contexts) -> {
          Set<DexType> old =
              rewritten.put(
                  namingLens.lookupType(global, factory),
                  SetUtils.mapIdentityHashSet(contexts, c -> namingLens.lookupType(c, factory)));
          assert old == null;
        });
    return rewritten;
  }

  public static void collectSyntheticInputs(AppView<?> appView) {
    // Collecting synthetic items must be the very first task after application build.
    SyntheticItems synthetics = appView.getSyntheticItems();
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
            method ->
                builder.addMethod(
                    new SyntheticMethodDefinition(marker.getKind(), marker.getContext(), method)));
      } else if (marker.isSyntheticClass()) {
        builder.addClass(
            new SyntheticProgramClassDefinition(marker.getKind(), marker.getContext(), clazz));
      }
    }
    CommittedSyntheticsCollection committed = builder.collectSyntheticInputs().build();
    if (committed.isEmpty()) {
      return;
    }
    CommittedItems commit =
        new CommittedItems(
            synthetics.state,
            appView.appInfo().app(),
            committed,
            ImmutableList.of(),
            synthetics.globalSyntheticsStrategy);
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
    SyntheticDefinition<?, ?, ?> item = pending.definitions.get(type);
    if (item != null) {
      clazz = item.getHolder();
      kind = item.getKind();
      assert clazz.isProgramClass() == item.isProgramDefinition();
      assert clazz.isClasspathClass() == item.isClasspathDefinition();
    }
    if (clazz != null) {
      assert kind != null;
      assert !baseDefinitionFor.apply(type).hasClassResolutionResult()
              || kind.isMayOverridesNonProgramType()
          : "Pending synthetic definition also present in the active program: " + type;
      return clazz;
    }
    return baseDefinitionFor.apply(type);
  }

  public boolean isFinalized() {
    return state == State.FINALIZED;
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

  public boolean isPendingSynthetic(DexType type) {
    return pending.containsType(type);
  }

  public boolean isSynthetic(DexProgramClass clazz) {
    return isSynthetic(clazz.type);
  }

  public boolean isSynthetic(DexType type) {
    return committed.containsType(type) || pending.definitions.containsKey(type);
  }

  public boolean isEligibleForClassMerging(DexProgramClass clazz, HorizontalClassMerger.Mode mode) {
    assert isSyntheticClass(clazz);
    return mode.isFinal() || isSyntheticLambda(clazz);
  }

  private boolean isSyntheticLambda(DexProgramClass clazz) {
    if (!isSynthetic(clazz)) {
      return false;
    }
    Iterable<SyntheticReference<?, ?, ?>> references = committed.getItems(clazz.getType());
    if (!Iterables.isEmpty(references)) {
      assert Iterables.size(references) == 1;
      return references.iterator().next().getKind() == naming.LAMBDA;
    }
    SyntheticDefinition<?, ?, ?> definition = pending.definitions.get(clazz.getType());
    if (definition != null) {
      return definition.getKind() == naming.LAMBDA;
    }
    assert false;
    return false;
  }

  public boolean isSubjectToKeepRules(DexProgramClass clazz) {
    assert isSyntheticClass(clazz);
    return isSyntheticInput(clazz);
  }

  public boolean isSyntheticClass(DexType type) {
    return isSynthetic(type);
  }

  public boolean isSyntheticClass(DexProgramClass clazz) {
    return isSyntheticClass(clazz.type);
  }

  public boolean isGlobalSyntheticClass(DexType type) {
    SyntheticDefinition<?, ?, ?> definition = pending.definitions.get(type);
    if (definition != null) {
      return definition.getKind().isGlobal();
    }
    return isGlobalReferences(committed.getClasses().get(type));
  }

  public boolean isGlobalSyntheticClass(DexProgramClass clazz) {
    return isGlobalSyntheticClass(clazz.getType());
  }

  private static boolean isGlobalReferences(List<SyntheticProgramClassReference> references) {
    if (references == null) {
      return false;
    }
    if (references.size() == 1 && references.get(0).getKind().isGlobal()) {
      return true;
    }
    assert verifyNoGlobals(references);
    return false;
  }

  private static boolean verifyNoGlobals(List<SyntheticProgramClassReference> references) {
    for (SyntheticProgramClassReference reference : references) {
      assert !reference.getKind().isGlobal();
    }
    return true;
  }

  public boolean isSyntheticOfKind(DexType type, SyntheticKindSelector kindSelector) {
    SyntheticKind kind = kindSelector.select(naming);
    return pending.containsTypeOfKind(type, kind) || committed.containsTypeOfKind(type, kind);
  }

  public Iterable<SyntheticKind> getSyntheticKinds(DexType type) {
    Iterable<SyntheticKind> references =
        IterableUtils.transform(committed.getItems(type), SyntheticReference::getKind);
    SyntheticDefinition<?, ?, ?> definition = pending.definitions.get(type);
    if (definition != null) {
      references = Iterables.concat(references, IterableUtils.singleton(definition.getKind()));
    }
    return references;
  }

  boolean isSyntheticInput(DexProgramClass clazz) {
    return committed.containsSyntheticInput(clazz.getType());
  }

  public FeatureSplit getContextualFeatureSplitOrDefault(DexType type, FeatureSplit defaultValue) {
    assert isSyntheticClass(type);
    if (isSyntheticOfKind(type, kinds -> kinds.ENUM_UNBOXING_SHARED_UTILITY_CLASS)) {
      return FeatureSplit.BASE;
    }
    List<SynthesizingContext> contexts = getSynthesizingContexts(type);
    if (contexts.isEmpty()) {
      assert false
          : "Expected synthetic to have at least one synthesizing context: " + type.getTypeName();
      return defaultValue;
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
    for (SyntheticReference<?, ?, ?> reference : committed.getItems(type)) {
      consumer.accept(reference.getContext());
    }
    SyntheticDefinition<?, ?, ?> definition = pending.definitions.get(type);
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

  public Collection<Origin> getSynthesizingOrigin(DexType type) {
    if (!isSynthetic(type)) {
      return Collections.emptyList();
    }
    ImmutableList.Builder<Origin> builder = ImmutableList.builder();
    forEachSynthesizingContext(
        type,
        context -> {
          builder.add(context.getInputContextOrigin());
        });
    return builder.build();
  }

  public DexType getSynthesizingInputContext(DexType syntheticType, InternalOptions options) {
    if (!isSynthetic(syntheticType)) {
      return null;
    }
    Box<DexType> uniqueInputContext = new Box<>(null);
    forEachSynthesizingContext(
        syntheticType,
        context -> {
          assert uniqueInputContext.get() == null;
          uniqueInputContext.set(context.getSynthesizingInputContext(options.intermediate));
        });
    return uniqueInputContext.get();
  }

  public interface SynthesizingContextOracle {

    Set<DexReference> getSynthesizingContexts(DexProgramClass clazz);
  }

  public boolean isSyntheticMethodThatShouldNotBeDoubleProcessed(ProgramMethod method) {
    for (SyntheticMethodReference reference :
        committed.getMethods().getOrDefault(method.getHolderType(), Collections.emptyList())) {
      if (reference.getKind().equals(naming.STATIC_INTERFACE_CALL)) {
        return true;
      }
    }
    SyntheticDefinition<?, ?, ?> definition = pending.definitions.get(method.getHolderType());
    if (definition != null) {
      return definition.getKind().equals(naming.STATIC_INTERFACE_CALL);
    }
    return false;
  }

  // The compiler should not inspect the kind of a synthetic, so this provided only as a assertion
  // utility.
  public boolean verifySyntheticLambdaProperty(
      DexProgramClass clazz,
      Predicate<DexProgramClass> ifIsLambda,
      Predicate<DexProgramClass> ifNotLambda) {
    Iterable<SyntheticReference<?, ?, ?>> references = committed.getItems(clazz.getType());
    SyntheticDefinition<?, ?, ?> definition = pending.definitions.get(clazz.getType());
    if (definition != null) {
      references = Iterables.concat(references, IterableUtils.singleton(definition.toReference()));
    }
    if (Iterables.any(references, reference -> reference.getKind().equals(naming.LAMBDA))) {
      assert ifIsLambda.test(clazz);
    } else {
      assert ifNotLambda.test(clazz);
    }
    return true;
  }

  private SynthesizingContext getSynthesizingContext(
      ProgramDefinition context, AppView<?> appView) {
    InternalOptions options = appView.options();
    if (appView.hasClassHierarchy()) {
      AppInfoWithClassHierarchy appInfo = appView.appInfoWithClassHierarchy();
      return getSynthesizingContext(
          context, appInfo.getClassToFeatureSplitMap(), options, appView.getStartupProfile());
    }
    return getSynthesizingContext(
        context,
        ClassToFeatureSplitMap.createEmptyClassToFeatureSplitMap(),
        options,
        StartupProfile.empty());
  }

  /** Used to find the synthesizing context for a new synthetic that is about to be created. */
  private SynthesizingContext getSynthesizingContext(
      ProgramDefinition context,
      ClassToFeatureSplitMap featureSplits,
      InternalOptions options,
      StartupProfile startupProfile) {
    DexType contextType = context.getContextType();
    SyntheticDefinition<?, ?, ?> existingDefinition = pending.definitions.get(contextType);
    if (existingDefinition != null) {
      return existingDefinition.getContext();
    }
    Iterable<SyntheticReference<?, ?, ?>> existingReferences = committed.getItems(contextType);
    if (!Iterables.isEmpty(existingReferences)) {
      // Use a deterministic synthesizing context from the set of contexts.
      return IterableUtils.min(
              existingReferences,
              (existingReference, other) ->
                  existingReference.getReference().compareTo(other.getReference()))
          .getContext();
    }
    // This context is not nested in an existing synthetic context so create a new "leaf" context.
    FeatureSplit featureSplit =
        featureSplits.getFeatureSplit(context, options, startupProfile, this);
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
      SyntheticKindSelector kindSelector, UniqueContext context, AppView<?> appView) {
    return createClass(kindSelector, context, appView, ConsumerUtils.emptyConsumer());
  }

  public DexProgramClass createClass(
      SyntheticKindSelector kindSelector,
      UniqueContext context,
      AppView<?> appView,
      Consumer<SyntheticProgramClassBuilder> fn) {
    SyntheticKind kind = kindSelector.select(naming);
    // Obtain the outer synthesizing context in the case the context itself is synthetic.
    // This is to ensure a flat input-type -> synthetic-item mapping.
    SynthesizingContext outerContext = getSynthesizingContext(context.getClassContext(), appView);
    Function<SynthesizingContext, DexType> contextToType =
        c -> SyntheticNaming.createInternalType(kind, c, context.getSyntheticSuffix(), appView);
    return internalCreateProgramClass(
        kind, fn, outerContext, contextToType.apply(outerContext), contextToType, appView);
  }

  // TODO(b/172194101): Make this take a unique context.
  public DexProgramClass createFixedClass(
      SyntheticKindSelector kindSelector,
      DexProgramClass context,
      AppView<?> appView,
      Consumer<SyntheticProgramClassBuilder> fn) {
    SyntheticKind kind = kindSelector.select(naming);
    SynthesizingContext outerContext = internalGetOuterContext(context, appView);
    Function<SynthesizingContext, DexType> contextToType =
        c -> SyntheticNaming.createFixedType(kind, c, appView.dexItemFactory());
    return internalCreateProgramClass(
        kind, fn, outerContext, contextToType.apply(outerContext), contextToType, appView);
  }

  public DexProgramClass getExistingFixedClass(
      SyntheticKindSelector kindSelector, DexClass context, AppView<?> appView) {
    SyntheticKind kind = kindSelector.select(naming);
    assert kind.isFixedSuffixSynthetic();
    SynthesizingContext outerContext = internalGetOuterContext(context, appView);
    DexType type = SyntheticNaming.createFixedType(kind, outerContext, appView.dexItemFactory());
    DexClass clazz = appView.definitionFor(type);
    assert clazz != null : "Missing existing fixed class " + type;
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

  @FunctionalInterface
  public interface SyntheticKindSelector {
    SyntheticKind select(SyntheticNaming naming);
  }

  /**
   * Ensure that a fixed synthetic class exists.
   *
   * <p>This method is thread safe and will synchronize based on the context of the fixed synthetic.
   */
  public DexProgramClass ensureFixedClass(
      SyntheticKindSelector kindSelector,
      DexClass context,
      AppView<?> appView,
      Consumer<SyntheticProgramClassBuilder> fn,
      Consumer<DexProgramClass> onCreationConsumer) {
    SyntheticKind kind = kindSelector.select(naming);
    assert kind.isFixedSuffixSynthetic();
    SynthesizingContext outerContext = internalGetOuterContext(context, appView);
    return internalEnsureFixedProgramClass(kind, fn, onCreationConsumer, outerContext, appView);
  }

  public ProgramMethod ensureFixedClassMethod(
      DexString name,
      DexProto proto,
      SyntheticKindSelector kindSelector,
      ProgramOrClasspathDefinition context,
      AppView<?> appView,
      Consumer<SyntheticProgramClassBuilder> buildClassCallback,
      Consumer<SyntheticMethodBuilder> buildMethodCallback) {
    return ensureFixedClassMethod(
        name,
        proto,
        kindSelector,
        context,
        appView,
        buildClassCallback,
        buildMethodCallback,
        emptyConsumer());
  }

  public ProgramMethod ensureFixedClassMethod(
      DexString name,
      DexProto proto,
      SyntheticKindSelector kindSelector,
      ProgramOrClasspathDefinition context,
      AppView<?> appView,
      Consumer<SyntheticProgramClassBuilder> buildClassCallback,
      Consumer<SyntheticMethodBuilder> buildMethodCallback,
      Consumer<ProgramMethod> newMethodCallback) {
    SyntheticKind kind = kindSelector.select(naming);
    DexProgramClass clazz =
        ensureFixedClass(
            kindSelector, context.getContextClass(), appView, buildClassCallback, emptyConsumer());
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
      SyntheticKindSelector kindSelector,
      DexType contextType,
      AppView<?> appView,
      Consumer<SyntheticClasspathClassBuilder> classConsumer,
      Consumer<DexClasspathClass> onCreationConsumer) {
    SyntheticKind kind = kindSelector.select(naming);
    SynthesizingContext outerContext = SynthesizingContext.fromType(contextType);
    return internalEnsureFixedClasspathClass(
        kind, classConsumer, onCreationConsumer, outerContext, appView);
  }

  public DexClasspathClass ensureFixedClasspathClass(
      SyntheticKindSelector kindSelector,
      ClasspathOrLibraryClass context,
      AppView<?> appView,
      Consumer<SyntheticClasspathClassBuilder> classConsumer,
      Consumer<DexClasspathClass> onCreationConsumer) {
    // Obtain the outer synthesizing context in the case the context itself is synthetic.
    // This is to ensure a flat input-type -> synthetic-item mapping.
    SynthesizingContext outerContext = SynthesizingContext.fromNonSyntheticInputContext(context);
    return internalEnsureFixedClasspathClass(
        kindSelector.select(naming), classConsumer, onCreationConsumer, outerContext, appView);
  }

  public ClasspathMethod ensureFixedClasspathMethodFromType(
      DexString methodName,
      DexProto methodProto,
      SyntheticKindSelector kindSelector,
      DexType contextType,
      AppView<?> appView,
      Consumer<SyntheticClasspathClassBuilder> classConsumer,
      Consumer<DexClasspathClass> onCreationConsumer,
      Consumer<SyntheticMethodBuilder> buildMethodCallback) {
    DexClasspathClass clazz =
        ensureFixedClasspathClassFromType(
            kindSelector, contextType, appView, classConsumer, onCreationConsumer);
    return internalEnsureFixedClasspathMethod(
        methodName, methodProto, kindSelector.select(naming), appView, buildMethodCallback, clazz);
  }

  public ClasspathMethod ensureFixedClasspathClassMethod(
      DexString methodName,
      DexProto methodProto,
      SyntheticKindSelector kindSelector,
      ClasspathOrLibraryClass context,
      AppView<?> appView,
      Consumer<SyntheticClasspathClassBuilder> buildClassCallback,
      Consumer<DexClasspathClass> onClassCreationCallback,
      Consumer<SyntheticMethodBuilder> buildMethodCallback) {
    DexClasspathClass clazz =
        ensureFixedClasspathClass(
            kindSelector, context, appView, buildClassCallback, onClassCreationCallback);
    return internalEnsureFixedClasspathMethod(
        methodName, methodProto, kindSelector.select(naming), appView, buildMethodCallback, clazz);
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

  public DexProgramClass ensureGlobalClass(
      Supplier<MissingGlobalSyntheticsConsumerDiagnostic> diagnosticSupplier,
      SyntheticKindSelector kindSelector,
      DexType globalType,
      Collection<? extends ProgramDefinition> contexts,
      AppView<?> appView,
      Consumer<SyntheticProgramClassBuilder> fn,
      Consumer<DexProgramClass> onCreationConsumer) {
    SyntheticKind kind = kindSelector.select(naming);
    assert kind.isGlobal();
    assert !contexts.isEmpty();
    if (appView.options().intermediate && !appView.options().hasGlobalSyntheticsConsumer()) {
      appView.reporter().fatalError(diagnosticSupplier.get());
    }
    // A global type is its own context.
    SynthesizingContext outerContext = SynthesizingContext.fromType(globalType);
    DexProgramClass globalSynthetic =
        internalEnsureFixedProgramClass(kind, fn, onCreationConsumer, outerContext, appView);
    Consumer<DexProgramClass> globalSyntheticCreatedCallback =
        appView.options().testing.globalSyntheticCreatedCallback;
    if (globalSyntheticCreatedCallback != null) {
      globalSyntheticCreatedCallback.accept(globalSynthetic);
    }
    addGlobalContexts(globalSynthetic.getType(), contexts);
    return globalSynthetic;
  }

  /** Create a single synthetic method item. */
  public ProgramMethod createMethod(
      SyntheticKindSelector kindSelector,
      UniqueContext context,
      AppView<?> appView,
      Consumer<SyntheticMethodBuilder> fn) {
    return createMethod(
        kindSelector, context.getClassContext(), appView, fn, context::getSyntheticSuffix);
  }

  private ProgramMethod createMethod(
      SyntheticKindSelector kindSelector,
      ProgramDefinition context,
      AppView<?> appView,
      Consumer<SyntheticMethodBuilder> fn,
      Supplier<String> syntheticIdSupplier) {
    assert !isFinalized();
    // Obtain the outer synthesizing context in the case the context itself is synthetic.
    // This is to ensure a flat input-type -> synthetic-item mapping.
    SynthesizingContext outerContext = getSynthesizingContext(context, appView);
    SyntheticKind kind = kindSelector.select(naming);
    DexType type =
        SyntheticNaming.createInternalType(kind, outerContext, syntheticIdSupplier.get(), appView);
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
    pending.definitions.put(definition.getHolder().getType(), definition);
  }

  private void addGlobalContexts(
      DexType globalType, Collection<? extends ProgramDefinition> contexts) {
    globalContexts.addGlobalContexts(globalType, contexts);
  }

  // Commit of the synthetic items to a new fully populated application.

  public CommittedItems commit(DexApplication application) {
    return commitPrunedItems(PrunedItems.empty(application));
  }

  public CommittedItems commitPrunedItems(PrunedItems prunedItems) {
    return commit(prunedItems, pending, globalContexts, committed, state, globalSyntheticsStrategy);
  }

  public CommittedItems commitRewrittenWithLens(
      DexApplication application, NonIdentityGraphLens lens, Timing timing) {
    timing.begin("Rewrite SyntheticItems");
    assert pending.verifyNotRewritten(lens);
    CommittedItems committedItems =
        commit(
            PrunedItems.empty(application),
            pending,
            globalContexts,
            committed.rewriteWithLens(lens, timing),
            state,
            globalSyntheticsStrategy);
    timing.end();
    return committedItems;
  }

  private static CommittedItems commit(
      PrunedItems prunedItems,
      PendingSynthetics pending,
      ContextsForGlobalSynthetics globalContexts,
      CommittedSyntheticsCollection committed,
      State state,
      GlobalSyntheticsStrategy globalSyntheticsStrategy) {
    DexApplication application = prunedItems.getPrunedApp();
    Set<DexType> removedClasses = prunedItems.getNoLongerSyntheticItems();
    CommittedSyntheticsCollection.Builder builder = committed.builder();
    // Compute the synthetic additions and add them to the application.
    ImmutableList<DexType> committedProgramTypes;
    DexApplication amendedApplication;
    if (pending.definitions.isEmpty()) {
      committedProgramTypes = ImmutableList.of();
      amendedApplication = application;
    } else {
      DexApplication.Builder<?> appBuilder = application.builder();
      ImmutableList.Builder<DexType> committedProgramTypesBuilder = ImmutableList.builder();
      for (SyntheticDefinition<?, ?, ?> definition : pending.definitions.values()) {
        if (!removedClasses.contains(definition.getHolder().getType())) {
          if (definition.isProgramDefinition()) {
            committedProgramTypesBuilder.add(definition.getHolder().getType());
            if (definition.getKind().isMayOverridesNonProgramType()) {
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
      builder.addGlobalContexts(globalContexts);
      committedProgramTypes = committedProgramTypesBuilder.build();
      amendedApplication = appBuilder.build();
    }
    return new CommittedItems(
        state,
        amendedApplication,
        builder.build().pruneItems(prunedItems),
        committedProgramTypes,
        globalSyntheticsStrategy);
  }

  public void writeAttributeIfIntermediateSyntheticClass(
      ClassWriter writer, DexProgramClass clazz, AppView<?> appView) {
    if (!appView.options().intermediate || !appView.options().isGeneratingClassFiles()) {
      return;
    }
    Iterator<SyntheticReference<?, ?, ?>> it = committed.getItems(clazz.getType()).iterator();
    if (it.hasNext()) {
      SyntheticKind kind = it.next().getKind();
      // When compiling intermediates there should not be any mergings as they may invalidate the
      // single kind of a synthetic which is required for marking synthetics. This check could be
      // relaxed to ensure that all kinds are equivalent if merging is possible.
      assert !it.hasNext();
      SyntheticMarker.writeMarkerAttribute(writer, kind, appView.getSyntheticItems());
    }
  }

  // Finalization of synthetic items.

  Result computeFinalSynthetics(AppView<?> appView, Timing timing) {
    assert !hasPendingSyntheticClasses();
    return new SyntheticFinalization(appView.options(), this, committed)
        .computeFinalSynthetics(appView, timing);
  }

  public void reportSyntheticsInformation(SyntheticInfoConsumer consumer) {
    assert isFinalized();
    Map<DexType, DexType> seen = new IdentityHashMap<>();
    committed.forEachItem(
        ref -> {
          DexType holder = ref.getHolder();
          DexType context = ref.getContext().getSynthesizingContextType();
          DexType old = seen.put(holder, context);
          assert old == null || old == context;
          if (old == null) {
            consumer.acceptSyntheticInfo(new SyntheticInfoConsumerDataImpl(holder, context));
          }
        });
  }

  private static class SyntheticInfoConsumerDataImpl implements SyntheticInfoConsumerData {

    private final DexType holder;
    private final DexType context;

    public SyntheticInfoConsumerDataImpl(DexType holder, DexType context) {
      this.holder = holder;
      this.context = context;
    }

    @Override
    public ClassReference getSyntheticClass() {
      return Reference.classFromDescriptor(holder.toDescriptorString());
    }

    @Override
    public ClassReference getSynthesizingContextClass() {
      return Reference.classFromDescriptor(context.toDescriptorString());
    }
  }
}
