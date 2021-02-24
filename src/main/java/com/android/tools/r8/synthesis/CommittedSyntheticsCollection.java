// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.utils.BooleanBox;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.Collection;
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
    private ImmutableMap.Builder<DexType, SyntheticProgramClassReference> newNonLegacyClasses =
        null;
    private ImmutableMap.Builder<DexType, SyntheticMethodReference> newNonLegacyMethods = null;
    private ImmutableMap.Builder<DexType, LegacySyntheticReference> newLegacyClasses = null;

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
        newNonLegacyClasses = ImmutableMap.builder();
      }
      newNonLegacyClasses.put(reference.getHolder(), reference);
      return this;
    }

    public Builder addNonLegacyMethod(SyntheticMethodDefinition definition) {
      return addNonLegacyMethod(definition.toReference());
    }

    public Builder addNonLegacyMethod(SyntheticMethodReference reference) {
      if (newNonLegacyMethods == null) {
        newNonLegacyMethods = ImmutableMap.builder();
      }
      newNonLegacyMethods.put(reference.getHolder(), reference);
      return this;
    }

    public Builder addLegacyClasses(Map<DexType, LegacySyntheticDefinition> classes) {
      if (newLegacyClasses == null) {
        newLegacyClasses = ImmutableMap.builder();
      }
      classes.forEach((type, item) -> newLegacyClasses.put(type, item.toReference()));
      return this;
    }

    public Builder addLegacyClass(LegacySyntheticReference item) {
      if (newLegacyClasses == null) {
        newLegacyClasses = ImmutableMap.builder();
      }
      newLegacyClasses.put(item.getHolder(), item);
      return this;
    }

    public CommittedSyntheticsCollection build() {
      if (newNonLegacyClasses == null && newNonLegacyMethods == null && newLegacyClasses == null) {
        return parent;
      }
      ImmutableMap<DexType, SyntheticProgramClassReference> allNonLegacyClasses =
          newNonLegacyClasses == null
              ? parent.nonLegacyClasses
              : newNonLegacyClasses.putAll(parent.nonLegacyClasses).build();
      ImmutableMap<DexType, SyntheticMethodReference> allNonLegacyMethods =
          newNonLegacyMethods == null
              ? parent.nonLegacyMethods
              : newNonLegacyMethods.putAll(parent.nonLegacyMethods).build();
      ImmutableMap<DexType, LegacySyntheticReference> allLegacyClasses =
          newLegacyClasses == null
              ? parent.legacyTypes
              : newLegacyClasses.putAll(parent.legacyTypes).build();
      return new CommittedSyntheticsCollection(
          allLegacyClasses, allNonLegacyMethods, allNonLegacyClasses);
    }
  }

  private static final CommittedSyntheticsCollection EMPTY =
      new CommittedSyntheticsCollection(ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of());

  /**
   * Immutable set of synthetic types in the application (eg, committed).
   *
   * <p>TODO(b/158159959): Remove legacy support.
   */
  private final ImmutableMap<DexType, LegacySyntheticReference> legacyTypes;

  /** Mapping from synthetic type to its synthetic method item description. */
  private final ImmutableMap<DexType, SyntheticMethodReference> nonLegacyMethods;

  /** Mapping from synthetic type to its synthetic class item description. */
  private final ImmutableMap<DexType, SyntheticProgramClassReference> nonLegacyClasses;

  public CommittedSyntheticsCollection(
      ImmutableMap<DexType, LegacySyntheticReference> legacyTypes,
      ImmutableMap<DexType, SyntheticMethodReference> nonLegacyMethods,
      ImmutableMap<DexType, SyntheticProgramClassReference> nonLegacyClasses) {
    this.legacyTypes = legacyTypes;
    this.nonLegacyMethods = nonLegacyMethods;
    this.nonLegacyClasses = nonLegacyClasses;
    assert legacyTypes.size() + nonLegacyMethods.size() + nonLegacyClasses.size()
        == Sets.union(
                Sets.union(nonLegacyMethods.keySet(), nonLegacyClasses.keySet()),
                legacyTypes.keySet())
            .size();
  }

  public static CommittedSyntheticsCollection empty() {
    return EMPTY;
  }

  Builder builder() {
    return new Builder(this);
  }

  boolean isEmpty() {
    return legacyTypes.isEmpty() && nonLegacyMethods.isEmpty() && nonLegacyClasses.isEmpty();
  }

  boolean containsType(DexType type) {
    return containsLegacyType(type) || containsNonLegacyType(type);
  }

  public boolean containsLegacyType(DexType type) {
    return legacyTypes.containsKey(type);
  }

  public boolean containsNonLegacyType(DexType type) {
    return nonLegacyMethods.containsKey(type) || nonLegacyClasses.containsKey(type);
  }

  public ImmutableMap<DexType, LegacySyntheticReference> getLegacyTypes() {
    return legacyTypes;
  }

  public ImmutableMap<DexType, SyntheticMethodReference> getNonLegacyMethods() {
    return nonLegacyMethods;
  }

  public ImmutableMap<DexType, SyntheticProgramClassReference> getNonLegacyClasses() {
    return nonLegacyClasses;
  }

  public SyntheticReference<?, ?, ?> getNonLegacyItem(DexType type) {
    SyntheticMethodReference reference = nonLegacyMethods.get(type);
    if (reference != null) {
      return reference;
    }
    return nonLegacyClasses.get(type);
  }

  public void forEachNonLegacyItem(Consumer<SyntheticReference<?, ?, ?>> fn) {
    nonLegacyMethods.forEach((t, r) -> fn.accept(r));
    nonLegacyClasses.forEach((t, r) -> fn.accept(r));
  }

  CommittedSyntheticsCollection pruneItems(PrunedItems prunedItems) {
    Set<DexType> removed = prunedItems.getNoLongerSyntheticItems();
    if (removed.isEmpty()) {
      return this;
    }
    Builder builder = CommittedSyntheticsCollection.empty().builder();
    BooleanBox changed = new BooleanBox(false);
    legacyTypes.forEach(
        (type, item) -> {
          if (removed.contains(type)) {
            changed.set();
          } else {
            builder.addLegacyClass(item);
          }
        });
    for (SyntheticMethodReference reference : nonLegacyMethods.values()) {
      if (removed.contains(reference.getHolder())) {
        changed.set();
      } else {
        builder.addNonLegacyMethod(reference);
      }
    }
    for (SyntheticProgramClassReference reference : nonLegacyClasses.values()) {
      if (removed.contains(reference.getHolder())) {
        changed.set();
      } else {
        builder.addNonLegacyClass(reference);
      }
    }
    return changed.isTrue() ? builder.build() : this;
  }

  CommittedSyntheticsCollection rewriteWithLens(NonIdentityGraphLens lens) {
    return new CommittedSyntheticsCollection(
        rewriteItems(legacyTypes, lens),
        rewriteItems(nonLegacyMethods, lens),
        rewriteItems(nonLegacyClasses, lens));
  }

  private static <R extends Rewritable<R>> ImmutableMap<DexType, R> rewriteItems(
      Map<DexType, R> items, NonIdentityGraphLens lens) {
    ImmutableMap.Builder<DexType, R> rewrittenItems = ImmutableMap.builder();
    for (R reference : items.values()) {
      R rewritten = reference.rewrite(lens);
      if (rewritten != null) {
        rewrittenItems.put(rewritten.getHolder(), rewritten);
      }
    }
    return rewrittenItems.build();
  }

  boolean verifyTypesAreInApp(DexApplication application) {
    assert verifyTypesAreInApp(application, legacyTypes.keySet());
    assert verifyTypesAreInApp(application, nonLegacyMethods.keySet());
    assert verifyTypesAreInApp(application, nonLegacyClasses.keySet());
    return true;
  }

  private static boolean verifyTypesAreInApp(DexApplication app, Collection<DexType> types) {
    for (DexType type : types) {
      assert app.programDefinitionFor(type) != null : "Missing synthetic: " + type;
    }
    return true;
  }
}
