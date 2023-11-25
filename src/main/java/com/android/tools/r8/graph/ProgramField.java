// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.kotlin.KotlinFieldLevelInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepFieldInfo;
import com.android.tools.r8.utils.InternalOptions;

public class ProgramField extends DexClassAndField
    implements ProgramMember<DexEncodedField, DexField> {

  public ProgramField(DexProgramClass holder, DexEncodedField field) {
    super(holder, field);
  }

  public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
    getReference().collectIndexedItems(appView, indexedItems);
    DexEncodedField definition = getDefinition();
    definition.annotations().collectIndexedItems(appView, indexedItems);
    if (definition.isStatic() && definition.hasExplicitStaticValue()) {
      definition.getStaticValue().collectIndexedItems(appView, indexedItems);
    }
  }

  @Override
  public boolean isEffectivelyFinal(AppView<AppInfoWithLiveness> appView) {
    FieldAccessFlags accessFlags = getAccessFlags();
    FieldAccessInfo accessInfo =
        appView.appInfo().getFieldAccessInfoCollection().get(getReference());
    KeepFieldInfo keepInfo = appView.getKeepInfo(this);
    InternalOptions options = appView.options();
    return keepInfo.isOptimizationAllowed(options)
        && keepInfo.isShrinkingAllowed(options)
        && !accessInfo.hasReflectiveWrite()
        && !accessInfo.isWrittenFromMethodHandle()
        && accessInfo.isWrittenOnlyInMethodSatisfying(
            method ->
                method.getDefinition().isInitializer()
                    && method.getAccessFlags().isStatic() == accessFlags.isStatic()
                    && method.getHolder() == getHolder());
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean isStructurallyEqualTo(ProgramField other) {
    return getDefinition() == other.getDefinition() && getHolder() == other.getHolder();
  }

  @Override
  public ProgramField getContext() {
    return this;
  }

  @Override
  public DexProgramClass getContextClass() {
    return getHolder();
  }

  @Override
  public boolean isProgramField() {
    return true;
  }

  @Override
  public ProgramField asField() {
    return this;
  }

  @Override
  public ProgramField asProgramField() {
    return this;
  }

  @Override
  public boolean isProgramMember() {
    return true;
  }

  @Override
  public ProgramField asProgramMember() {
    return this;
  }

  @Override
  public DexProgramClass getHolder() {
    DexClass holder = super.getHolder();
    assert holder.isProgramClass();
    return holder.asProgramClass();
  }

  @Override
  public KotlinFieldLevelInfo getKotlinInfo() {
    return getDefinition().getKotlinInfo();
  }
}
