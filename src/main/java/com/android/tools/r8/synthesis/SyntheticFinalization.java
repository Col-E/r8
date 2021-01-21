// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
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
import com.android.tools.r8.shaking.MainDexClasses;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.collections.BidirectionalManyToManyRepresentativeMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneHashMap;
import com.android.tools.r8.utils.structural.RepresentativeMap;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

public class SyntheticFinalization {

  public static class Result {
    public final CommittedItems commit;
    public final NonIdentityGraphLens lens;
    public final PrunedItems prunedItems;

    public Result(
        CommittedItems commit, SyntheticFinalizationGraphLens lens, PrunedItems prunedItems) {
      this.commit = commit;
      this.lens = lens;
      this.prunedItems = prunedItems;
    }
  }

  public static class SyntheticFinalizationGraphLens extends NestedGraphLens {

    private final Map<DexType, DexType> syntheticTypeMap;
    private final Map<DexMethod, DexMethod> syntheticMethodsMap;

    private SyntheticFinalizationGraphLens(
        GraphLens previous,
        Map<DexType, DexType> syntheticClassesMap,
        Map<DexMethod, DexMethod> syntheticMethodsMap,
        Map<DexType, DexType> typeMap,
        BidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap,
        Map<DexMethod, DexMethod> methodMap,
        BidirectionalManyToManyRepresentativeMap<DexMethod, DexMethod> originalMethodSignatures,
        DexItemFactory factory) {
      super(typeMap, methodMap, fieldMap, originalMethodSignatures, previous, factory);
      this.syntheticTypeMap = syntheticClassesMap;
      this.syntheticMethodsMap = syntheticMethodsMap;
    }

    // The mapping is many to one, so the inverse is only defined up to equivalence groups.
    // Override the access to renamed signatures to first check for synthetic mappings before
    // using the original item mappings of the

    @Override
    public DexField getRenamedFieldSignature(DexField originalField) {
      if (syntheticTypeMap.containsKey(originalField.holder)) {
        DexField renamed = fieldMap.get(originalField);
        if (renamed != null) {
          return renamed;
        }
      }
      return super.getRenamedFieldSignature(originalField);
    }

    @Override
    public DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens applied) {
      if (syntheticTypeMap.containsKey(originalMethod.holder)) {
        DexMethod renamed = methodMap.get(originalMethod);
        if (renamed != null) {
          return renamed;
        }
      }
      DexMethod renamed = syntheticMethodsMap.get(originalMethod);
      return renamed != null ? renamed : super.getRenamedMethodSignature(originalMethod, applied);
    }
  }

  private static class Builder {

    // Forward mapping of internal to external synthetics.
    Map<DexType, DexType> syntheticClassesMap = new IdentityHashMap<>();
    Map<DexMethod, DexMethod> syntheticMethodsMap = new IdentityHashMap<>();

    Map<DexType, DexType> typeMap = new IdentityHashMap<>();
    BidirectionalManyToOneRepresentativeHashMap<DexField, DexField> fieldMap =
        new BidirectionalManyToOneRepresentativeHashMap<>();
    Map<DexMethod, DexMethod> methodMap = new IdentityHashMap<>();

    protected final BidirectionalOneToOneHashMap<DexMethod, DexMethod> originalMethodSignatures =
        new BidirectionalOneToOneHashMap<>();

    void moveSyntheticClass(DexType from, DexType to) {
      assert !syntheticClassesMap.containsKey(from);
      syntheticClassesMap.put(from, to);
      typeMap.put(from, to);
    }

    void moveSyntheticMethod(DexMethod from, DexMethod to) {
      assert !syntheticMethodsMap.containsKey(from);
      syntheticMethodsMap.put(from, to);
      methodMap.put(from, to);
    }

    void move(DexType from, DexType to) {
      typeMap.put(from, to);
    }

    void move(DexField from, DexField to) {
      fieldMap.put(from, to);
    }

    void move(DexMethod from, DexMethod to) {
      methodMap.put(from, to);
      originalMethodSignatures.put(to, from);
    }

    SyntheticFinalizationGraphLens build(GraphLens previous, DexItemFactory factory) {
      assert verifySubMap(syntheticClassesMap, typeMap);
      if (typeMap.isEmpty() && fieldMap.isEmpty() && methodMap.isEmpty()) {
        return null;
      }
      return new SyntheticFinalizationGraphLens(
          previous,
          syntheticClassesMap,
          syntheticMethodsMap,
          typeMap,
          fieldMap,
          methodMap,
          originalMethodSignatures,
          factory);
    }

    private static <K, V> boolean verifySubMap(Map<K, V> sub, Map<K, V> sup) {
      for (Entry<K, V> entry : sub.entrySet()) {
        assert sup.get(entry.getKey()) == entry.getValue();
      }
      return true;
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

    public int compareToIncludingContext(EquivalenceGroup<T> other, GraphLens graphLens) {
      return getRepresentative().compareTo(other.getRepresentative(), true, graphLens);
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
  private final CommittedSyntheticsCollection synthetics;

  SyntheticFinalization(InternalOptions options, CommittedSyntheticsCollection synthetics) {
    this.options = options;
    this.synthetics = synthetics;
  }

  public Result computeFinalSynthetics(AppView<?> appView) {
    assert verifyNoNestedSynthetics();
    DexApplication application;
    MainDexClasses mainDexClasses = appView.appInfo().getMainDexClasses();
    List<DexProgramClass> finalSyntheticClasses = new ArrayList<>();
    Builder lensBuilder = new Builder();
    {
      Map<DexType, NumberGenerator> generators = new IdentityHashMap<>();
      application =
          buildLensAndProgram(
              appView,
              computeEquivalences(appView, synthetics.getNonLegacyMethods().values(), generators),
              computeEquivalences(appView, synthetics.getNonLegacyClasses().values(), generators),
              mainDexClasses,
              lensBuilder,
              finalSyntheticClasses);
    }

    handleSynthesizedClassMapping(
        finalSyntheticClasses, application, options, mainDexClasses, lensBuilder.typeMap);

    assert appView.appInfo().getMainDexClasses() == mainDexClasses;

    Set<DexType> finalSyntheticTypes = Sets.newIdentityHashSet();
    finalSyntheticClasses.forEach(clazz -> finalSyntheticTypes.add(clazz.getType()));

    Set<DexType> prunedSynthetics = Sets.newIdentityHashSet();
    synthetics.forEachNonLegacyItem(
        reference -> {
          DexType type = reference.getHolder();
          if (!finalSyntheticTypes.contains(type)) {
            prunedSynthetics.add(type);
          }
        });

    return new Result(
        new CommittedItems(
            SyntheticItems.INVALID_ID_AFTER_SYNTHETIC_FINALIZATION,
            application,
            new CommittedSyntheticsCollection(
                synthetics.getLegacyTypes(), ImmutableMap.of(), ImmutableMap.of()),
            ImmutableList.of()),
        lensBuilder.build(appView.graphLens(), appView.dexItemFactory()),
        PrunedItems.builder()
            .setPrunedApp(application)
            .addRemovedClasses(prunedSynthetics)
            .build());
  }

  private <R extends SyntheticReference<R, D, ?>, D extends SyntheticDefinition<R, D, ?>>
      Map<DexType, EquivalenceGroup<D>> computeEquivalences(
          AppView<?> appView,
          ImmutableCollection<R> references,
          Map<DexType, NumberGenerator> generators) {
    boolean intermediate = appView.options().intermediate;
    Map<DexType, D> definitions = lookupDefinitions(appView, references);
    Collection<List<D>> potentialEquivalences =
        computePotentialEquivalences(
            definitions, intermediate, appView.dexItemFactory(), appView.graphLens());
    return computeActualEquivalences(potentialEquivalences, generators, appView, intermediate);
  }

  private boolean isNotSyntheticType(DexType type) {
    return !synthetics.containsNonLegacyType(type);
  }

  private boolean verifyNoNestedSynthetics() {
    // Check that a context is never itself synthetic class.
    synthetics.forEachNonLegacyItem(
        item -> {
          assert isNotSyntheticType(item.getContext().getSynthesizingContextType());
        });
    return true;
  }

  private void handleSynthesizedClassMapping(
      List<DexProgramClass> finalSyntheticClasses,
      DexApplication application,
      InternalOptions options,
      MainDexClasses mainDexClasses,
      Map<DexType, DexType> derivedMainDexTypesToIgnore) {
    boolean includeSynthesizedClassMappingInOutput = shouldAnnotateSynthetics(options);
    if (includeSynthesizedClassMappingInOutput) {
      updateSynthesizedClassMapping(application, finalSyntheticClasses);
    }
    updateMainDexListWithSynthesizedClassMap(
        application, mainDexClasses, derivedMainDexTypesToIgnore);
    if (!includeSynthesizedClassMappingInOutput) {
      clearSynthesizedClassMapping(application);
    }
  }

  private void updateSynthesizedClassMapping(
      DexApplication application, List<DexProgramClass> finalSyntheticClasses) {
    ListMultimap<DexProgramClass, DexProgramClass> originalToSynthesized =
        ArrayListMultimap.create();
    for (DexType type : synthetics.getLegacyTypes()) {
      DexProgramClass clazz = DexProgramClass.asProgramClassOrNull(application.definitionFor(type));
      if (clazz != null) {
        for (DexProgramClass origin : clazz.getSynthesizedFrom()) {
          originalToSynthesized.put(origin, clazz);
        }
      }
    }
    for (DexProgramClass clazz : finalSyntheticClasses) {
      for (DexProgramClass origin : clazz.getSynthesizedFrom()) {
        originalToSynthesized.put(origin, clazz);
      }
    }
    for (Map.Entry<DexProgramClass, Collection<DexProgramClass>> entry :
        originalToSynthesized.asMap().entrySet()) {
      DexProgramClass original = entry.getKey();
      // Use a tree set to make sure that we have an ordering on the types.
      // These types are put in an array in annotations in the output and we
      // need a consistent ordering on them.
      TreeSet<DexType> synthesized = new TreeSet<>(DexType::compareTo);
      entry.getValue().stream()
          .map(dexProgramClass -> dexProgramClass.type)
          .forEach(synthesized::add);
      synthesized.addAll(
          DexAnnotation.readAnnotationSynthesizedClassMap(original, application.dexItemFactory));

      DexAnnotation updatedAnnotation =
          DexAnnotation.createAnnotationSynthesizedClassMap(
              synthesized, application.dexItemFactory);

      original.setAnnotations(original.annotations().getWithAddedOrReplaced(updatedAnnotation));
    }
  }

  private void updateMainDexListWithSynthesizedClassMap(
      DexApplication application,
      MainDexClasses mainDexClasses,
      Map<DexType, DexType> derivedMainDexTypesToIgnore) {
    if (mainDexClasses.isEmpty()) {
      return;
    }
    List<DexProgramClass> newMainDexClasses = new ArrayList<>();
    mainDexClasses.forEach(
        dexType -> {
          DexProgramClass programClass =
              DexProgramClass.asProgramClassOrNull(application.definitionFor(dexType));
          if (programClass != null) {
            Collection<DexType> derived =
                DexAnnotation.readAnnotationSynthesizedClassMap(
                    programClass, application.dexItemFactory);
            for (DexType type : derived) {
              DexType mappedType = derivedMainDexTypesToIgnore.getOrDefault(type, type);
              DexProgramClass syntheticClass =
                  DexProgramClass.asProgramClassOrNull(application.definitionFor(mappedType));
              if (syntheticClass != null) {
                newMainDexClasses.add(syntheticClass);
              }
            }
          }
        });
    mainDexClasses.addAll(newMainDexClasses);
  }

  private void clearSynthesizedClassMapping(DexApplication application) {
    for (DexProgramClass clazz : application.classes()) {
      clazz.setAnnotations(
          clazz.annotations().getWithout(application.dexItemFactory.annotationSynthesizedClassMap));
    }
  }

  private static DexApplication buildLensAndProgram(
      AppView<?> appView,
      Map<DexType, EquivalenceGroup<SyntheticMethodDefinition>> syntheticMethodGroups,
      Map<DexType, EquivalenceGroup<SyntheticProgramClassDefinition>> syntheticClassGroups,
      MainDexClasses mainDexClasses,
      Builder lensBuilder,
      List<DexProgramClass> newSyntheticClasses) {
    DexApplication application = appView.appInfo().app();
    DexItemFactory factory = appView.dexItemFactory();

    // TODO(b/168584485): Remove this once class-mapping support is removed.
    Set<DexType> derivedMainDexTypes = Sets.newIdentityHashSet();
    mainDexClasses.forEach(
        mainDexType -> {
          derivedMainDexTypes.add(mainDexType);
          DexProgramClass mainDexClass =
              DexProgramClass.asProgramClassOrNull(
                  appView.appInfo().definitionForWithoutExistenceAssert(mainDexType));
          if (mainDexClass != null) {
            derivedMainDexTypes.addAll(
                DexAnnotation.readAnnotationSynthesizedClassMap(mainDexClass, factory));
          }
        });

    Set<DexType> pruned = Sets.newIdentityHashSet();
    syntheticMethodGroups.forEach(
        (syntheticType, syntheticGroup) -> {
          SyntheticMethodDefinition representative = syntheticGroup.getRepresentative();
          SynthesizingContext context = representative.getContext();
          context.registerPrefixRewriting(syntheticType, appView);
          DexProgramClass externalSyntheticClass =
              createExternalMethodClass(syntheticType, representative, factory);
          newSyntheticClasses.add(externalSyntheticClass);
          addSyntheticMarker(representative.getKind(), externalSyntheticClass, context, appView);
          assert externalSyntheticClass.getMethodCollection().size() == 1;
          DexEncodedMethod externalSyntheticMethod =
              externalSyntheticClass.methods().iterator().next();
          for (SyntheticMethodDefinition member : syntheticGroup.getMembers()) {
            DexMethod memberReference = member.getMethod().getReference();
            pruned.add(member.getHolder().getType());
            if (memberReference != externalSyntheticMethod.method) {
              lensBuilder.moveSyntheticMethod(memberReference, externalSyntheticMethod.method);
            }
          }
        });

    List<DexProgramClass> deduplicatedClasses = new ArrayList<>();
    syntheticClassGroups.forEach(
        (syntheticType, syntheticGroup) -> {
          SyntheticProgramClassDefinition representative = syntheticGroup.getRepresentative();
          SynthesizingContext context = representative.getContext();
          context.registerPrefixRewriting(syntheticType, appView);
          DexProgramClass externalSyntheticClass = representative.getHolder();
          newSyntheticClasses.add(externalSyntheticClass);
          addSyntheticMarker(representative.getKind(), externalSyntheticClass, context, appView);
          for (SyntheticProgramClassDefinition member : syntheticGroup.getMembers()) {
            DexProgramClass memberClass = member.getHolder();
            DexType memberType = memberClass.getType();
            pruned.add(memberType);
            if (memberType != syntheticType) {
              lensBuilder.moveSyntheticClass(memberType, syntheticType);
            }
            // The aliasing of the non-representative members needs to be recorded manually.
            if (member != representative) {
              deduplicatedClasses.add(memberClass);
            }
          }
        });

    List<DexProgramClass> newProgramClasses = new ArrayList<>(newSyntheticClasses);
    for (DexProgramClass clazz : application.classes()) {
      if (!pruned.contains(clazz.type)) {
        newProgramClasses.add(clazz);
      }
    }
    application = application.builder().replaceProgramClasses(newProgramClasses).build();

    // We can only assert that the method container classes are in here as the classes need
    // to be rewritten by the tree-fixer.
    for (DexType key : syntheticMethodGroups.keySet()) {
      assert application.definitionFor(key) != null;
    }

    newSyntheticClasses.clear();

    DexApplication.Builder<?> builder = application.builder();
    TreeFixerBase treeFixer =
        new TreeFixerBase(appView) {
          @Override
          public DexType mapClassType(DexType type) {
            return lensBuilder.syntheticClassesMap.getOrDefault(type, type);
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
    treeFixer.fixupClasses(deduplicatedClasses);
    builder.replaceProgramClasses(treeFixer.fixupClasses(application.classes()));
    application = builder.build();

    // Add the synthesized from after repackaging which changed class definitions.
    final DexApplication appForLookup = application;
    syntheticClassGroups.forEach(
        (syntheticType, syntheticGroup) -> {
          DexProgramClass externalSyntheticClass = appForLookup.programDefinitionFor(syntheticType);
          newSyntheticClasses.add(externalSyntheticClass);
          for (SyntheticProgramClassDefinition member : syntheticGroup.getMembers()) {
            addMainDexAndSynthesizedFromForMember(
                member,
                externalSyntheticClass,
                mainDexClasses,
                derivedMainDexTypes,
                appForLookup::programDefinitionFor);
          }
        });
    syntheticMethodGroups.forEach(
        (syntheticType, syntheticGroup) -> {
          DexProgramClass externalSyntheticClass = appForLookup.programDefinitionFor(syntheticType);
          newSyntheticClasses.add(externalSyntheticClass);
          for (SyntheticMethodDefinition member : syntheticGroup.getMembers()) {
            addMainDexAndSynthesizedFromForMember(
                member,
                externalSyntheticClass,
                mainDexClasses,
                derivedMainDexTypes,
                appForLookup::programDefinitionFor);
          }
        });

    for (DexType key : syntheticMethodGroups.keySet()) {
      assert application.definitionFor(key) != null;
    }

    for (DexType key : syntheticClassGroups.keySet()) {
      assert application.definitionFor(key) != null;
    }

    return application;
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

  private static DexProgramClass createExternalMethodClass(
      DexType syntheticType, SyntheticMethodDefinition representative, DexItemFactory factory) {
    SyntheticProgramClassBuilder builder =
        new SyntheticProgramClassBuilder(syntheticType, representative.getContext(), factory);
    // TODO(b/158159959): Support grouping multiple methods per synthetic class.
    builder.addMethod(
        methodBuilder -> {
          DexEncodedMethod definition = representative.getMethod().getDefinition();
          methodBuilder
              .setName(SyntheticNaming.INTERNAL_SYNTHETIC_METHOD_PREFIX)
              .setAccessFlags(definition.accessFlags)
              .setProto(definition.getProto())
              .setClassFileVersion(
                  definition.hasClassFileVersion() ? definition.getClassFileVersion() : null)
              .setCode(m -> definition.getCode());
        });
    return builder.build();
  }

  private static void addMainDexAndSynthesizedFromForMember(
      SyntheticDefinition<?, ?, ?> member,
      DexProgramClass externalSyntheticClass,
      MainDexClasses mainDexClasses,
      Set<DexType> derivedMainDexTypes,
      Function<DexType, DexProgramClass> definitions) {
    member
        .getContext()
        .addIfDerivedFromMainDexClass(externalSyntheticClass, mainDexClasses, derivedMainDexTypes);
    // TODO(b/168584485): Remove this once class-mapping support is removed.
    DexProgramClass from = definitions.apply(member.getContext().getSynthesizingContextType());
    if (from != null) {
      externalSyntheticClass.addSynthesizedFrom(from);
    }
  }

  private static boolean shouldAnnotateSynthetics(InternalOptions options) {
    // Only intermediate builds have annotated synthetics to allow later sharing.
    // This is currently also disabled on CF to CF desugaring to avoid missing class references to
    // the annotated classes.
    // TODO(b/147485959): Find an alternative encoding for synthetics to avoid missing-class refs.
    // TODO(b/168584485): Remove support for main-dex tracing with the class-map annotation.
    return options.intermediate && !options.cfToCfDesugar;
  }

  private <T extends SyntheticDefinition<?, T, ?>>
      Map<DexType, EquivalenceGroup<T>> computeActualEquivalences(
          Collection<List<T>> potentialEquivalences,
          Map<DexType, NumberGenerator> generators,
          AppView<?> appView,
          boolean intermediate) {
    Map<DexType, List<EquivalenceGroup<T>>> groupsPerContext = new IdentityHashMap<>();
    potentialEquivalences.forEach(
        members -> {
          List<List<T>> groups = groupEquivalent(members, intermediate, appView.graphLens());
          for (List<T> group : groups) {
            T representative = findDeterministicRepresentative(group, appView.graphLens());
            // The representative is required to be the first element of the group.
            group.remove(representative);
            group.add(0, representative);
            groupsPerContext
                .computeIfAbsent(
                    representative.getContext().getSynthesizingContextType(),
                    k -> new ArrayList<>())
                .add(new EquivalenceGroup<>(representative, group));
          }
        });

    Map<DexType, EquivalenceGroup<T>> equivalences = new IdentityHashMap<>();
    groupsPerContext.forEach(
        (context, groups) -> {
          // Sort the equivalence groups that go into 'context' including the context type of the
          // representative which is equal to 'context' here (see assert below).
          groups.sort((a, b) -> a.compareToIncludingContext(b, appView.graphLens()));
          for (int i = 0; i < groups.size(); i++) {
            EquivalenceGroup<T> group = groups.get(i);
            assert group.getRepresentative().getContext().getSynthesizingContextType() == context;
            // Two equivalence groups in same context type must be distinct otherwise the assignment
            // of the synthetic name will be non-deterministic between the two.
            assert i == 0 || checkGroupsAreDistinct(groups.get(i - 1), group, appView.graphLens());
            SyntheticKind kind = group.members.get(0).getKind();
            DexType representativeType = createExternalType(kind, context, generators, appView);
            equivalences.put(representativeType, group);
          }
        });
    return equivalences;
  }

  private static <T extends SyntheticDefinition<?, T, ?>> List<List<T>> groupEquivalent(
      List<T> potentialEquivalence, boolean intermediate, GraphLens graphLens) {
    List<List<T>> groups = new ArrayList<>();
    // Each other member is in a shared group if it is actually equivalent to the first member.
    for (T synthetic : potentialEquivalence) {
      boolean requireNewGroup = true;
      for (List<T> group : groups) {
        if (synthetic.isEquivalentTo(group.get(0), intermediate, graphLens)) {
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
      EquivalenceGroup<T> g1, EquivalenceGroup<T> g2, GraphLens graphLens) {
    int order = g1.compareToIncludingContext(g2, graphLens);
    assert order != 0;
    assert order != g2.compareToIncludingContext(g1, graphLens);
    return true;
  }

  private static <T extends SyntheticDefinition<?, T, ?>> T findDeterministicRepresentative(
      List<T> members, GraphLens graphLens) {
    // Pick a deterministic member as representative.
    T smallest = members.get(0);
    for (int i = 1; i < members.size(); i++) {
      T next = members.get(i);
      if (next.compareTo(smallest, true, graphLens) < 0) {
        smallest = next;
      }
    }
    return smallest;
  }

  private DexType createExternalType(
      SyntheticKind kind,
      DexType representativeContext,
      Map<DexType, NumberGenerator> generators,
      AppView<?> appView) {
    NumberGenerator generator =
        generators.computeIfAbsent(representativeContext, k -> new NumberGenerator());
    DexType externalType;
    do {
      externalType =
          SyntheticNaming.createExternalType(
              kind,
              representativeContext,
              Integer.toString(generator.next()),
              appView.dexItemFactory());
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
          GraphLens graphLens) {
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
      HashCode hash = definition.computeHash(map, intermediate);
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
