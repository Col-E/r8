// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.field;


import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Map;
import java.util.function.BiConsumer;

/** See {@link InstanceFieldArgumentInitializationInfo}. */
public class NonTrivialInstanceFieldInitializationInfoCollection
    extends InstanceFieldInitializationInfoCollection {

  private final Map<DexField, InstanceFieldInitializationInfo> infos;

  NonTrivialInstanceFieldInitializationInfoCollection(
      Map<DexField, InstanceFieldInitializationInfo> infos) {
    assert !infos.isEmpty();
    assert infos.values().stream().noneMatch(InstanceFieldInitializationInfo::isUnknown);
    this.infos = infos;
  }

  @Override
  public void forEach(
      DexDefinitionSupplier definitions,
      BiConsumer<DexEncodedField, InstanceFieldInitializationInfo> consumer) {
    infos.forEach(
        (field, info) -> {
          DexClass holder = definitions.definitionForHolder(field);
          DexEncodedField definition = field.lookupOnClass(holder);
          if (definition != null) {
            consumer.accept(definition, info);
          } else {
            assert false;
          }
        });
  }

  @Override
  public InstanceFieldInitializationInfo get(DexEncodedField field) {
    return infos.getOrDefault(field.field, UnknownInstanceFieldInitializationInfo.getInstance());
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public InstanceFieldInitializationInfoCollection rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLense lens) {
    Builder builder = InstanceFieldInitializationInfoCollection.builder();
    infos.forEach(
        (field, info) -> {
          DexField rewrittenField = lens.lookupField(field);
          InstanceFieldInitializationInfo rewrittenInfo = info.rewrittenWithLens(appView, lens);
          if (!rewrittenInfo.isUnknown()) {
            builder.recordInitializationInfo(rewrittenField, rewrittenInfo);
          }
        });
    return builder.build();
  }
}
