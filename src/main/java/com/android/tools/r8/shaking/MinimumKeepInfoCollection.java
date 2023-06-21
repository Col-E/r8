// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.shaking.KeepInfo.Joiner;
import com.android.tools.r8.utils.MapUtils;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class MinimumKeepInfoCollection {

  private static final MinimumKeepInfoCollection EMPTY =
      new MinimumKeepInfoCollection(Collections.emptyMap());

  private final Map<DexReference, KeepInfo.Joiner<?, ?, ?>> minimumKeepInfo;

  private MinimumKeepInfoCollection(Map<DexReference, KeepInfo.Joiner<?, ?, ?>> minimumKeepInfo) {
    this.minimumKeepInfo = minimumKeepInfo;
  }

  public static MinimumKeepInfoCollection create() {
    return new MinimumKeepInfoCollection(new IdentityHashMap<>());
  }

  public static MinimumKeepInfoCollection create(int capacity) {
    return new MinimumKeepInfoCollection(new IdentityHashMap<>(capacity));
  }

  public static MinimumKeepInfoCollection createConcurrent() {
    return new MinimumKeepInfoCollection(new ConcurrentHashMap<>());
  }

  public static MinimumKeepInfoCollection empty() {
    return EMPTY;
  }

  public void forEach(BiConsumer<DexReference, KeepInfo.Joiner<?, ?, ?>> consumer) {
    minimumKeepInfo.forEach(consumer);
  }

  public void forEach(
      DexDefinitionSupplier definitions,
      BiConsumer<DexProgramClass, KeepClassInfo.Joiner> classConsumer,
      BiConsumer<ProgramField, KeepFieldInfo.Joiner> fieldConsumer,
      BiConsumer<ProgramMethod, KeepMethodInfo.Joiner> methodConsumer) {
    minimumKeepInfo.forEach(
        (reference, joiner) -> {
          DexProgramClass contextClass =
              asProgramClassOrNull(definitions.definitionFor(reference.getContextType()));
          if (contextClass != null) {
            reference.accept(
                clazz -> classConsumer.accept(contextClass, joiner.asClassJoiner()),
                fieldReference -> {
                  ProgramField field = contextClass.lookupProgramField(fieldReference);
                  if (field != null) {
                    fieldConsumer.accept(field, joiner.asFieldJoiner());
                  }
                },
                methodReference -> {
                  ProgramMethod method = contextClass.lookupProgramMethod(methodReference);
                  if (method != null) {
                    methodConsumer.accept(method, joiner.asMethodJoiner());
                  }
                });
          }
        });
  }

  @SuppressWarnings("unchecked")
  public <T extends DexReference> void forEachThatMatches(
      BiPredicate<DexReference, Joiner<?, ?, ?>> predicate,
      BiConsumer<T, KeepInfo.Joiner<?, ?, ?>> consumer) {
    minimumKeepInfo.forEach(
        (reference, minimumKeepInfoForReference) -> {
          if (predicate.test(reference, minimumKeepInfoForReference)) {
            consumer.accept((T) reference, minimumKeepInfoForReference);
          }
        });
  }

  public KeepInfo.Joiner<?, ?, ?> getOrDefault(
      DexReference reference, KeepInfo.Joiner<?, ?, ?> defaultValue) {
    return minimumKeepInfo.getOrDefault(reference, defaultValue);
  }

  public KeepInfo.Joiner<?, ?, ?> getOrCreateMinimumKeepInfoFor(DexReference reference) {
    return minimumKeepInfo.computeIfAbsent(
        reference, ignoreKey(() -> KeepInfo.newEmptyJoinerFor(reference)));
  }

  public boolean hasMinimumKeepInfoThatMatches(
      DexReference reference, Predicate<KeepInfo.Joiner<?, ?, ?>> predicate) {
    KeepInfo.Joiner<?, ?, ?> minimumKeepInfoForReference = minimumKeepInfo.get(reference);
    return minimumKeepInfoForReference != null && predicate.test(minimumKeepInfoForReference);
  }

  public boolean isEmpty() {
    return minimumKeepInfo.isEmpty();
  }

  public void merge(MinimumKeepInfoCollection otherMinimumKeepInfo) {
    otherMinimumKeepInfo.forEach(this::mergeMinimumKeepInfoFor);
  }

  public void mergeMinimumKeepInfoFor(
      DexReference reference, KeepInfo.Joiner<?, ?, ?> minimumKeepInfoForReference) {
    getOrCreateMinimumKeepInfoFor(reference).mergeUnsafe(minimumKeepInfoForReference);
  }

  public void pruneDeadItems(DexDefinitionSupplier definitions, Enqueuer enqueuer) {
    MapUtils.removeIf(
        minimumKeepInfo,
        (reference, minimumKeepInfoForReference) -> {
          assert !minimumKeepInfoForReference.isBottom();
          ProgramDefinition definition =
              reference.apply(
                  clazz -> asProgramClassOrNull(definitions.definitionFor(clazz)),
                  field ->
                      field.lookupOnProgramClass(
                          asProgramClassOrNull(definitions.definitionFor(field.getHolderType()))),
                  method ->
                      method.lookupOnProgramClass(
                          asProgramClassOrNull(definitions.definitionFor(method.getHolderType()))));
          return definition == null || !enqueuer.isReachable(definition);
        });
  }

  public void pruneItems(PrunedItems prunedItems) {
    minimumKeepInfo.keySet().removeIf(prunedItems::isRemoved);
  }

  public KeepClassInfo.Joiner remove(DexType clazz) {
    return (KeepClassInfo.Joiner) minimumKeepInfo.remove(clazz);
  }

  public KeepFieldInfo.Joiner remove(DexField field) {
    return (KeepFieldInfo.Joiner) minimumKeepInfo.remove(field);
  }

  public KeepMethodInfo.Joiner remove(DexMethod method) {
    return (KeepMethodInfo.Joiner) minimumKeepInfo.remove(method);
  }

  public MinimumKeepInfoCollection rewrittenWithLens(GraphLens graphLens) {
    MinimumKeepInfoCollection rewrittenMinimumKeepInfo = create(size());
    forEach(
        (reference, minimumKeepInfoForReference) -> {
          DexReference rewrittenReference =
              reference.apply(
                  type -> {
                    DexType rewrittenType = graphLens.lookupType(type);
                    if (rewrittenType.isPrimitiveType()) {
                      // May happen due to enum unboxing.
                      assert type.isClassType();
                      assert rewrittenType.isIntType();
                      return null;
                    }
                    return rewrittenType;
                  },
                  graphLens::getRenamedFieldSignature,
                  graphLens::getRenamedMethodSignature);
          if (rewrittenReference != null) {
            rewrittenMinimumKeepInfo
                .getOrCreateMinimumKeepInfoFor(rewrittenReference)
                .mergeUnsafe(minimumKeepInfoForReference);
          }
        });
    return rewrittenMinimumKeepInfo;
  }

  public int size() {
    return minimumKeepInfo.size();
  }
}
