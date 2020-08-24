// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.graph.GraphLens.NestedGraphLens;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class SyntheticItems implements SyntheticDefinitionsProvider {

  public static class CommittedItems implements SyntheticDefinitionsProvider {
    // Set of all types that represent synthesized items.
    private final ImmutableSet<DexType> syntheticTypes;

    private CommittedItems(ImmutableSet<DexType> syntheticTypes) {
      this.syntheticTypes = syntheticTypes;
    }

    SyntheticItems toSyntheticItems() {
      return new SyntheticItems(syntheticTypes);
    }

    @Override
    public DexClass definitionFor(DexType type, Function<DexType, DexClass> baseDefinitionFor) {
      return baseDefinitionFor.apply(type);
    }
  }

  // Thread safe collection of synthesized classes that are not yet committed to the application.
  private final Map<DexType, DexProgramClass> pendingClasses = new ConcurrentHashMap<>();

  // Immutable set of types that represent synthetic definitions in the application (eg, committed).
  private final ImmutableSet<DexType> syntheticTypes;

  private SyntheticItems(ImmutableSet<DexType> syntheticTypes) {
    this.syntheticTypes = syntheticTypes;
  }

  public static SyntheticItems createInitialSyntheticItems() {
    return new SyntheticItems(ImmutableSet.of());
  }

  public boolean hasPendingSyntheticClasses() {
    return !pendingClasses.isEmpty();
  }

  public Collection<DexProgramClass> getPendingSyntheticClasses() {
    return Collections.unmodifiableCollection(pendingClasses.values());
  }

  @Override
  public DexClass definitionFor(DexType type, Function<DexType, DexClass> baseDefinitionFor) {
    DexProgramClass pending = pendingClasses.get(type);
    if (pending != null) {
      assert baseDefinitionFor.apply(type) == null
          : "Pending synthetic definition also present in the active program: " + type;
      return pending;
    }
    return baseDefinitionFor.apply(type);
  }

  // TODO(b/158159959): Remove the usage of this direct class addition (and name-based id).
  public void addSyntheticClass(DexProgramClass clazz) {
    assert clazz.type.isD8R8SynthesizedClassType();
    assert !syntheticTypes.contains(clazz.type);
    DexProgramClass previous = pendingClasses.put(clazz.type, clazz);
    assert previous == null || previous == clazz;
  }

  public boolean isSyntheticClass(DexType type) {
    return syntheticTypes.contains(type)
        || pendingClasses.containsKey(type)
        // TODO(b/158159959): Remove usage of name-based identification.
        || type.isD8R8SynthesizedClassType();
  }

  public boolean isSyntheticClass(DexProgramClass clazz) {
    return isSyntheticClass(clazz.type);
  }

  public CommittedItems commit(DexApplication application) {
    assert verifyAllPendingSyntheticsAreInApp(application, this);
    // All synthetics are in the app proper and no further meta-data is present so the empty
    // collection is currently returned here.
    ImmutableSet<DexType> merged = syntheticTypes;
    if (!pendingClasses.isEmpty()) {
      merged =
          ImmutableSet.<DexType>builder()
              .addAll(syntheticTypes)
              .addAll(pendingClasses.keySet())
              .build();
    }
    return new CommittedItems(merged);
  }

  public CommittedItems commit(DexApplication application, NestedGraphLens lens) {
    return new CommittedItems(lens.rewriteTypes(commit(application).syntheticTypes));
  }

  private static boolean verifyAllPendingSyntheticsAreInApp(
      DexApplication app, SyntheticItems synthetics) {
    for (DexProgramClass clazz : synthetics.getPendingSyntheticClasses()) {
      assert app.programDefinitionFor(clazz.type) != null;
    }
    return true;
  }
}
