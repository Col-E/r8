// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.FieldAccessInfoCollectionImpl;
import com.android.tools.r8.graph.FieldAccessInfoImpl;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.IdentityHashMap;
import java.util.Map;

public class FieldAccessInfoCollectionModifier {

  static class FieldReferences {
    private final ProgramMethodSet writeContexts = ProgramMethodSet.create();
    private final ProgramMethodSet readContexts = ProgramMethodSet.create();
  }

  private final Map<DexField, FieldReferences> newFieldAccesses;

  FieldAccessInfoCollectionModifier(Map<DexField, FieldReferences> newFieldAccesses) {
    this.newFieldAccesses = newFieldAccesses;
  }

  public void modify(AppView<AppInfoWithLiveness> appView) {
    FieldAccessInfoCollectionImpl impl = appView.appInfo().getMutableFieldAccessInfoCollection();
    newFieldAccesses.forEach(
        (field, info) -> {
          FieldAccessInfoImpl fieldAccessInfo = new FieldAccessInfoImpl(field);
          info.readContexts.forEach(context -> fieldAccessInfo.recordRead(field, context));
          info.writeContexts.forEach(context -> fieldAccessInfo.recordWrite(field, context));
          impl.extend(field, fieldAccessInfo);
        });
  }

  public static class Builder {

    private final Map<DexField, FieldReferences> newFieldAccesses = new IdentityHashMap<>();

    public Builder() {}

    private FieldReferences getFieldReferences(DexField field) {
      return newFieldAccesses.computeIfAbsent(field, ignore -> new FieldReferences());
    }

    public void recordFieldReadInContext(DexField field, ProgramMethod context) {
      getFieldReferences(field).readContexts.add(context);
    }

    public void recordFieldWrittenInContext(DexField field, ProgramMethod context) {
      getFieldReferences(field).writeContexts.add(context);
    }

    public FieldAccessInfoCollectionModifier build() {
      return new FieldAccessInfoCollectionModifier(newFieldAccesses);
    }
  }
}
