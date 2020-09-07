// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.dex.IndexedItemCollection;

public class ProgramField extends DexClassAndField
    implements ProgramMember<DexEncodedField, DexField> {

  public ProgramField(DexProgramClass holder, DexEncodedField field) {
    super(holder, field);
  }

  public void collectIndexedItems(IndexedItemCollection indexedItems) {
    getReference().collectIndexedItems(indexedItems);
    DexEncodedField definition = getDefinition();
    definition.annotations().collectIndexedItems(indexedItems);
    if (definition.isStatic() && definition.hasExplicitStaticValue()) {
      definition.getStaticValue().collectIndexedItems(indexedItems);
    }
  }

  public boolean isStructurallyEqualTo(ProgramField other) {
    return getDefinition() == other.getDefinition() && getHolder() == other.getHolder();
  }

  @Override
  public boolean isProgramField() {
    return true;
  }

  @Override
  public ProgramField asProgramField() {
    return this;
  }

  @Override
  public DexProgramClass getHolder() {
    DexClass holder = super.getHolder();
    assert holder.isProgramClass();
    return holder.asProgramClass();
  }
}
