// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMergerGraphLens.Builder;
import com.android.tools.r8.utils.ListUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ClassInstanceFieldsMerger {

  // Map from target class field to all fields which should be merged into that field.
  private final Map<DexEncodedField, List<DexEncodedField>> fieldMappings = new LinkedHashMap<>();
  private final Builder lensBuilder;

  public ClassInstanceFieldsMerger(
      HorizontalClassMergerGraphLens.Builder lensBuilder, MergeGroup group) {
    this.lensBuilder = lensBuilder;
    group
        .getTarget()
        .instanceFields()
        .forEach(field -> fieldMappings.computeIfAbsent(field, ignore -> new ArrayList<>()));
  }

  public void addFields(DexProgramClass clazz) {
    Map<DexType, List<DexEncodedField>> availableFields = new IdentityHashMap<>();
    for (DexEncodedField field : fieldMappings.keySet()) {
      availableFields.computeIfAbsent(field.type(), ignore -> new LinkedList<>()).add(field);
    }

    for (DexEncodedField oldField : clazz.instanceFields()) {
      DexEncodedField newField =
          ListUtils.removeFirstMatch(
                  availableFields.get(oldField.type()),
                  field -> field.getAccessFlags().isSameVisibility(oldField.getAccessFlags()))
              .get();
      assert newField != null;
      fieldMappings.get(newField).add(oldField);
    }
  }

  private void mergeField(DexEncodedField oldField, DexEncodedField newField) {
    if (newField.isFinal() && !oldField.isFinal()) {
      newField.getAccessFlags().demoteFromFinal();
    }
    lensBuilder.moveField(oldField.field, newField.field);
  }

  private void mergeFields(DexEncodedField newField, Collection<DexEncodedField> oldFields) {
    DexField newFieldReference = newField.getReference();

    lensBuilder.moveField(newFieldReference, newFieldReference);
    lensBuilder.setRepresentativeField(newFieldReference, newFieldReference);

    oldFields.forEach(oldField -> mergeField(oldField, newField));
  }

  public void merge() {
    fieldMappings.forEach(this::mergeFields);
  }
}
