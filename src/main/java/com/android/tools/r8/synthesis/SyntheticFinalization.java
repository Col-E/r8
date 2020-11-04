// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.Builder;
import com.android.tools.r8.graph.GraphLens.NestedGraphLens;
import com.android.tools.r8.shaking.MainDexClasses;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

public class SyntheticFinalization {

  public static class Result {
    public final CommittedItems commit;
    public final ImmutableSet<DexType> removedSyntheticClasses;

    public Result(CommittedItems commit, ImmutableSet<DexType> removedSyntheticClasses) {
      this.commit = commit;
      this.removedSyntheticClasses = removedSyntheticClasses;
    }
  }

  private static class EquivalenceGroup<T extends SyntheticDefinition & Comparable<T>>
      implements Comparable<EquivalenceGroup<T>> {
    private List<T> members;

    EquivalenceGroup(T singleton) {
      this(singleton, Collections.singletonList(singleton));
    }

    EquivalenceGroup(T representative, List<T> members) {
      assert !members.isEmpty();
      assert members.get(0) == representative;
      this.members = members;
    }

    T getRepresentative() {
      return members.get(0);
    }

    public List<T> getMembers() {
      return members;
    }

    @Override
    public int compareTo(EquivalenceGroup<T> other) {
      return getRepresentative().compareTo(other.getRepresentative());
    }
  }

  private final InternalOptions options;
  private final ImmutableSet<DexType> legacySyntheticTypes;
  private final ImmutableMap<DexType, SyntheticReference> syntheticItems;

  SyntheticFinalization(
      InternalOptions options,
      ImmutableSet<DexType> legacySyntheticTypes,
      ImmutableMap<DexType, SyntheticReference> syntheticItems) {
    this.options = options;
    this.legacySyntheticTypes = legacySyntheticTypes;
    this.syntheticItems = syntheticItems;
  }

  public Result computeFinalSynthetics(AppView<?> appView) {
    assert verifyNoNestedSynthetics();
    DexApplication application = appView.appInfo().app();
    MainDexClasses mainDexClasses = appView.appInfo().getMainDexClasses();
    GraphLens graphLens = appView.graphLens();

    List<SyntheticMethodDefinition> methodDefinitions =
        lookupSyntheticMethodDefinitions(application);

    Collection<List<SyntheticMethodDefinition>> potentialEquivalences =
        computePotentialEquivalences(methodDefinitions, options.intermediate);

    Map<DexType, EquivalenceGroup<SyntheticMethodDefinition>> equivalences =
        computeActualEquivalences(potentialEquivalences, options.intermediate, options.itemFactory);

    Builder lensBuilder = NestedGraphLens.builder();
    List<DexProgramClass> newProgramClasses = new ArrayList<>();
    List<DexProgramClass> finalSyntheticClasses = new ArrayList<>();
    Set<DexType> derivedMainDexTypesToIgnore = Sets.newIdentityHashSet();
    buildLensAndProgram(
        appView,
        equivalences,
        syntheticItems::containsKey,
        mainDexClasses,
        lensBuilder,
        newProgramClasses,
        finalSyntheticClasses,
        derivedMainDexTypesToIgnore);

    newProgramClasses.addAll(finalSyntheticClasses);

    handleSynthesizedClassMapping(
        finalSyntheticClasses, application, options, mainDexClasses, derivedMainDexTypesToIgnore);

    DexApplication app = application.builder().replaceProgramClasses(newProgramClasses).build();

    appView.setGraphLens(lensBuilder.build(options.itemFactory, graphLens));
    assert appView.appInfo().getMainDexClasses() == mainDexClasses;
    return new Result(
        new CommittedItems(
            SyntheticItems.INVALID_ID_AFTER_SYNTHETIC_FINALIZATION,
            app,
            legacySyntheticTypes,
            ImmutableMap.of(),
            ImmutableList.of()),
        syntheticItems.keySet());
  }

  private boolean verifyNoNestedSynthetics() {
    // Check that a context is never itself synthetic class.
    for (SyntheticReference item : syntheticItems.values()) {
      assert !syntheticItems.containsKey(item.getContext().getSynthesizingContextType());
    }
    return true;
  }

  private void handleSynthesizedClassMapping(
      List<DexProgramClass> finalSyntheticClasses,
      DexApplication application,
      InternalOptions options,
      MainDexClasses mainDexClasses,
      Set<DexType> derivedMainDexTypesToIgnore) {
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
    for (DexType type : legacySyntheticTypes) {
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
      TreeSet<DexType> synthesized = new TreeSet<>(DexType::slowCompareTo);
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
      Set<DexType> derivedMainDexTypesToIgnore) {
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
              if (!derivedMainDexTypesToIgnore.contains(type)) {
                DexProgramClass syntheticClass =
                    DexProgramClass.asProgramClassOrNull(application.definitionFor(type));
                if (syntheticClass != null) {
                  newMainDexClasses.add(syntheticClass);
                }
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

  private static void buildLensAndProgram(
      AppView<?> appView,
      Map<DexType, EquivalenceGroup<SyntheticMethodDefinition>> syntheticMethodGroups,
      Predicate<DexType> isSyntheticType,
      MainDexClasses mainDexClasses,
      Builder lensBuilder,
      List<DexProgramClass> normalClasses,
      List<DexProgramClass> newSyntheticClasses,
      Set<DexType> derivedMainDexTypesToIgnore) {
    DexItemFactory factory = appView.dexItemFactory();

    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (!isSyntheticType.test(clazz.type)) {
        assert SyntheticItems.verifyNotInternalSynthetic(clazz.type);
        normalClasses.add(clazz);
      }
    }

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

    syntheticMethodGroups.forEach(
        (syntheticType, syntheticGroup) -> {
          SyntheticMethodDefinition representative = syntheticGroup.getRepresentative();
          SynthesizingContext context = representative.getContext();
          context.registerPrefixRewriting(syntheticType, appView);
          SyntheticClassBuilder builder =
              new SyntheticClassBuilder(syntheticType, context, factory);
          // TODO(b/158159959): Support grouping multiple methods per synthetic class.
          builder.addMethod(
              methodBuilder -> {
                DexEncodedMethod definition = representative.getMethod().getDefinition();
                methodBuilder
                    .setAccessFlags(definition.accessFlags)
                    .setProto(definition.getProto())
                    .setCode(m -> definition.getCode());
              });
          DexProgramClass externalSyntheticClass = builder.build();
          if (shouldAnnotateSynthetics(appView.options())) {
            externalSyntheticClass.setAnnotations(
                externalSyntheticClass
                    .annotations()
                    .getWithAddedOrReplaced(
                        DexAnnotation.createAnnotationSynthesizedClass(
                            context.getSynthesizingContextType(), factory)));
          }
          assert externalSyntheticClass.getMethodCollection().size() == 1;
          DexEncodedMethod externalSyntheticMethod =
              externalSyntheticClass.methods().iterator().next();
          newSyntheticClasses.add(externalSyntheticClass);
          for (SyntheticMethodDefinition member : syntheticGroup.getMembers()) {
            if (member.getMethod().getReference() != externalSyntheticMethod.method) {
              lensBuilder.map(member.getMethod().getReference(), externalSyntheticMethod.method);
            }
            member
                .getContext()
                .addIfDerivedFromMainDexClass(
                    externalSyntheticClass,
                    mainDexClasses,
                    derivedMainDexTypes,
                    derivedMainDexTypesToIgnore);
            // TODO(b/168584485): Remove this once class-mapping support is removed.
            DexProgramClass from =
                DexProgramClass.asProgramClassOrNull(
                    appView
                        .appInfo()
                        .definitionForWithoutExistenceAssert(
                            member.getContext().getSynthesizingContextType()));
            if (from != null) {
              externalSyntheticClass.addSynthesizedFrom(from);
            }
          }
        });
  }

  private static boolean shouldAnnotateSynthetics(InternalOptions options) {
    // Only intermediate builds have annotated synthetics to allow later sharing.
    // This is currently also disabled on CF to CF desugaring to avoid missing class references to
    // the annotated classes.
    // TODO(b/147485959): Find an alternative encoding for synthetics to avoid missing-class refs.
    // TODO(b/168584485): Remove support for main-dex tracing with the class-map annotation.
    return options.intermediate && !options.cfToCfDesugar;
  }

  private static <T extends SyntheticDefinition & Comparable<T>>
      Map<DexType, EquivalenceGroup<T>> computeActualEquivalences(
          Collection<List<T>> potentialEquivalences, boolean intermediate, DexItemFactory factory) {
    Map<DexType, List<EquivalenceGroup<T>>> groupsPerContext = new IdentityHashMap<>();
    potentialEquivalences.forEach(
        members -> {
          List<List<T>> groups = groupEquivalent(members, intermediate);
          for (List<T> group : groups) {
            T representative = findDeterministicRepresentative(group);
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
          groups.sort(EquivalenceGroup::compareTo);
          for (int i = 0; i < groups.size(); i++) {
            EquivalenceGroup<T> group = groups.get(i);
            // Two equivalence groups in same context type must be distinct otherwise the assignment
            // of the synthetic name will be non-deterministic between the two.
            assert i == 0 || checkGroupsAreDistict(groups.get(i - 1), group);
            DexType representativeType = createExternalType(context, i, factory);
            equivalences.put(representativeType, group);
          }
        });
    return equivalences;
  }

  private static <T extends SyntheticDefinition & Comparable<T>> List<List<T>> groupEquivalent(
      List<T> potentialEquivalence, boolean intermediate) {
    List<List<T>> groups = new ArrayList<>();
    // Each other member is in a shared group if it is actually equivalent to the first member.
    for (T synthetic : potentialEquivalence) {
      boolean requireNewGroup = true;
      for (List<T> group : groups) {
        if (synthetic.isEquivalentTo(group.get(0), intermediate)) {
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

  private static <T extends SyntheticDefinition & Comparable<T>> boolean checkGroupsAreDistict(
      EquivalenceGroup<T> g1, EquivalenceGroup<T> g2) {
    assert g1.compareTo(g2) != 0;
    return true;
  }

  private static <T extends SyntheticDefinition & Comparable<T>> T findDeterministicRepresentative(
      List<T> members) {
    // Pick a deterministic member as representative.
    T smallest = members.get(0);
    for (int i = 1; i < members.size(); i++) {
      T next = members.get(i);
      if (next.compareTo(smallest) < 0) {
        smallest = next;
      }
    }
    return smallest;
  }

  private static DexType createExternalType(
      DexType representativeContext, int nextContextId, DexItemFactory factory) {
    return factory.createType(
        DescriptorUtils.getDescriptorFromClassBinaryName(
            representativeContext.getInternalName()
                + SyntheticItems.EXTERNAL_SYNTHETIC_CLASS_SEPARATOR
                + nextContextId));
  }

  private static <T extends SyntheticDefinition> Collection<List<T>> computePotentialEquivalences(
      List<T> definitions, boolean intermediate) {
    Map<HashCode, List<T>> equivalences = new HashMap<>(definitions.size());
    for (T definition : definitions) {
      HashCode hash = definition.computeHash(intermediate);
      equivalences.computeIfAbsent(hash, k -> new ArrayList<>()).add(definition);
    }
    return equivalences.values();
  }

  private List<SyntheticMethodDefinition> lookupSyntheticMethodDefinitions(
      DexApplication finalApp) {
    List<SyntheticMethodDefinition> methods = new ArrayList<>(syntheticItems.size());
    for (SyntheticReference reference : syntheticItems.values()) {
      SyntheticDefinition definition = reference.lookupDefinition(finalApp::definitionFor);
      if (definition == null || !(definition instanceof SyntheticMethodDefinition)) {
        // We expect pruned definitions to have been removed.
        assert false;
        continue;
      }
      SyntheticMethodDefinition method = (SyntheticMethodDefinition) definition;
      if (SyntheticMethodBuilder.isValidSyntheticMethod(method.getMethod().getDefinition())) {
        methods.add(method);
      } else {
        // Failing this check indicates that an optimization has modified the synthetic in a
        // disruptive way.
        assert false;
      }
    }
    return methods;
  }
}
