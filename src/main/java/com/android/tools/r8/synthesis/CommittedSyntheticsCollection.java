// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import static java.util.Collections.emptyList;

import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.synthesis.SyntheticItems.ContextsForGlobalSynthetics;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
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
    private Map<DexType, List<SyntheticProgramClassReference>> classes = null;
    private Map<DexType, List<SyntheticMethodReference>> methods = null;
    private ImmutableSet.Builder<DexType> newSyntheticInputs = null;
    private Map<DexType, Set<DexType>> globalContexts = null;

    public Builder(CommittedSyntheticsCollection parent) {
      this.parent = parent;
    }

    public Builder addItem(SyntheticDefinition<?, ?, ?> definition) {
      if (definition.isProgramDefinition()) {
        definition.asProgramDefinition().apply(this::addMethod, this::addClass);
      }
      return this;
    }

    public Builder addClass(SyntheticProgramClassDefinition definition) {
      return addClass(definition.toReference());
    }

    public Builder addClass(SyntheticProgramClassReference reference) {
      if (classes == null) {
        classes = new IdentityHashMap<>();
      }
      classes.computeIfAbsent(reference.getHolder(), ignore -> new ArrayList<>()).add(reference);
      return this;
    }

    public Builder addMethod(SyntheticMethodDefinition definition) {
      return addMethod(definition.toReference());
    }

    public Builder addMethod(SyntheticMethodReference reference) {
      if (methods == null) {
        methods = new IdentityHashMap<>();
      }
      methods.computeIfAbsent(reference.getHolder(), ignore -> new ArrayList<>()).add(reference);
      return this;
    }

    public Builder addSyntheticInput(DexType syntheticInput) {
      ensureNewSyntheticInputs().add(syntheticInput);
      return this;
    }

    Builder collectSyntheticInputs() {
      if (classes != null) {
        ensureNewSyntheticInputs().addAll(classes.keySet());
      }
      if (methods != null) {
        ensureNewSyntheticInputs().addAll(methods.keySet());
      }
      return this;
    }

    private ImmutableSet.Builder<DexType> ensureNewSyntheticInputs() {
      if (newSyntheticInputs == null) {
        newSyntheticInputs = ImmutableSet.builder();
      }
      return newSyntheticInputs;
    }

    public Builder addGlobalContexts(ContextsForGlobalSynthetics additionalGlobalContexts) {
      if (!additionalGlobalContexts.isEmpty()) {
        if (globalContexts == null) {
          globalContexts = new IdentityHashMap<>();
        }
        additionalGlobalContexts.forEach(
            (globalType, contexts) ->
                globalContexts
                    .computeIfAbsent(globalType, k -> SetUtils.newIdentityHashSet())
                    .addAll(contexts));
      }
      return this;
    }

    public CommittedSyntheticsCollection build() {
      if (classes == null && methods == null && globalContexts == null) {
        // Adding synthetic inputs implies that an actual synthetic is added too.
        assert newSyntheticInputs == null;
        return parent;
      }
      ImmutableMap<DexType, List<SyntheticProgramClassReference>> allClasses =
          mergeMapOfLists(classes, parent.classes);
      ImmutableMap<DexType, List<SyntheticMethodReference>> allMethods =
          mergeMapOfLists(methods, parent.methods);
      ImmutableSet<DexType> allSyntheticInputs =
          newSyntheticInputs == null ? parent.syntheticInputs : newSyntheticInputs.build();
      ImmutableMap<DexType, Set<DexType>> allGlobalContexts =
          globalContexts == null
              ? parent.globalContexts
              : mergeMapOfSets(globalContexts, parent.globalContexts);
      return new CommittedSyntheticsCollection(
          parent.naming, allMethods, allClasses, allGlobalContexts, allSyntheticInputs);
    }
  }

  private static <T> ImmutableMap<DexType, List<T>> mergeMapOfLists(
      Map<DexType, List<T>> newSynthetics, ImmutableMap<DexType, List<T>> oldSynthetics) {
    if (newSynthetics == null) {
      return oldSynthetics;
    }
    oldSynthetics.forEach(
        (type, elements) ->
            newSynthetics.computeIfAbsent(type, ignore -> new ArrayList<>()).addAll(elements));
    return ImmutableMap.copyOf(newSynthetics);
  }

  private static <T> ImmutableMap<DexType, Set<T>> mergeMapOfSets(
      Map<DexType, Set<T>> newSynthetics, ImmutableMap<DexType, Set<T>> oldSynthetics) {
    if (newSynthetics == null) {
      return oldSynthetics;
    }
    oldSynthetics.forEach(
        (type, elements) ->
            newSynthetics.computeIfAbsent(type, ignore -> new HashSet<>()).addAll(elements));
    return ImmutableMap.copyOf(newSynthetics);
  }

  private final SyntheticNaming naming;

  /** Mapping from synthetic type to its synthetic method item description. */
  private final ImmutableMap<DexType, List<SyntheticMethodReference>> methods;

  /** Mapping from synthetic type to its synthetic class item description. */
  private final ImmutableMap<DexType, List<SyntheticProgramClassReference>> classes;

  /** Mapping from global synthetic type to its synthesizing contexts. */
  private final ImmutableMap<DexType, Set<DexType>> globalContexts;

  /** Set of synthetic types that were present in the input. */
  public final ImmutableSet<DexType> syntheticInputs;

  public CommittedSyntheticsCollection(
      SyntheticNaming naming,
      ImmutableMap<DexType, List<SyntheticMethodReference>> methods,
      ImmutableMap<DexType, List<SyntheticProgramClassReference>> classes,
      ImmutableMap<DexType, Set<DexType>> globalContexts,
      ImmutableSet<DexType> syntheticInputs) {
    this.naming = naming;
    this.methods = methods;
    this.classes = classes;
    this.globalContexts = globalContexts;
    this.syntheticInputs = syntheticInputs;
    assert verifySyntheticInputsSubsetOfSynthetics();
  }

  SyntheticNaming getNaming() {
    return naming;
  }

  private boolean verifySyntheticInputsSubsetOfSynthetics() {
    Set<DexType> synthetics =
        ImmutableSet.<DexType>builder().addAll(methods.keySet()).addAll(classes.keySet()).build();
    syntheticInputs.forEach(
        syntheticInput -> {
          assert synthetics.contains(syntheticInput)
              : "Expected " + syntheticInput.toSourceString() + " to be a synthetic";
        });
    return true;
  }

  public static CommittedSyntheticsCollection empty(SyntheticNaming naming) {
    return new CommittedSyntheticsCollection(
        naming, ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of(), ImmutableSet.of());
  }

  Builder builder() {
    return new Builder(this);
  }

  boolean isEmpty() {
    boolean empty = methods.isEmpty() && classes.isEmpty();
    assert !empty || syntheticInputs.isEmpty();
    return empty;
  }

  public boolean containsType(DexType type) {
    return methods.containsKey(type) || classes.containsKey(type);
  }

  @SuppressWarnings("ReferenceEquality")
  boolean containsTypeOfKind(DexType type, SyntheticKind kind) {
    List<SyntheticProgramClassReference> synthetics = classes.get(type);
    if (synthetics == null) {
      List<SyntheticMethodReference> syntheticMethodReferences = methods.get(type);
      if (syntheticMethodReferences == null) {
        return false;
      }
      for (SyntheticMethodReference syntheticMethodReference : syntheticMethodReferences) {
        if (syntheticMethodReference.getKind() == kind) {
          return true;
        }
      }
      return false;
    }
    for (SyntheticProgramClassReference synthetic : synthetics) {
      if (synthetic.getKind() == kind) {
        return true;
      }
    }
    return false;
  }

  public boolean containsSyntheticInput(DexType type) {
    return syntheticInputs.contains(type);
  }

  public Set<DexType> getContextsForGlobal(DexType globalSynthetic) {
    return globalContexts.get(globalSynthetic);
  }

  public ImmutableMap<DexType, Set<DexType>> getGlobalContexts() {
    return globalContexts;
  }

  public ImmutableMap<DexType, List<SyntheticMethodReference>> getMethods() {
    return methods;
  }

  public ImmutableMap<DexType, List<SyntheticProgramClassReference>> getClasses() {
    return classes;
  }

  public Iterable<SyntheticReference<?, ?, ?>> getItems(DexType type) {
    return Iterables.concat(
        classes.getOrDefault(type, emptyList()), methods.getOrDefault(type, emptyList()));
  }

  public void forEachSyntheticInput(Consumer<DexType> fn) {
    syntheticInputs.forEach(fn);
  }

  public void forEachItem(Consumer<SyntheticReference<?, ?, ?>> fn) {
    methods.values().forEach(r -> r.forEach(fn));
    classes.values().forEach(r -> r.forEach(fn));
  }

  CommittedSyntheticsCollection pruneItems(PrunedItems prunedItems) {
    Set<DexType> removed = prunedItems.getNoLongerSyntheticItems();
    if (removed.isEmpty()) {
      return this;
    }
    Builder builder = CommittedSyntheticsCollection.empty(naming).builder();
    boolean changed = false;
    for (SyntheticMethodReference reference : IterableUtils.flatten(methods.values())) {
      if (removed.contains(reference.getHolder())) {
        changed = true;
      } else {
        builder.addMethod(reference);
      }
    }
    for (SyntheticProgramClassReference reference : IterableUtils.flatten(classes.values())) {
      if (removed.contains(reference.getHolder())) {
        changed = true;
      } else {
        builder.addClass(reference);
      }
    }
    for (DexType syntheticInput : syntheticInputs) {
      if (removed.contains(syntheticInput)) {
        changed = true;
      } else {
        builder.addSyntheticInput(syntheticInput);
      }
    }
    // Global synthetic contexts are only collected for per-file modes which only prune synthetic
    // items, not inputs.
    assert globalContexts.isEmpty()
        || prunedItems.getNoLongerSyntheticItems().size() == prunedItems.getRemovedClasses().size();
    return changed ? builder.build() : this;
  }

  CommittedSyntheticsCollection rewriteWithLens(NonIdentityGraphLens lens, Timing timing) {
    ImmutableSet.Builder<DexType> syntheticInputsBuilder = ImmutableSet.builder();
    return new CommittedSyntheticsCollection(
        naming,
        rewriteItems(methods, lens, syntheticInputsBuilder),
        rewriteItems(classes, lens, syntheticInputsBuilder),
        globalContexts,
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
    assert verifyTypesAreInApp(application, methods.keySet());
    assert verifyTypesAreInApp(application, classes.keySet());
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
