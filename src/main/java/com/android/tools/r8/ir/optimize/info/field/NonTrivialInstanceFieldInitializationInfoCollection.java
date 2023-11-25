// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.field;


import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/** See {@link InstanceFieldArgumentInitializationInfo}. */
public class NonTrivialInstanceFieldInitializationInfoCollection
    extends InstanceFieldInitializationInfoCollection {

  private final TreeMap<DexField, InstanceFieldInitializationInfo> infos;

  NonTrivialInstanceFieldInitializationInfoCollection(
      TreeMap<DexField, InstanceFieldInitializationInfo> infos) {
    assert !infos.isEmpty();
    assert infos.values().stream().noneMatch(InstanceFieldInitializationInfo::isUnknown);
    this.infos = infos;
  }

  @Override
  public void forEach(
      DexDefinitionSupplier definitions,
      BiConsumer<DexClassAndField, InstanceFieldInitializationInfo> consumer) {
    infos.forEach(
        (field, info) -> {
          DexClassAndField definition = definitions.definitionFor(field);
          if (definition != null) {
            consumer.accept(definition, info);
          } else {
            assert false;
          }
        });
  }

  @Override
  public void forEachWithDeterministicOrder(
      DexDefinitionSupplier definitions,
      BiConsumer<DexClassAndField, InstanceFieldInitializationInfo> consumer) {
    // We currently use a sorted backing and can therefore simply use forEach().
    forEach(definitions, consumer);
  }

  @Override
  public InstanceFieldInitializationInfo get(DexEncodedField field) {
    return infos.getOrDefault(
        field.getReference(), UnknownInstanceFieldInitializationInfo.getInstance());
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public InstanceFieldInitializationInfoCollection fixupAfterParametersChanged(
      ArgumentInfoCollection argumentInfoCollection) {
    Builder builder = InstanceFieldInitializationInfoCollection.builder();
    infos.forEach(
        (field, info) ->
            builder.recordInitializationInfo(
                field, info.fixupAfterParametersChanged(argumentInfoCollection)));
    return builder.build();
  }

  @Override
  public InstanceFieldInitializationInfoCollection rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens lens, GraphLens codeLens) {
    Builder builder = InstanceFieldInitializationInfoCollection.builder();
    infos.forEach(
        (field, info) ->
            builder.recordInitializationInfo(
                lens.lookupField(field, codeLens),
                info.rewrittenWithLens(appView, field.getType(), lens, codeLens)));
    return builder.build();
  }

  @Override
  public String toString() {
    List<String> strings = new ArrayList<>();
    infos.forEach((field, info) -> strings.add(field.toSourceString() + " -> " + info));
    return "NonTrivialInstanceFieldInitializationInfoCollection("
        + StringUtils.join("; ", strings)
        + ")";
  }
}
