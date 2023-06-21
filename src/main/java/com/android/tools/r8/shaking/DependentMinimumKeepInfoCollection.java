// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.shaking.EnqueuerEvent.ClassEnqueuerEvent;
import com.android.tools.r8.shaking.EnqueuerEvent.UnconditionalKeepInfoEvent;
import com.android.tools.r8.shaking.KeepInfo.Joiner;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.TriConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

public abstract class DependentMinimumKeepInfoCollection {

  private Map<EnqueuerEvent, MinimumKeepInfoCollection> dependentMinimumKeepInfo;

  DependentMinimumKeepInfoCollection() {
    this.dependentMinimumKeepInfo = createBacking();
  }

  DependentMinimumKeepInfoCollection(int capacity) {
    this.dependentMinimumKeepInfo = createBacking(capacity);
  }

  public static DependentMinimumKeepInfoCollection create() {
    return new HashDependentMinimumKeepInfoCollection();
  }

  public static DependentMinimumKeepInfoCollection create(int capacity) {
    return new HashDependentMinimumKeepInfoCollection(capacity);
  }

  public static DependentMinimumKeepInfoCollection createConcurrent() {
    return new ConcurrentDependentMinimumKeepInfoCollection();
  }

  public abstract Map<EnqueuerEvent, MinimumKeepInfoCollection> createBacking();

  public abstract Map<EnqueuerEvent, MinimumKeepInfoCollection> createBacking(int capacity);

  public abstract MinimumKeepInfoCollection createInnerBacking();

  public void forEach(BiConsumer<EnqueuerEvent, MinimumKeepInfoCollection> consumer) {
    dependentMinimumKeepInfo.forEach(consumer);
  }

  public void forEach(
      DexDefinitionSupplier definitions,
      TriConsumer<EnqueuerEvent, DexProgramClass, KeepClassInfo.Joiner> classConsumer,
      TriConsumer<EnqueuerEvent, ProgramField, KeepFieldInfo.Joiner> fieldConsumer,
      TriConsumer<EnqueuerEvent, ProgramMethod, KeepMethodInfo.Joiner> methodConsumer) {
    dependentMinimumKeepInfo.forEach(
        (preconditionEvent, minimumKeepInfo) ->
            minimumKeepInfo.forEach(
                definitions,
                (clazz, minimumKeepInfoForClass) ->
                    classConsumer.accept(preconditionEvent, clazz, minimumKeepInfoForClass),
                (field, minimumKeepInfoForField) ->
                    fieldConsumer.accept(preconditionEvent, field, minimumKeepInfoForField),
                (method, minimumKeepInfoForMethod) ->
                    methodConsumer.accept(preconditionEvent, method, minimumKeepInfoForMethod)));
  }

  public MinimumKeepInfoCollection get(EnqueuerEvent preconditionEvent) {
    return dependentMinimumKeepInfo.get(preconditionEvent);
  }

  public MinimumKeepInfoCollection getOrCreateMinimumKeepInfoFor(EnqueuerEvent preconditionEvent) {
    return dependentMinimumKeepInfo.computeIfAbsent(
        preconditionEvent, ignoreKey(this::createInnerBacking));
  }

  public Joiner<?, ?, ?> getOrCreateMinimumKeepInfoFor(
      EnqueuerEvent preconditionEvent, DexReference reference) {
    return getOrCreateMinimumKeepInfoFor(preconditionEvent)
        .getOrCreateMinimumKeepInfoFor(reference);
  }

  public MinimumKeepInfoCollection getOrCreateUnconditionalMinimumKeepInfo() {
    return getOrCreateMinimumKeepInfoFor(UnconditionalKeepInfoEvent.get());
  }

  public Joiner<?, ?, ?> getOrCreateUnconditionalMinimumKeepInfoFor(DexReference reference) {
    return getOrCreateMinimumKeepInfoFor(UnconditionalKeepInfoEvent.get(), reference);
  }

  public MinimumKeepInfoCollection getOrDefault(
      EnqueuerEvent preconditionEvent, MinimumKeepInfoCollection defaultValue) {
    return dependentMinimumKeepInfo.getOrDefault(preconditionEvent, defaultValue);
  }

  public MinimumKeepInfoCollection getUnconditionalMinimumKeepInfoOrDefault(
      MinimumKeepInfoCollection defaultValue) {
    return getOrDefault(UnconditionalKeepInfoEvent.get(), defaultValue);
  }

  public void merge(DependentMinimumKeepInfoCollection otherDependentMinimumKeepInfo) {
    otherDependentMinimumKeepInfo.forEach(
        (preconditionEvent, minimumKeepInfo) ->
            getOrCreateMinimumKeepInfoFor(preconditionEvent).merge(minimumKeepInfo));
  }

  public void pruneDeadItems(DexDefinitionSupplier definitions, Enqueuer enqueuer) {
    MapUtils.removeIf(
        dependentMinimumKeepInfo,
        (preconditionEvent, minimumKeepInfo) -> {
          // Check if the precondition refers to a pruned type.
          if (preconditionEvent.isClassEvent()) {
            ClassEnqueuerEvent classPreconditionEvent = preconditionEvent.asClassEvent();
            DexClass clazz = definitions.definitionFor(classPreconditionEvent.getType());
            if (clazz == null || !enqueuer.isReachable(clazz)) {
              return true;
            }
          } else {
            assert preconditionEvent.isUnconditionalKeepInfoEvent();
          }

          // Prune the consequent minimum keep info.
          assert !minimumKeepInfo.isEmpty();
          minimumKeepInfo.pruneDeadItems(definitions, enqueuer);

          // If the consequent minimum keep info ended up empty, then remove the preconditionEvent
          // from the dependent minimum keep info collection.
          return minimumKeepInfo.isEmpty();
        });
  }

  public MinimumKeepInfoCollection remove(EnqueuerEvent preconditionEvent) {
    return dependentMinimumKeepInfo.remove(preconditionEvent);
  }

  public KeepClassInfo.Joiner remove(EnqueuerEvent preconditionEvent, DexType clazz) {
    return internalRemove(preconditionEvent, minimumKeepInfo -> minimumKeepInfo.remove(clazz));
  }

  public KeepFieldInfo.Joiner remove(EnqueuerEvent preconditionEvent, DexField field) {
    return internalRemove(preconditionEvent, minimumKeepInfo -> minimumKeepInfo.remove(field));
  }

  public KeepMethodInfo.Joiner remove(EnqueuerEvent preconditionEvent, DexMethod method) {
    return internalRemove(preconditionEvent, minimumKeepInfo -> minimumKeepInfo.remove(method));
  }

  private <J extends Joiner<?, ?, ?>> J internalRemove(
      EnqueuerEvent preconditionEvent, Function<MinimumKeepInfoCollection, J> fn) {
    MinimumKeepInfoCollection minimumKeepInfo = get(preconditionEvent);
    if (minimumKeepInfo == null) {
      return null;
    }
    J minimumKeepInfoForReference = fn.apply(minimumKeepInfo);
    if (minimumKeepInfo.isEmpty()) {
      remove(preconditionEvent);
    }
    return minimumKeepInfoForReference;
  }

  public DependentMinimumKeepInfoCollection rewrittenWithLens(GraphLens graphLens, Timing timing) {
    timing.begin("Rewrite DependentMinimumKeepInfoCollection");
    DependentMinimumKeepInfoCollection rewrittenDependentMinimumKeepInfo = create(size());
    forEach(
        (preconditionEvent, minimumKeepInfo) -> {
          EnqueuerEvent rewrittenPreconditionEvent = preconditionEvent.rewrittenWithLens(graphLens);
          if (!rewrittenPreconditionEvent.isNoSuchEvent()) {
            rewrittenDependentMinimumKeepInfo
                .getOrCreateMinimumKeepInfoFor(rewrittenPreconditionEvent)
                .merge(minimumKeepInfo.rewrittenWithLens(graphLens));
          }
        });
    timing.end();
    return rewrittenDependentMinimumKeepInfo;
  }

  public int size() {
    return dependentMinimumKeepInfo.size();
  }

  private static class ConcurrentDependentMinimumKeepInfoCollection
      extends DependentMinimumKeepInfoCollection {

    @Override
    public Map<EnqueuerEvent, MinimumKeepInfoCollection> createBacking() {
      return new ConcurrentHashMap<>();
    }

    @Override
    public Map<EnqueuerEvent, MinimumKeepInfoCollection> createBacking(int capacity) {
      return new ConcurrentHashMap<>(capacity);
    }

    @Override
    public MinimumKeepInfoCollection createInnerBacking() {
      return MinimumKeepInfoCollection.createConcurrent();
    }
  }

  private static class HashDependentMinimumKeepInfoCollection
      extends DependentMinimumKeepInfoCollection {

    HashDependentMinimumKeepInfoCollection() {
      super();
    }

    HashDependentMinimumKeepInfoCollection(int capacity) {
      super(capacity);
    }

    @Override
    public Map<EnqueuerEvent, MinimumKeepInfoCollection> createBacking() {
      return new HashMap<>();
    }

    @Override
    public Map<EnqueuerEvent, MinimumKeepInfoCollection> createBacking(int capacity) {
      return new HashMap<>(capacity);
    }

    @Override
    public MinimumKeepInfoCollection createInnerBacking() {
      return MinimumKeepInfoCollection.create();
    }
  }
}
