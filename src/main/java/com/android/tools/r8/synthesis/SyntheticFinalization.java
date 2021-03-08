// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.NestedGraphLens;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.TreeFixerBase;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.collections.BidirectionalManyToManyRepresentativeMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToManyRepresentativeHashMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToManyRepresentativeMap;
import com.android.tools.r8.utils.structural.RepresentativeMap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class SyntheticFinalization {

  public static class Result {
    public final CommittedItems commit;
    public final NonIdentityGraphLens lens;
    public final PrunedItems prunedItems;
    public final MainDexInfo mainDexInfo;

    public Result(
        CommittedItems commit,
        SyntheticFinalizationGraphLens lens,
        PrunedItems prunedItems,
        MainDexInfo mainDexInfo) {
      this.commit = commit;
      this.lens = lens;
      this.prunedItems = prunedItems;
      this.mainDexInfo = mainDexInfo;
    }
  }

  public static class SyntheticFinalizationGraphLens extends NestedGraphLens {

    private SyntheticFinalizationGraphLens(
        GraphLens previous,
        Map<DexType, DexType> typeMap,
        BidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap,
        Map<DexMethod, DexMethod> methodMap,
        BidirectionalManyToManyRepresentativeMap<DexMethod, DexMethod> originalMethodSignatures,
        DexItemFactory factory) {
      super(typeMap, methodMap, fieldMap, originalMethodSignatures, previous, factory);
    }

    @Override
    public boolean isSyntheticFinalizationGraphLens() {
      return true;
    }
  }

  private static class Builder {

    Map<DexType, DexType> typeMap = new IdentityHashMap<>();
    BidirectionalManyToOneRepresentativeHashMap<DexField, DexField> fieldMap =
        BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    Map<DexMethod, DexMethod> methodMap = new IdentityHashMap<>();

    protected final MutableBidirectionalOneToManyRepresentativeMap<DexMethod, DexMethod>
        originalMethodSignatures = new BidirectionalOneToManyRepresentativeHashMap<>();

    void move(DexType from, DexType to) {
      DexType old = typeMap.put(from, to);
      assert old == null || old == to;
    }

    void move(DexField from, DexField to) {
      DexField old = fieldMap.put(from, to);
      assert old == null || old == to;
    }

    void move(DexMethod from, DexMethod to) {
      DexMethod old = methodMap.put(from, to);
      assert old == null || old == to;
      originalMethodSignatures.put(to, from);
    }

    SyntheticFinalizationGraphLens build(GraphLens previous, DexItemFactory factory) {
      if (typeMap.isEmpty() && fieldMap.isEmpty() && methodMap.isEmpty()) {
        return null;
      }
      return new SyntheticFinalizationGraphLens(
          previous,
          typeMap,
          fieldMap,
          methodMap,
          originalMethodSignatures,
          factory);
    }
  }

  public static class EquivalenceGroup<T extends SyntheticDefinition<?, T, ?>> {
    private final List<T> members;

    public EquivalenceGroup(T representative, List<T> members) {
      assert !members.isEmpty();
      assert members.get(0) == representative;
      this.members = members;
    }

    public T getRepresentative() {
      return members.get(0);
    }

    public List<T> getMembers() {
      return members;
    }

    public int compareToIncludingContext(
        EquivalenceGroup<T> other,
        GraphLens graphLens,
        ClassToFeatureSplitMap classToFeatureSplitMap,
        SyntheticItems syntheticItems) {
      return getRepresentative()
          .compareTo(
              other.getRepresentative(), true, graphLens, classToFeatureSplitMap, syntheticItems);
    }

    @Override
    public String toString() {
      return "EquivalenceGroup{ members = "
          + members.size()
          + ", repr = "
          + getRepresentative()
          + " }";
    }
  }

  private final InternalOptions options;
  private final SyntheticItems synthetics;
  private final CommittedSyntheticsCollection committed;

  SyntheticFinalization(
      InternalOptions options, SyntheticItems synthetics, CommittedSyntheticsCollection committed) {
    this.options = options;
    this.synthetics = synthetics;
    this.committed = committed;
  }

  public static void finalize(AppView<AppInfo> appView) {
    assert !appView.appInfo().hasClassHierarchy();
    assert !appView.appInfo().hasLiveness();
    Result result = appView.getSyntheticItems().computeFinalSynthetics(appView);
    appView.setAppInfo(new AppInfo(result.commit, result.mainDexInfo));
    if (result.lens != null) {
      appView.setAppInfo(
          appView
              .appInfo()
              .rebuildWithMainDexInfo(
                  appView.appInfo().getMainDexInfo().rewrittenWithLens(result.lens)));
      appView.setGraphLens(result.lens);
    }
    appView.pruneItems(result.prunedItems);
  }

  public static void finalizeWithClassHierarchy(AppView<AppInfoWithClassHierarchy> appView) {
    assert !appView.appInfo().hasLiveness();
    Result result = appView.getSyntheticItems().computeFinalSynthetics(appView);
    appView.setAppInfo(appView.appInfo().rebuildWithClassHierarchy(result.commit));
    appView.setAppInfo(appView.appInfo().rebuildWithMainDexInfo(result.mainDexInfo));
    if (result.lens != null) {
      appView.setGraphLens(result.lens);
      appView.setAppInfo(
          appView
              .appInfo()
              .rebuildWithMainDexInfo(
                  appView.appInfo().getMainDexInfo().rewrittenWithLens(result.lens)));
    }
    appView.pruneItems(result.prunedItems);
  }

  public static void finalizeWithLiveness(AppView<AppInfoWithLiveness> appView) {
    Result result = appView.getSyntheticItems().computeFinalSynthetics(appView);
    appView.setAppInfo(appView.appInfo().rebuildWithLiveness(result.commit));
    appView.setAppInfo(appView.appInfo().rebuildWithMainDexInfo(result.mainDexInfo));
    appView.rewriteWithLens(result.lens);
    appView.pruneItems(result.prunedItems);
  }

  Result computeFinalSynthetics(AppView<?> appView) {
    assert verifyNoNestedSynthetics();
    DexApplication application;
    Builder lensBuilder = new Builder();
    ImmutableMap.Builder<DexType, SyntheticMethodReference> finalMethodsBuilder =
        ImmutableMap.builder();
    ImmutableMap.Builder<DexType, SyntheticProgramClassReference> finalClassesBuilder =
        ImmutableMap.builder();
    Set<DexType> derivedMainDexTypes = Sets.newIdentityHashSet();
    {
      Map<String, NumberGenerator> generators = new HashMap<>();
      application =
          buildLensAndProgram(
              appView,
              computeEquivalences(
                  appView, committed.getNonLegacyMethods().values(), generators, lensBuilder),
              computeEquivalences(
                  appView, committed.getNonLegacyClasses().values(), generators, lensBuilder),
              lensBuilder,
              (clazz, reference) -> finalClassesBuilder.put(clazz.getType(), reference),
              (clazz, reference) -> finalMethodsBuilder.put(clazz.getType(), reference),
              derivedMainDexTypes);
    }
    ImmutableMap<DexType, SyntheticMethodReference> finalMethods = finalMethodsBuilder.build();
    ImmutableMap<DexType, SyntheticProgramClassReference> finalClasses =
        finalClassesBuilder.build();

    Set<DexType> prunedSynthetics = Sets.newIdentityHashSet();
    committed.forEachNonLegacyItem(
        reference -> {
          DexType type = reference.getHolder();
          if (!finalMethods.containsKey(type) && !finalClasses.containsKey(type)) {
            prunedSynthetics.add(type);
          }
        });

    // TODO(b/181858113): Remove once deprecated main-dex-list is removed.
    MainDexInfo.Builder mainDexInfoBuilder = appView.appInfo().getMainDexInfo().builderFromCopy();
    derivedMainDexTypes.forEach(mainDexInfoBuilder::addList);

    return new Result(
        new CommittedItems(
            SyntheticItems.INVALID_ID_AFTER_SYNTHETIC_FINALIZATION,
            application,
            new CommittedSyntheticsCollection(
                committed.getLegacyTypes(), finalMethods, finalClasses),
            ImmutableList.of()),
        lensBuilder.build(appView.graphLens(), appView.dexItemFactory()),
        PrunedItems.builder().setPrunedApp(application).addRemovedClasses(prunedSynthetics).build(),
        mainDexInfoBuilder.build());
  }

  private <R extends SyntheticReference<R, D, ?>, D extends SyntheticDefinition<R, D, ?>>
      Map<DexType, EquivalenceGroup<D>> computeEquivalences(
          AppView<?> appView,
          ImmutableCollection<R> references,
          Map<String, NumberGenerator> generators,
          Builder lensBuilder) {
    boolean intermediate = appView.options().intermediate;
    Map<DexType, D> definitions = lookupDefinitions(appView, references);
    ClassToFeatureSplitMap classToFeatureSplitMap =
        appView.appInfo().hasClassHierarchy()
            ? appView.appInfo().withClassHierarchy().getClassToFeatureSplitMap()
            : ClassToFeatureSplitMap.createEmptyClassToFeatureSplitMap();
    Collection<List<D>> potentialEquivalences =
        computePotentialEquivalences(
            definitions,
            intermediate,
            appView.dexItemFactory(),
            appView.graphLens(),
            classToFeatureSplitMap,
            synthetics);
    return computeActualEquivalences(
        potentialEquivalences,
        generators,
        appView,
        intermediate,
        classToFeatureSplitMap,
        lensBuilder);
  }

  private boolean isNotSyntheticType(DexType type) {
    return !committed.containsNonLegacyType(type);
  }

  private boolean verifyNoNestedSynthetics() {
    // Check that a context is never itself synthetic class.
    committed.forEachNonLegacyItem(
        item -> {
          assert isNotSyntheticType(item.getContext().getSynthesizingContextType())
              || item.getKind().allowSyntheticContext();
        });
    return true;
  }

  private static DexApplication buildLensAndProgram(
      AppView<?> appView,
      Map<DexType, EquivalenceGroup<SyntheticMethodDefinition>> syntheticMethodGroups,
      Map<DexType, EquivalenceGroup<SyntheticProgramClassDefinition>> syntheticClassGroups,
      Builder lensBuilder,
      BiConsumer<DexProgramClass, SyntheticProgramClassReference> addFinalSyntheticClass,
      BiConsumer<DexProgramClass, SyntheticMethodReference> addFinalSyntheticMethod,
      Set<DexType> derivedMainDexSynthetics) {
    DexApplication application = appView.appInfo().app();
    MainDexInfo mainDexInfo = appView.appInfo().getMainDexInfo();
    List<DexProgramClass> newProgramClasses = new ArrayList<>();
    Set<DexType> pruned = Sets.newIdentityHashSet();

    TreeFixerBase treeFixer =
        new TreeFixerBase(appView) {
          @Override
          public DexType mapClassType(DexType type) {
            return lensBuilder.typeMap.getOrDefault(type, type);
          }

          @Override
          public void recordFieldChange(DexField from, DexField to) {
            lensBuilder.move(from, to);
          }

          @Override
          public void recordMethodChange(DexMethod from, DexMethod to) {
            lensBuilder.move(from, to);
          }

          @Override
          public void recordClassChange(DexType from, DexType to) {
            lensBuilder.move(from, to);
          }
        };

    List<DexProgramClass> deduplicatedClasses = new ArrayList<>();
    syntheticMethodGroups.forEach(
        (syntheticType, syntheticGroup) -> {
          SyntheticMethodDefinition representative = syntheticGroup.getRepresentative();
          SynthesizingContext context = representative.getContext();
          context.registerPrefixRewriting(syntheticType, appView);
          DexProgramClass representativeClass = representative.getHolder();
          addSyntheticMarker(representative.getKind(), representativeClass, context, appView);
          assert representativeClass.getMethodCollection().size() == 1;
          for (SyntheticMethodDefinition member : syntheticGroup.getMembers()) {
            if (member != representative) {
              pruned.add(member.getHolder().getType());
              deduplicatedClasses.add(member.getHolder());
            }
            if (member.getContext().isDerivedFromMainDexList(mainDexInfo)) {
              derivedMainDexSynthetics.add(syntheticType);
            }
          }
        });

    syntheticClassGroups.forEach(
        (syntheticType, syntheticGroup) -> {
          SyntheticProgramClassDefinition representative = syntheticGroup.getRepresentative();
          SynthesizingContext context = representative.getContext();
          context.registerPrefixRewriting(syntheticType, appView);
          addSyntheticMarker(
              representative.getKind(), representative.getHolder(), context, appView);
          for (SyntheticProgramClassDefinition member : syntheticGroup.getMembers()) {
            DexProgramClass memberClass = member.getHolder();
            DexType memberType = memberClass.getType();
            if (member != representative) {
              pruned.add(memberType);
              deduplicatedClasses.add(memberClass);
            }
            if (member.getContext().isDerivedFromMainDexList(mainDexInfo)) {
              derivedMainDexSynthetics.add(syntheticType);
            }
          }
        });

    for (DexProgramClass clazz : application.classes()) {
      if (!pruned.contains(clazz.type)) {
        newProgramClasses.add(clazz);
      }
    }
    application = application.builder().replaceProgramClasses(newProgramClasses).build();

    // Assert that the non-representatives have been removed from the app.
    assert verifyNonRepresentativesRemovedFromApplication(application, syntheticClassGroups);
    assert verifyNonRepresentativesRemovedFromApplication(application, syntheticMethodGroups);

    DexApplication.Builder<?> builder = application.builder();
    treeFixer.fixupClasses(deduplicatedClasses);
    builder.replaceProgramClasses(treeFixer.fixupClasses(application.classes()));
    application = builder.build();

    // Add the synthesized from after repackaging which changed class definitions.
    final DexApplication appForLookup = application;
    syntheticClassGroups.forEach(
        (syntheticType, syntheticGroup) -> {
          DexProgramClass externalSyntheticClass = appForLookup.programDefinitionFor(syntheticType);
          SyntheticProgramClassDefinition representative = syntheticGroup.getRepresentative();
          addFinalSyntheticClass.accept(
              externalSyntheticClass,
              new SyntheticProgramClassReference(
                  representative.getKind(),
                  representative.getContext(),
                  externalSyntheticClass.type));
        });
    syntheticMethodGroups.forEach(
        (syntheticType, syntheticGroup) -> {
          DexProgramClass externalSyntheticClass = appForLookup.programDefinitionFor(syntheticType);
          SyntheticMethodDefinition representative = syntheticGroup.getRepresentative();
          assert externalSyntheticClass.getMethodCollection().size() == 1;
          assert externalSyntheticClass.getMethodCollection().hasDirectMethods();
          DexEncodedMethod syntheticMethodDefinition =
              externalSyntheticClass.getMethodCollection().getDirectMethod(alwaysTrue());
          addFinalSyntheticMethod.accept(
              externalSyntheticClass,
              new SyntheticMethodReference(
                  representative.getKind(),
                  representative.getContext(),
                  syntheticMethodDefinition.getReference()));
        });

    for (DexType key : syntheticMethodGroups.keySet()) {
      assert application.definitionFor(key) != null;
    }

    for (DexType key : syntheticClassGroups.keySet()) {
      assert application.definitionFor(key) != null;
    }

    return application;
  }

  private static <T extends SyntheticDefinition<?, T, ?>>
      boolean verifyNonRepresentativesRemovedFromApplication(
          DexApplication application, Map<DexType, EquivalenceGroup<T>> syntheticGroups) {
    for (EquivalenceGroup<?> syntheticGroup : syntheticGroups.values()) {
      for (SyntheticDefinition<?, ?, ?> member : syntheticGroup.getMembers()) {
        assert member == syntheticGroup.getRepresentative()
            || application.definitionFor(member.getHolder().getType()) == null;
      }
    }
    return true;
  }

  private static void addSyntheticMarker(
      SyntheticKind kind,
      DexProgramClass externalSyntheticClass,
      SynthesizingContext context,
      AppView<?> appView) {
    if (shouldAnnotateSynthetics(appView.options())) {
      SyntheticMarker.addMarkerToClass(
          externalSyntheticClass, kind, context, appView.dexItemFactory());
    }
  }

  private static boolean shouldAnnotateSynthetics(InternalOptions options) {
    // Only intermediate builds have annotated synthetics to allow later sharing.
    // This is currently also disabled on CF to CF desugaring to avoid missing class references to
    // the annotated classes.
    // TODO(b/147485959): Find an alternative encoding for synthetics to avoid missing-class refs.
    return options.intermediate && !options.cfToCfDesugar;
  }

  private <T extends SyntheticDefinition<?, T, ?>>
      Map<DexType, EquivalenceGroup<T>> computeActualEquivalences(
          Collection<List<T>> potentialEquivalences,
          Map<String, NumberGenerator> generators,
          AppView<?> appView,
          boolean intermediate,
          ClassToFeatureSplitMap classToFeatureSplitMap,
          Builder lensBuilder) {
    Map<String, List<EquivalenceGroup<T>>> groupsPerPrefix = new HashMap<>();
    potentialEquivalences.forEach(
        members -> {
          List<List<T>> groups =
              groupEquivalent(
                  members, intermediate, appView.graphLens(), classToFeatureSplitMap, synthetics);
          for (List<T> group : groups) {
            T representative =
                findDeterministicRepresentative(
                    group, appView.graphLens(), classToFeatureSplitMap, synthetics);
            // The representative is required to be the first element of the group.
            group.remove(representative);
            group.add(0, representative);
            groupsPerPrefix
                .computeIfAbsent(
                    representative.getPrefixForExternalSyntheticType(), k -> new ArrayList<>())
                .add(new EquivalenceGroup<>(representative, group));
          }
        });

    Map<DexType, EquivalenceGroup<T>> equivalences = new IdentityHashMap<>();
    groupsPerPrefix.forEach(
        (externalSyntheticTypePrefix, groups) -> {
          // Sort the equivalence groups that go into 'context' including the context type of the
          // representative which is equal to 'context' here (see assert below).
          groups.sort(
              (a, b) ->
                  a.compareToIncludingContext(
                      b, appView.graphLens(), classToFeatureSplitMap, synthetics));
          for (int i = 0; i < groups.size(); i++) {
            EquivalenceGroup<T> group = groups.get(i);
            assert group
                .getRepresentative()
                .getPrefixForExternalSyntheticType()
                .equals(externalSyntheticTypePrefix);
            // Two equivalence groups in same context type must be distinct otherwise the assignment
            // of the synthetic name will be non-deterministic between the two.
            assert i == 0
                || checkGroupsAreDistinct(
                    groups.get(i - 1),
                    group,
                    appView.graphLens(),
                    classToFeatureSplitMap,
                    synthetics);
            SyntheticKind kind = group.members.get(0).getKind();
            DexType representativeType =
                createExternalType(kind, externalSyntheticTypePrefix, generators, appView);
            equivalences.put(representativeType, group);
            for (T member : group.getMembers()) {
              lensBuilder.move(member.getHolder().getType(), representativeType);
            }
          }
        });
    return equivalences;
  }

  private static <T extends SyntheticDefinition<?, T, ?>> List<List<T>> groupEquivalent(
      List<T> potentialEquivalence,
      boolean intermediate,
      GraphLens graphLens,
      ClassToFeatureSplitMap classToFeatureSplitMap,
      SyntheticItems syntheticItems) {
    List<List<T>> groups = new ArrayList<>();
    // Each other member is in a shared group if it is actually equivalent to the first member.
    for (T synthetic : potentialEquivalence) {
      boolean requireNewGroup = true;
      for (List<T> group : groups) {
        if (synthetic.isEquivalentTo(
            group.get(0), intermediate, graphLens, classToFeatureSplitMap, syntheticItems)) {
          requireNewGroup = false;
          group.add(synthetic);
          break;
        }
      }
      if (requireNewGroup) {
        List<T> newGroup = new ArrayList<>();
        newGroup.add(synthetic);
        groups.add(newGroup);
      }
    }
    return groups;
  }

  private static <T extends SyntheticDefinition<?, T, ?>> boolean checkGroupsAreDistinct(
      EquivalenceGroup<T> g1,
      EquivalenceGroup<T> g2,
      GraphLens graphLens,
      ClassToFeatureSplitMap classToFeatureSplitMap,
      SyntheticItems syntheticItems) {
    int order = g1.compareToIncludingContext(g2, graphLens, classToFeatureSplitMap, syntheticItems);
    assert order != 0;
    assert order
        != g2.compareToIncludingContext(g1, graphLens, classToFeatureSplitMap, syntheticItems);
    return true;
  }

  private static <T extends SyntheticDefinition<?, T, ?>> T findDeterministicRepresentative(
      List<T> members,
      GraphLens graphLens,
      ClassToFeatureSplitMap classToFeatureSplitMap,
      SyntheticItems syntheticItems) {
    // Pick a deterministic member as representative.
    T smallest = members.get(0);
    for (int i = 1; i < members.size(); i++) {
      T next = members.get(i);
      if (next.toReference().getReference().compareTo(smallest.toReference().getReference()) < 0) {
        smallest = next;
      }
    }
    return smallest;
  }

  private DexType createExternalType(
      SyntheticKind kind,
      String externalSyntheticTypePrefix,
      Map<String, NumberGenerator> generators,
      AppView<?> appView) {
    DexItemFactory factory = appView.dexItemFactory();
    if (kind.isFixedSuffixSynthetic) {
      return SyntheticNaming.createExternalType(kind, externalSyntheticTypePrefix, "", factory);
    }
    NumberGenerator generator =
        generators.computeIfAbsent(externalSyntheticTypePrefix, k -> new NumberGenerator());
    DexType externalType;
    do {
      externalType =
          SyntheticNaming.createExternalType(
              kind, externalSyntheticTypePrefix, Integer.toString(generator.next()), factory);
      DexClass clazz = appView.appInfo().definitionForWithoutExistenceAssert(externalType);
      if (clazz != null && isNotSyntheticType(clazz.type)) {
        assert options.testing.allowConflictingSyntheticTypes
            : "Unexpected creation of an existing external synthetic type: " + clazz;
        externalType = null;
      }
    } while (externalType == null);
    return externalType;
  }

  private static <T extends SyntheticDefinition<?, T, ?>>
      Collection<List<T>> computePotentialEquivalences(
          Map<DexType, T> definitions,
          boolean intermediate,
          DexItemFactory factory,
          GraphLens graphLens,
          ClassToFeatureSplitMap classToFeatureSplitMap,
          SyntheticItems syntheticItems) {
    if (definitions.isEmpty()) {
      return Collections.emptyList();
    }
    // Map all synthetic types to the java 'void' type. This is not an actual valid type, so it
    // cannot collide with any valid java type providing a good hashing key for the synthetics.
    Set<DexType> syntheticTypes;
    if (graphLens.isIdentityLens()) {
      syntheticTypes = definitions.keySet();
    } else {
      // If the synthetics are renamed include their original names in the equivalence too.
      syntheticTypes = SetUtils.newIdentityHashSet(definitions.size() * 2);
      definitions
          .keySet()
          .forEach(
              t -> {
                syntheticTypes.add(t);
                syntheticTypes.add(graphLens.getOriginalType(t));
              });
    }
    RepresentativeMap map = t -> syntheticTypes.contains(t) ? factory.voidType : t;
    Map<HashCode, List<T>> equivalences = new HashMap<>(definitions.size());
    for (T definition : definitions.values()) {
      HashCode hash =
          definition.computeHash(map, intermediate, classToFeatureSplitMap, syntheticItems);
      equivalences.computeIfAbsent(hash, k -> new ArrayList<>()).add(definition);
    }
    return equivalences.values();
  }

  private <R extends SyntheticReference<R, D, ?>, D extends SyntheticDefinition<R, D, ?>>
      Map<DexType, D> lookupDefinitions(AppView<?> appView, Collection<R> references) {
    Map<DexType, D> definitions = new IdentityHashMap<>(references.size());
    for (R reference : references) {
      D definition = reference.lookupDefinition(appView::definitionFor);
      if (definition == null) {
        // We expect pruned definitions to have been removed.
        assert false;
        continue;
      }
      if (definition.isValid()) {
        definitions.put(reference.getHolder(), definition);
      } else {
        // Failing this check indicates that an optimization has modified the synthetic in a
        // disruptive way.
        assert false;
      }
    }
    return definitions;
  }
}
