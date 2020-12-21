// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;
import com.android.tools.r8.horizontalclassmerging.policies.SameInstanceFields.InstanceFieldInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import java.util.Objects;

public class SameInstanceFields extends MultiClassSameReferencePolicy<Multiset<InstanceFieldInfo>> {

  private final DexItemFactory dexItemFactory;

  public SameInstanceFields(AppView<AppInfoWithLiveness> appView) {
    this.dexItemFactory = appView.dexItemFactory();
  }

  @Override
  public Multiset<InstanceFieldInfo> getMergeKey(DexProgramClass clazz) {
    Multiset<InstanceFieldInfo> fields = HashMultiset.create();
    for (DexEncodedField field : clazz.instanceFields()) {
      fields.add(InstanceFieldInfo.createRelaxed(field, dexItemFactory));
    }
    return fields;
  }

  public static class InstanceFieldInfo {

    private final FieldAccessFlags accessFlags;
    private final DexType type;

    private InstanceFieldInfo(FieldAccessFlags accessFlags, DexType type) {
      this.accessFlags =
          FieldAccessFlags.fromSharedAccessFlags(accessFlags.materialize())
              .unsetFinal()
              .unsetSynthetic();
      this.type = type;
    }

    public static InstanceFieldInfo createExact(DexEncodedField field) {
      return new InstanceFieldInfo(field.getAccessFlags(), field.getType());
    }

    public static InstanceFieldInfo createRelaxed(
        DexEncodedField field, DexItemFactory dexItemFactory) {
      return new InstanceFieldInfo(
          field.getAccessFlags(),
          field.getType().isReferenceType() ? dexItemFactory.objectType : field.getType());
    }

    public FieldAccessFlags getAccessFlags() {
      return accessFlags;
    }

    public InstanceFieldInfo toInfoWithRelaxedType(DexItemFactory dexItemFactory) {
      return new InstanceFieldInfo(
          accessFlags, type.isReferenceType() ? dexItemFactory.objectType : type);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      InstanceFieldInfo info = (InstanceFieldInfo) obj;
      return accessFlags.materialize() == info.accessFlags.materialize() && type == info.type;
    }

    @Override
    public int hashCode() {
      return Objects.hash(accessFlags, type);
    }
  }
}
