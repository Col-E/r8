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

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;
  private final MergeGroup group;

  @SuppressWarnings("BadImport")
  private final Builder lensBuilder;

  private final Map<DexField, DexEncodedField> targetFields = new LinkedHashMap<>();

  public ClassStaticFieldsMerger(
      AppView<?> appView, HorizontalClassMergerGraphLens.Builder lensBuilder, MergeGroup group) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.group = group;
    this.lensBuilder = lensBuilder;
  }

  private boolean isFresh(DexField fieldReference) {
    if (group.getTarget().lookupField(fieldReference) != null) {
      // The target class has an instance or static field with the given reference.
      return false;
    }
    if (targetFields.containsKey(fieldReference)) {
      // We have already committed another static field from a source class in the merge group to
      // the given field reference (but the field is not yet added to the target class).
      return false;
    }
    return true;
  }

  private void addField(DexEncodedField field) {
    DexField oldFieldReference = field.getReference();
    DexField newFieldReference =
        dexItemFactory.createFreshFieldNameWithoutHolder(
            group.getTarget().getType(),
            field.getType(),
            field.getName().toString(),
            this::isFresh);

    field = field.toTypeSubstitutedField(appView, newFieldReference);
    targetFields.put(newFieldReference, field);

    lensBuilder.recordNewFieldSignature(oldFieldReference, newFieldReference);
  }

  public void addFields(DexProgramClass toMerge) {
    toMerge.forEachStaticField(this::addField);
  }

  public void merge() {
    group.getTarget().appendStaticFields(targetFields.values());
  }
}
