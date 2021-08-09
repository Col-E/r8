// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.EnqueuerEvent.ClassEnqueuerEvent;
import com.android.tools.r8.shaking.EnqueuerEvent.UnconditionalKeepInfoEvent;
import com.android.tools.r8.shaking.KeepMethodInfo.Joiner;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.TriConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class DependentMinimumKeepInfoCollection {

  private final Map<EnqueuerEvent, MinimumKeepInfoCollection> dependentMinimumKeepInfo;

  public DependentMinimumKeepInfoCollection() {
    this(new HashMap<>());
  }

  private DependentMinimumKeepInfoCollection(
      Map<EnqueuerEvent, MinimumKeepInfoCollection> dependentMinimumKeepInfo) {
    this.dependentMinimumKeepInfo = dependentMinimumKeepInfo;
  }

  public void forEach(BiConsumer<EnqueuerEvent, MinimumKeepInfoCollection> consumer) {
    dependentMinimumKeepInfo.forEach(consumer);
  }

  public void forEach(
      DexDefinitionSupplier definitions,
      TriConsumer<EnqueuerEvent, DexProgramClass, KeepClassInfo.Joiner> classConsumer,
      TriConsumer<EnqueuerEvent, ProgramField, KeepFieldInfo.Joiner> fieldConsumer,
      TriConsumer<EnqueuerEvent, ProgramMethod, Joiner> methodConsumer) {
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

  public MinimumKeepInfoCollection getOrCreateMinimumKeepInfoFor(EnqueuerEvent preconditionEvent) {
    return dependentMinimumKeepInfo.computeIfAbsent(
        preconditionEvent, ignoreKey(MinimumKeepInfoCollection::new));
  }

  public KeepInfo.Joiner<?, ?, ?> getOrCreateMinimumKeepInfoFor(
      EnqueuerEvent preconditionEvent, DexReference reference) {
    return getOrCreateMinimumKeepInfoFor(preconditionEvent)
        .getOrCreateMinimumKeepInfoFor(reference);
  }

  public MinimumKeepInfoCollection getOrCreateUnconditionalMinimumKeepInfo() {
    return getOrCreateMinimumKeepInfoFor(UnconditionalKeepInfoEvent.get());
  }

  public KeepInfo.Joiner<?, ?, ?> getOrCreateUnconditionalMinimumKeepInfoFor(
      DexReference reference) {
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

  public DependentMinimumKeepInfoCollection rewrittenWithLens(GraphLens graphLens) {
    DependentMinimumKeepInfoCollection rewrittenDependentMinimumKeepInfo =
        new DependentMinimumKeepInfoCollection();
    forEach(
        (preconditionEvent, minimumKeepInfo) ->
            rewrittenDependentMinimumKeepInfo
                .getOrCreateMinimumKeepInfoFor(preconditionEvent.rewrittenWithLens(graphLens))
                .merge(minimumKeepInfo.rewrittenWithLens(graphLens)));
    return rewrittenDependentMinimumKeepInfo;
  }
}
