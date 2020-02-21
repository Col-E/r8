// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.field;

import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
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
          DexEncodedField encodedField = definitions.definitionFor(field);
          if (encodedField != null) {
            consumer.accept(encodedField, info);
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
}
