// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import static java.util.Collections.emptyList;

import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.IterableUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Immutable collection of committed items.
 *
 * <p>This structure is to make it easier to pass the items from SyntheticItems to CommittedItems
 * and back while also providing a builder for updating the committed synthetics.
 */
class CommittedSyntheticsCollection {

  static class Builder {
    private final CommittedSyntheticsCollection parent;
    private Map<DexType, List<SyntheticProgramClassReference>> newNonLegacyClasses = null;
    private Map<DexType, List<SyntheticMethodReference>> newNonLegacyMethods = null;
    private Map<DexType, List<LegacySyntheticReference>> newLegacyClasses = null;
    private ImmutableSet.Builder<DexType> newSyntheticInputs = null;

    public Builder(CommittedSyntheticsCollection parent) {
      this.parent = parent;
    }

    public Builder addItem(SyntheticDefinition<?, ?, ?> definition) {
      if (definition.isProgramDefinition()) {
        definition.asProgramDefinition().apply(this::addNonLegacyMethod, this::addNonLegacyClass);
      }
      return this;
    }

    public Builder addNonLegacyClass(SyntheticProgramClassDefinition definition) {
      return addNonLegacyClass(definition.toReference());
    }

    public Builder addNonLegacyClass(SyntheticProgramClassReference reference) {
      if (newNonLegacyClasses == null) {
        newNonLegacyClasses = new IdentityHashMap<>();
      }
      newNonLegacyClasses
          .computeIfAbsent(reference.getHolder(), ignore -> new ArrayList<>())
          .add(reference);
      return this;
    }

    public Builder addNonLegacyMethod(SyntheticMethodDefinition definition) {
      return addNonLegacyMethod(definition.toReference());
    }

    public Builder addNonLegacyMethod(SyntheticMethodReference reference) {
      if (newNonLegacyMethods == null) {
        newNonLegacyMethods = new IdentityHashMap<>();
      }
      newNonLegacyMethods
          .computeIfAbsent(reference.getHolder(), ignore -> new ArrayList<>())
          .add(reference);
      return this;
    }

    public Builder addLegacyClasses(Map<DexType, LegacySyntheticDefinition> classes) {
      if (newLegacyClasses == null) {
        newLegacyClasses = new IdentityHashMap<>();
      }
      classes.forEach(
          (type, item) ->
              newLegacyClasses
                  .computeIfAbsent(type, ignore -> new ArrayList<>())
                  .add(item.toReference()));
      return this;
    }

    public Builder addLegacyClass(LegacySyntheticReference reference) {
      if (newLegacyClasses == null) {
        newLegacyClasses = new IdentityHashMap<>();
      }
      newLegacyClasses
          .computeIfAbsent(reference.getHolder(), ignore -> new ArrayList<>())
          .add(reference);
      return this;
    }

    public Builder addSyntheticInput(DexType syntheticInput) {
      if (newSyntheticInputs == null) {
        newSyntheticInputs = ImmutableSet.builder();
      }
      newSyntheticInputs.add(syntheticInput);
      return this;
    }

    Builder collectSyntheticInputs() {
      if (newSyntheticInputs == null) {
        newSyntheticInputs = ImmutableSet.builder();
      }
      if (newNonLegacyClasses != null) {
        newSyntheticInputs.addAll(newNonLegacyClasses.keySet());
      }
      if (newNonLegacyMethods != null) {
        newSyntheticInputs.addAll(newNonLegacyMethods.keySet());
      }
      if (newLegacyClasses != null) {
        newSyntheticInputs.addAll(newLegacyClasses.keySet());
      }
      return this;
    }

    public CommittedSyntheticsCollection build() {
      if (newNonLegacyClasses == null && newNonLegacyMethods == null && newLegacyClasses == null) {
        return parent;
      }
      ImmutableMap<DexType, List<SyntheticProgramClassReference>> allNonLegacyClasses =
          merge(newNonLegacyClasses, parent.nonLegacyClasses);
      ImmutableMap<DexType, List<SyntheticMethodReference>> allNonLegacyMethods =
          merge(newNonLegacyMethods, parent.nonLegacyMethods);
      ImmutableMap<DexType, List<LegacySyntheticReference>> allLegacyClasses =
          merge(newLegacyClasses, parent.legacyTypes);
      ImmutableSet<DexType> allSyntheticInputs =
          newSyntheticInputs == null ? parent.syntheticInputs : newSyntheticInputs.build();
      return new CommittedSyntheticsCollection(
          allLegacyClasses, allNonLegacyMethods, allNonLegacyClasses, allSyntheticInputs);
    }
  }

  private static <T> ImmutableMap<DexType, List<T>> merge(
      Map<DexType, List<T>> newSynthetics, ImmutableMap<DexType, List<T>> oldSynthetics) {
    if (newSynthetics == null) {
      return oldSynthetics;
    }
    oldSynthetics.forEach(
        (type, elements) ->
            newSynthetics.computeIfAbsent(type, ignore -> new ArrayList<>()).addAll(elements));
    return ImmutableMap.copyOf(newSynthetics);
  }

  private static final CommittedSyntheticsCollection EMPTY =
      new CommittedSyntheticsCollection(
          ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of(), ImmutableSet.of());

  /**
   * Immutable set of synthetic types in the application (eg, committed).
   *
   * <p>TODO(b/158159959): Remove legacy support.
   */
  private final ImmutableMap<DexType, List<LegacySyntheticReference>> legacyTypes;

  /** Mapping from synthetic type to its synthetic method item description. */
  private final ImmutableMap<DexType, List<SyntheticMethodReference>> nonLegacyMethods;

  /** Mapping from synthetic type to its synthetic class item description. */
  private final ImmutableMap<DexType, List<SyntheticProgramClassReference>> nonLegacyClasses;

  /** Set of synthetic types that were present in the input. */
  public final ImmutableSet<DexType> syntheticInputs;

  public CommittedSyntheticsCollection(
      ImmutableMap<DexType, List<LegacySyntheticReference>> legacyTypes,
      ImmutableMap<DexType, List<SyntheticMethodReference>> nonLegacyMethods,
      ImmutableMap<DexType, List<SyntheticProgramClassReference>> nonLegacyClasses,
      ImmutableSet<DexType> syntheticInputs) {
    this.legacyTypes = legacyTypes;
    this.nonLegacyMethods = nonLegacyMethods;
    this.nonLegacyClasses = nonLegacyClasses;
    this.syntheticInputs = syntheticInputs;
    assert verifySyntheticInputsSubsetOfSynthetics();
  }

  private boolean verifySyntheticInputsSubsetOfSynthetics() {
    Set<DexType> synthetics =
        ImmutableSet.<DexType>builder()
            .addAll(legacyTypes.keySet())
            .addAll(nonLegacyMethods.keySet())
            .addAll(nonLegacyClasses.keySet())
            .build();
    syntheticInputs.forEach(
        syntheticInput -> {
          assert synthetics.contains(syntheticInput)
              : "Expected " + syntheticInput.toSourceString() + " to be a synthetic";
        });
    return true;
  }

  public static CommittedSyntheticsCollection empty() {
    return EMPTY;
  }

  Builder builder() {
    return new Builder(this);
  }

  boolean isEmpty() {
    boolean empty =
        legacyTypes.isEmpty() && nonLegacyMethods.isEmpty() && nonLegacyClasses.isEmpty();
    assert !empty || syntheticInputs.isEmpty();
    return empty;
  }

  boolean containsType(DexType type) {
    return containsLegacyType(type) || containsNonLegacyType(type);
  }

  boolean containsTypeOfKind(DexType type, SyntheticKind kind) {
    List<SyntheticProgramClassReference> synthetics = nonLegacyClasses.get(type);
    if (synthetics == null) {
      return false;
    }
    for (SyntheticProgramClassReference synthetic : synthetics) {
      if (synthetic.getKind() == kind) {
        return true;
      }
    }
    return false;
  }

  public boolean containsLegacyType(DexType type) {
    return legacyTypes.containsKey(type);
  }

  public boolean containsNonLegacyType(DexType type) {
    return nonLegacyMethods.containsKey(type) || nonLegacyClasses.containsKey(type);
  }

  public boolean containsSyntheticInput(DexType type) {
    return syntheticInputs.contains(type);
  }

  public ImmutableMap<DexType, List<LegacySyntheticReference>> getLegacyTypes() {
    return legacyTypes;
  }

  public List<LegacySyntheticReference> getLegacyTypes(DexType type) {
    return legacyTypes.getOrDefault(type, emptyList());
  }

  public ImmutableMap<DexType, List<SyntheticMethodReference>> getNonLegacyMethods() {
    return nonLegacyMethods;
  }

  public ImmutableMap<DexType, List<SyntheticProgramClassReference>> getNonLegacyClasses() {
    return nonLegacyClasses;
  }

  public Iterable<SyntheticReference<?, ?, ?>> getNonLegacyItems(DexType type) {
    return Iterables.concat(
        nonLegacyClasses.getOrDefault(type, emptyList()),
        nonLegacyMethods.getOrDefault(type, emptyList()));
  }

  public void forEachSyntheticInput(Consumer<DexType> fn) {
    syntheticInputs.forEach(fn);
  }

  public void forEachNonLegacyItem(Consumer<SyntheticReference<?, ?, ?>> fn) {
    nonLegacyMethods.values().forEach(r -> r.forEach(fn));
    nonLegacyClasses.values().forEach(r -> r.forEach(fn));
  }

  CommittedSyntheticsCollection pruneItems(PrunedItems prunedItems) {
    Set<DexType> removed = prunedItems.getNoLongerSyntheticItems();
    if (removed.isEmpty()) {
      return this;
    }
    Builder builder = CommittedSyntheticsCollection.empty().builder();
    boolean changed = false;
    for (LegacySyntheticReference reference : IterableUtils.flatten(legacyTypes.values())) {
      if (removed.contains(reference.getHolder())) {
        changed = true;
      } else {
        builder.addLegacyClass(reference);
      }
    }
    for (SyntheticMethodReference reference : IterableUtils.flatten(nonLegacyMethods.values())) {
      if (removed.contains(reference.getHolder())) {
        changed = true;
      } else {
        builder.addNonLegacyMethod(reference);
      }
    }
    for (SyntheticProgramClassReference reference :
        IterableUtils.flatten(nonLegacyClasses.values())) {
      if (removed.contains(reference.getHolder())) {
        changed = true;
      } else {
        builder.addNonLegacyClass(reference);
      }
    }
    for (DexType syntheticInput : syntheticInputs) {
      if (removed.contains(syntheticInput)) {
        changed = true;
      } else {
        builder.addSyntheticInput(syntheticInput);
      }
    }
    return changed ? builder.build() : this;
  }

  CommittedSyntheticsCollection rewriteWithLens(NonIdentityGraphLens lens) {
    ImmutableSet.Builder<DexType> syntheticInputsBuilder = ImmutableSet.builder();
    return new CommittedSyntheticsCollection(
        rewriteItems(legacyTypes, lens, syntheticInputsBuilder),
        rewriteItems(nonLegacyMethods, lens, syntheticInputsBuilder),
        rewriteItems(nonLegacyClasses, lens, syntheticInputsBuilder),
        syntheticInputsBuilder.build());
  }

  private <R extends Rewritable<R>> ImmutableMap<DexType, List<R>> rewriteItems(
      Map<DexType, List<R>> items,
      NonIdentityGraphLens lens,
      ImmutableSet.Builder<DexType> syntheticInputsBuilder) {
    Map<DexType, List<R>> rewrittenItems = new IdentityHashMap<>();
    for (R reference : IterableUtils.flatten(items.values())) {
      R rewritten = reference.rewrite(lens);
      if (rewritten != null) {
        rewrittenItems
            .computeIfAbsent(rewritten.getHolder(), ignore -> new ArrayList<>())
            .add(rewritten);
        if (syntheticInputs.contains(reference.getHolder())) {
          syntheticInputsBuilder.add(rewritten.getHolder());
        }
      }
    }
    return ImmutableMap.copyOf(rewrittenItems);
  }

  boolean verifyTypesAreInApp(DexApplication application) {
    assert verifyTypesAreInApp(application, legacyTypes.keySet());
    assert verifyTypesAreInApp(application, nonLegacyMethods.keySet());
    assert verifyTypesAreInApp(application, nonLegacyClasses.keySet());
    assert verifyTypesAreInApp(application, syntheticInputs);
    return true;
  }

  private static boolean verifyTypesAreInApp(DexApplication app, Collection<DexType> types) {
    for (DexType type : types) {
      assert app.programDefinitionFor(type) != null : "Missing synthetic: " + type;
    }
    return true;
  }
}
