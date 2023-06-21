// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.utils.LensUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.Timing;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

public class FieldAccessInfoCollectionImpl
    implements FieldAccessInfoCollection<FieldAccessInfoImpl> {

  private final Map<DexField, FieldAccessInfoImpl> infos;

  public FieldAccessInfoCollectionImpl() {
    this(new IdentityHashMap<>());
  }

  public FieldAccessInfoCollectionImpl(Map<DexField, FieldAccessInfoImpl> infos) {
    this.infos = infos;
  }

  @Override
  public void destroyAccessContexts() {
    infos.values().forEach(FieldAccessInfoImpl::destroyAccessContexts);
  }

  @Override
  public void flattenAccessContexts() {
    infos.values().forEach(FieldAccessInfoImpl::flattenAccessContexts);
  }

  public FieldAccessInfoImpl computeIfAbsent(
      DexField field, Function<DexField, FieldAccessInfoImpl> fn) {
    return infos.computeIfAbsent(field, fn);
  }

  @Override
  public boolean contains(DexField field) {
    return infos.containsKey(field);
  }

  @Override
  public FieldAccessInfoImpl get(DexField field) {
    return infos.get(field);
  }

  public FieldAccessInfoImpl extend(DexField field, FieldAccessInfoImpl info) {
    assert !infos.containsKey(field);
    infos.put(field, info);
    return info;
  }

  @Override
  public void forEach(Consumer<FieldAccessInfoImpl> consumer) {
    // Verify that the mapping os one-to-one, otherwise the caller could receive duplicates.
    assert verifyMappingIsOneToOne();
    infos.values().forEach(consumer);
  }

  public void remove(DexField field) {
    infos.remove(field);
  }

  @Override
  public void removeIf(BiPredicate<DexField, FieldAccessInfoImpl> predicate) {
    infos.entrySet().removeIf(entry -> predicate.test(entry.getKey(), entry.getValue()));
  }

  @Override
  public void restrictToProgram(DexDefinitionSupplier definitions) {
    removeIf((field, info) -> !definitions.definitionForHolder(field).isProgramClass());
  }

  public FieldAccessInfoCollectionImpl rewrittenWithLens(
      DexDefinitionSupplier definitions, GraphLens lens, Timing timing) {
    timing.begin("Rewrite FieldAccessInfoCollectionImpl");
    Map<DexField, FieldAccessInfoImpl> newInfos =
        LensUtils.mutableRewriteMap(
            infos,
            IdentityHashMap::new,
            (field, info) -> info.rewrittenWithLens(definitions, lens, timing),
            (field, info, rewrittenInfo) -> rewrittenInfo.getField(),
            (field, info, rewrittenInfo) -> rewrittenInfo,
            (field, info, otherInfo) -> info.join(otherInfo));
    FieldAccessInfoCollectionImpl result =
        newInfos != infos ? new FieldAccessInfoCollectionImpl(newInfos) : this;
    timing.end();
    return result;
  }

  // This is used to verify that the temporary mappings inserted into `infos` by the Enqueuer are
  // removed.
  public boolean verifyMappingIsOneToOne() {
    assert infos.values().size() == SetUtils.newIdentityHashSet(infos.values()).size();
    return true;
  }
}
