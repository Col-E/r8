// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.FieldMultiset;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SameFields extends MultiClassPolicy {

  private final AppView<AppInfoWithLiveness> appView;

  public SameFields(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public void addTo(FieldMultiset key, DexProgramClass clazz, Map<FieldMultiset, MergeGroup> map) {
    map.computeIfAbsent(key, ignore -> new MergeGroup()).add(clazz);
  }

  @Override
  public final Collection<MergeGroup> apply(MergeGroup group) {
    // First find all classes that can be merged without changing field types.
    Map<FieldMultiset, MergeGroup> groups = new LinkedHashMap<>();
    group.getClasses().forEach(clazz -> addTo(getMergeKey(clazz), clazz, groups));

    // For each trivial group, try to generalise it and then group it.
    Map<FieldMultiset, MergeGroup> generalizedGroups = new LinkedHashMap<>();
    Iterator<MergeGroup> iterator = groups.values().iterator();
    while (iterator.hasNext()) {
      MergeGroup newGroup = iterator.next();
      if (newGroup.isTrivial()) {
        newGroup
            .getClasses()
            .forEach(clazz -> addTo(getGeneralizedMergeKey(clazz), clazz, generalizedGroups));
        iterator.remove();
      }
    }

    removeTrivialGroups(generalizedGroups.values());

    List<MergeGroup> newGroups = new ArrayList<>();
    newGroups.addAll(groups.values());
    newGroups.addAll(generalizedGroups.values());
    return newGroups;
  }

  public FieldMultiset getMergeKey(DexProgramClass clazz) {
    return new FieldMultiset(clazz);
  }

  public DexEncodedField generalizeField(DexEncodedField field) {
    if (!field.type().isReferenceType()) {
      return field;
    }
    return field.toTypeSubstitutedField(
        field
            .getReference()
            .withType(appView.dexItemFactory().objectType, appView.dexItemFactory()));
  }

  public FieldMultiset getGeneralizedMergeKey(DexProgramClass clazz) {
    return new FieldMultiset(Iterables.transform(clazz.instanceFields(), this::generalizeField));
  }
}
