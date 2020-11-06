// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMergerGraphLens.Builder;
import java.util.LinkedHashMap;
import java.util.Map;

public class ClassStaticFieldsMerger {
  private final Builder lensBuilder;
  private final DexProgramClass target;
  private final Map<DexField, DexEncodedField> targetFields = new LinkedHashMap<>();
  private final DexItemFactory dexItemFactory;
  private final AppView<?> appView;

  public ClassStaticFieldsMerger(
      AppView<?> appView,
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      DexProgramClass target) {
    this.appView = appView;
    this.lensBuilder = lensBuilder;

    this.target = target;
    // Add mappings for all target fields.
    target.staticFields().forEach(field -> targetFields.put(field.getReference(), field));

    this.dexItemFactory = appView.dexItemFactory();
  }

  private final boolean isFresh(DexField fieldReference) {
    return !targetFields.containsKey(fieldReference);
  }

  private void addField(DexEncodedField field) {
    DexField oldFieldReference = field.getReference();
    DexField templateReference = field.getReference().withHolder(target.type, dexItemFactory);
    DexField newFieldReference =
        dexItemFactory.createFreshFieldNameWithHolderSuffix(
            templateReference, field.holder(), this::isFresh);

    field = field.toTypeSubstitutedField(newFieldReference);
    targetFields.put(newFieldReference, field);

    lensBuilder.mapField(oldFieldReference, newFieldReference);
  }

  public void addFields(DexProgramClass toMerge) {
    toMerge.staticFields().forEach(this::addField);
  }

  public void merge(DexProgramClass clazz) {
    clazz.setStaticFields(targetFields.values().toArray(DexEncodedField.EMPTY_ARRAY));
  }
}
